# Common functions used by build-docker-image.sh and publish-neo4j-admin-image.sh

function contains_element
{
  local e match="$1"
  shift
  for e; do [[ "$e" == "$match" ]] && return 0; done
  return 1
}

function get_series_from_version
{
    local version=$1
    local major=$(echo "${version}" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\1/')
    local minor=$(echo "${version}" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\2/')
    if [[ "${major}" -ge "5" ]]; then
        echo "${major}"
    else
        echo "${major}.${minor}"
    fi
}

function get_compatible_dockerfile_for_os_or_error
{
    local version=${1}
    local requested_os=${2}

    local major=$(echo "${version}" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\1/')
    local minor=$(echo "${version}" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\2/')

    case ${major} in
        # Version is calver
        2024)
                local SUPPORTED_IMAGE_OS=("debian" "ubi9")
                if contains_element ${requested_os} "${SUPPORTED_IMAGE_OS[@]}"; then
                    echo  "Dockerfile-${requested_os}"
                    return 0
                fi
                ;;
        5)
            local SUPPORTED_IMAGE_OS=("debian" "ubi9")
            if contains_element ${requested_os} "${SUPPORTED_IMAGE_OS[@]}"; then
                echo  "Dockerfile-${requested_os}"
                return 0
            fi
            ;;
        4)
            case ${minor} in
            4)
                local SUPPORTED_IMAGE_OS=("debian" "ubi9")
                if contains_element ${requested_os} "${SUPPORTED_IMAGE_OS[@]}"; then
                    echo  "Dockerfile-${requested_os}"
                    return 0
                fi
                ;;
            esac
    esac
    if [[ ${requested_os} = "debian" ]]; then
        echo "Dockerfile"
        return 0
    fi
    echo >&2 "${IMAGE_OS} is not a supported operating system for ${version}."
    usage
    DOCKERFILE_NAME

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