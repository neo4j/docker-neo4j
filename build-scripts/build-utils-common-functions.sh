# Common functions used by build-docker-image.sh and publish-neo4j-admin-image.sh

EDITIONS=("community" "enterprise")
DISTRIBUTION_SITE="https://dist.neo4j.org"

function contains_element
{
  local e match="$1"
  shift
  for e; do [[ "$e" == "$match" ]] && return 0; done
  return 1
}

function get_branch_from_version
{
    local version=$1
    local major=$(echo "${version}" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\1/')
    local minor=$(echo "${version}" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\2/')
    case ${major} in
      1|2|3|4)
        echo "${major}.${minor}"
        return
        ;;
      5)
        echo "${major}"
        return
        ;;
      *)
        echo "calver"
        return
    esac
}

function get_major_from_version
{
    echo "$1" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\1/'
}

function get_compatible_dockerfile_for_os_or_error
{
    local branch=${1}
    local requested_os=${2}

    case ${branch} in
        calver | 5 | 4.4 )
            if contains_element "${requested_os}" "${SUPPORTED_IMAGE_OS[@]}"; then
                echo  "Dockerfile-${requested_os}"
                return 0
            fi
            ;;
        *)
            local SUPPORTED_IMAGE_OS=("bullseye")
            if contains_element "${requested_os}" "${SUPPORTED_IMAGE_OS[@]}"; then
                echo  "Dockerfile"
                return 0
            fi
            ;;
    esac
    echo >&2 "${requested_os} is not a supported operating system for ${branch}."
    return 1
}

function tarball_name
{
    local version=${1}
    local edition=${2}
    echo "neo4j-${2}-${1}-unix.tar.gz"
}

function cached_tarball
{
    local version=${1}
    local edition=${2}
    echo "${TAR_CACHE}/$(tarball_name ${version} ${edition})"
}

function fetch_tarball
{
    local version=${1}
    local edition=${2}
    local tar_name=$(tarball_name "${version}" "${edition}")
    mkdir -p ${TAR_CACHE}
    if [[ ! -f $(cached_tarball "${version}" "${edition}") ]]; then
        echo "Downloading ${tar_name} from ${DISTRIBUTION_SITE} to $(cached_tarball ${version} ${edition})"
        wget ${DISTRIBUTION_SITE}/${tar_name} -O "$(cached_tarball ${version} ${edition})"
    fi
}

echo "Calculating supported base images"
if [[ -z ${NEO4JVERSION:-""} ]]; then
    echo >&2 "NEO4JVERSION is unset. Either set it in the environment or pass as argument to this script."
    exit 0
fi

case "$(get_branch_from_version ${NEO4JVERSION})" in
  calver )
    SUPPORTED_IMAGE_OS=("bullseye" "trixie" "ubi9" "ubi10")
    DEPRECATED_IMAGE_OS=("bullseye" "ubi8" "ubi9")
    ;;
  5 )
    SUPPORTED_IMAGE_OS=("bullseye" "trixie" "ubi9" "ubi10")
    DEPRECATED_IMAGE_OS=("bullseye" "ubi8" "ubi9")
    ;;
  4.4 )
    SUPPORTED_IMAGE_OS=("bullseye" "ubi9")
    DEPRECATED_IMAGE_OS=("ubi8")
    ;;
  *)
    SUPPORTED_IMAGE_OS=("bullseye")
    DEPRECATED_IMAGE_OS=()
    ;;
esac
