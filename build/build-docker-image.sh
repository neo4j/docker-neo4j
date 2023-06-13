#!/bin/bash
set -eu -o pipefail

SUPPORTED_IMAGE_OS=("debian" "rhel8")
EDITIONS=("community" "enterprise")

DISTRIBUTION_SITE="https://dist.neo4j.org"
BUILD_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SRC_DIR=${BUILD_DIR}/../docker-image-src
TAR_CACHE=${BUILD_DIR}/tars

function usage
{
    echo >&2 "USAGE: $0 <version> <edition> <operating system>
    For example:
        $0 4.4.10 community debian
        $0 5.10.0 enterprise rhel8
    Version and operating system can also be set in the environment.
    For example:
        NEO4JVERSION=4.4.10 NEO4JEDITION=community IMAGE_OS=debian $0
        NEO4JVERSION=5.10.0 NEO4JEDITION=enterprise IMAGE_OS=rhel8 $0
    "
    exit 1
}

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
        echo "Downloading ${tar_name} from ${DISTRIBUTION_SITE}"
        wget ${DISTRIBUTION_SITE}/${tar_name} -O "$(cached_tarball ${version} ${edition})"
    fi
}

## ==========================================
## get and sanitise script inputs and paths

BUILD_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SRC_DIR=${BUILD_DIR}/../docker-image-src
TAR_CACHE=${BUILD_DIR}/tars

if [[ $# -eq 3 ]]; then
    NEO4JVERSION=${1}
    NEO4JEDITION=${2}
    IMAGE_OS=${3}
elif [[ -z ${NEO4JVERSION:-""} ]]; then
    echo >&2 "NEO4JVERSION is unset. Either set it in the environment or pass as argument to this script."
    usage
elif [[ -z ${NEO4JEDITION:-""} ]]; then
    echo >&2 "NEO4JEDITION is unset. Either set it in the environment or pass as argument to this script."
    usage
elif [[ -z ${IMAGE_OS:-""} ]]; then
    echo >&2 "IMAGE_OS is unset. Either set it in the environment or pass as argument to this script."
    usage
fi
# verify edition
if ! contains_element "${NEO4JEDITION}" "${EDITIONS[@]}"; then
    echo >&2 "${NEO4JEDITION} is not a supported edition."
    usage
fi
# verify compatible OS
if ! contains_element "${IMAGE_OS}" "${SUPPORTED_IMAGE_OS[@]}"; then
    echo >&2 "${IMAGE_OS} is not a supported operating system at this time."
    usage
fi
# verify compatible neo4j version
if [[ ! "${NEO4JVERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+.*$  ]]; then
    echo "\"${NEO4JVERSION}\" is not a valid version number."
    usage
fi

echo "Building docker neo4j-${NEO4JEDITION}-${NEO4JVERSION} image based on ${IMAGE_OS}."

## ==================================================================================
## get neo4j source tar from distribution site. This is required for release artifacts
## so that we can calculate the sha256, and for the local image build.

echo "Caching neo4j source tarball"
fetch_tarball "${NEO4JVERSION}" "${NEO4JEDITION}"

## ==================================================================================
## construct local build context. These are all the files required to build the
## neo4j image locally.

echo "Building local context for docker build"
COREDB_LOCALCXT_DIR=${BUILD_DIR}/${IMAGE_OS}/coredb/${NEO4JEDITION}
ADMIN_LOCALCXT_DIR=${BUILD_DIR}/${IMAGE_OS}/neo4j-admin/${NEO4JEDITION}
mkdir -p ${COREDB_LOCALCXT_DIR}
mkdir -p ${ADMIN_LOCALCXT_DIR}

SERIES=$(get_series_from_version ${NEO4JVERSION})

# copy coredb sources
mkdir -p ${COREDB_LOCALCXT_DIR}/local-package
cp ${SRC_DIR}/common/* ${COREDB_LOCALCXT_DIR}/local-package
cp ${SRC_DIR}/${SERIES}/coredb/*.sh ${COREDB_LOCALCXT_DIR}/local-package
cp ${SRC_DIR}/${SERIES}/coredb/*.json ${COREDB_LOCALCXT_DIR}/local-package
coredb_sha=$(shasum --algorithm=256 "$(cached_tarball "${NEO4JVERSION}" "${NEO4JEDITION}")" | cut -d' ' -f1)
cp "$(cached_tarball "${NEO4JVERSION}" "${NEO4JEDITION}")" ${COREDB_LOCALCXT_DIR}/local-package/

# create coredb Dockerfile
cp "${SRC_DIR}/${SERIES}/coredb/Dockerfile-${IMAGE_OS}" "${COREDB_LOCALCXT_DIR}/Dockerfile"
sed -i \
    -e "s|%%NEO4J_SHA%%|${coredb_sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(tarball_name "${NEO4JVERSION}" "${NEO4JEDITION}")|" \
    -e "s|%%NEO4J_EDITION%%|${NEO4JEDITION}|" \
    -e "s|%%NEO4J_DIST_SITE%%|${DISTRIBUTION_SITE}|" \
    "${COREDB_LOCALCXT_DIR}/Dockerfile"

# copy neo4j-admin sources
mkdir -p ${ADMIN_LOCALCXT_DIR}/local-package
cp ${SRC_DIR}/common/* ${ADMIN_LOCALCXT_DIR}/local-package
cp "$(cached_tarball "${NEO4JVERSION}" "${NEO4JEDITION}")" ${ADMIN_LOCALCXT_DIR}/local-package/
cp ${SRC_DIR}/${SERIES}/neo4j-admin/*.sh ${ADMIN_LOCALCXT_DIR}/local-package

# create neo4j-admin Dockerfile
cp "${SRC_DIR}/${SERIES}/neo4j-admin/Dockerfile-${IMAGE_OS}" "${ADMIN_LOCALCXT_DIR}/Dockerfile"
sed -i \
    -e "s|%%NEO4J_SHA%%|${coredb_sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(tarball_name ${NEO4JVERSION} ${NEO4JEDITION})|" \
    -e "s|%%NEO4J_EDITION%%|${NEO4JEDITION}|" \
    -e "s|%%NEO4J_DIST_SITE%%|${DISTRIBUTION_SITE}|" \
    "${ADMIN_LOCALCXT_DIR}/Dockerfile"





## ==================================================================================
## Finally we are ready to do a docker build...

# build coredb
coredb_image_tag=test/${RANDOM}
echo "Building CoreDB docker image for neo4j-${NEO4JVERSION} ${NEO4JEDITION} on ${IMAGE_OS}."
docker build --tag=${coredb_image_tag} \
    --build-arg="NEO4J_URI=file:///startup/$(tarball_name "${NEO4JVERSION}" "${NEO4JEDITION}")" \
    "${COREDB_LOCALCXT_DIR}"
echo "Tagged CoreDB image ${coredb_image_tag}"
echo -n "${coredb_image_tag}" > ${COREDB_LOCALCXT_DIR}/../.image-id-"${NEO4JEDITION}"

# build neo4j-admin
admin_image_tag=test/${RANDOM}
echo "Building neo4j-admin docker image for neo4j-admin-${NEO4JVERSION} ${NEO4JEDITION} on ${IMAGE_OS}."
docker build --tag=${admin_image_tag} \
    --build-arg="NEO4J_URI=file:///startup/$(tarball_name "${NEO4JVERSION}" "${NEO4JEDITION}")" \
    "${ADMIN_LOCALCXT_DIR}"
echo "Tagged neo4j-admin image ${admin_image_tag}"
echo -n "${admin_image_tag}" > ${ADMIN_LOCALCXT_DIR}/../.image-id-"${NEO4JEDITION}"

## ==================================================================================
# generate env files for local development
{
    echo "NEO4JVERSION=${NEO4JVERSION}"
    echo "NEO4J_IMAGE=$(cat "${COREDB_LOCALCXT_DIR}"/../.image-id-"${NEO4JEDITION}")"
    echo "NEO4JADMIN_IMAGE=$(cat "${ADMIN_LOCALCXT_DIR}"/../.image-id-"${NEO4JEDITION}")"
    echo "NEO4J_EDITION=${NEO4JEDITION}"
    echo "NEO4J_SKIP_MOUNTED_FOLDER_TARBALLING=true"
} > ${BUILD_DIR}/devenv-"${NEO4JEDITION}".env

