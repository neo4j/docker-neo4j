#!/bin/bash
set -eu -o pipefail

EDITIONS=("community" "enterprise")

ROOT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
source "$ROOT_DIR/build-utils-common-functions.sh"
BUILD_DIR=${ROOT_DIR}/build
SRC_DIR=${ROOT_DIR}/docker-image-src
# shellcheck disable=SC2034  # Used in docker-common-functions.sh
TAR_CACHE=${ROOT_DIR}/in

function usage
{
    echo >&2 "USAGE: $0 <version> <edition> <operating system> <repository>
    For example:
        $0 4.4.10 community debian neo/neo4j-admin
        $0 5.10.0 enterprise ubi9 neo/neo4j-admin
    Version and operating system can also be set in the environment.
    For example:
        NEO4JVERSION=4.4.10 NEO4JEDITION=community IMAGE_OS=debian REPOSITORY=neo/neo4j-admin $0
        NEO4JVERSION=5.10.0 NEO4JEDITION=enterprise IMAGE_OS=ubi9 REPOSITORY=neo/neo4j-admin $0
    "
    exit 1
}

## ==========================================
## get and sanitise script inputs

if [[ $# -eq 4 ]]; then
    NEO4JVERSION=${1}
    NEO4JEDITION=${2}
    IMAGE_OS=${3}
    REPOSITORY=${4}
elif [[ -z ${NEO4JVERSION:-""} ]]; then
    echo >&2 "NEO4JVERSION is unset. Either set it in the environment or pass as argument to this script."
    usage
elif [[ -z ${NEO4JEDITION:-""} ]]; then
    echo >&2 "NEO4JEDITION is unset. Either set it in the environment or pass as argument to this script."
    usage
elif [[ -z ${IMAGE_OS:-""} ]]; then
    echo >&2 "IMAGE_OS is unset. Either set it in the environment or pass as argument to this script."
    usage
elif [[ -z ${REPOSITORY:-""} ]]; then
    echo >&2 "REPOSITORY is unset. Either set it in the environment or pass as argument to this script."
    usage
fi
# verify edition
if ! contains_element "${NEO4JEDITION}" "${EDITIONS[@]}"; then
    echo >&2 "${NEO4JEDITION} is not a supported edition."
    usage
fi
# verify compatible neo4j version
if [[ ! "${NEO4JVERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+.*$  ]]; then
    echo "\"${NEO4JVERSION}\" is not a valid version number."
    usage
fi
# get source files
BRANCH=$(get_branch_from_version ${NEO4JVERSION})
DOCKERFILE_NAME=$(get_compatible_dockerfile_for_os_or_error "${BRANCH}" "${IMAGE_OS}")

echo "Building local context for docker build"
ADMIN_LOCALCXT_DIR=${BUILD_DIR}/${IMAGE_OS}/neo4j-admin/${NEO4JEDITION}
mkdir -p ${ADMIN_LOCALCXT_DIR}

# copy neo4j-admin sources
mkdir -p ${ADMIN_LOCALCXT_DIR}/local-package
cp ${SRC_DIR}/common/* ${ADMIN_LOCALCXT_DIR}/local-package
cp "$(cached_tarball "${NEO4JVERSION}" "${NEO4JEDITION}")" ${ADMIN_LOCALCXT_DIR}/local-package/
cp ${SRC_DIR}/${BRANCH}/neo4j-admin/*.sh ${ADMIN_LOCALCXT_DIR}/local-package

# create neo4j-admin Dockerfile
cp "${SRC_DIR}/${BRANCH}/neo4j-admin/${DOCKERFILE_NAME}" "${ADMIN_LOCALCXT_DIR}/Dockerfile"
coredb_sha=$(shasum --algorithm=256 "$(cached_tarball "${NEO4JVERSION}" "${NEO4JEDITION}")" | cut -d' ' -f1)
sed -i -e "s|%%NEO4J_SHA%%|${coredb_sha}|" "${ADMIN_LOCALCXT_DIR}/Dockerfile"
sed -i -e "s|%%NEO4J_TARBALL%%|$(tarball_name ${NEO4JVERSION} ${NEO4JEDITION})|" "${ADMIN_LOCALCXT_DIR}/Dockerfile"
sed -i -e "s|%%NEO4J_EDITION%%|${NEO4JEDITION}|" "${ADMIN_LOCALCXT_DIR}/Dockerfile"

# build and push neo4j-admin
MAJOR=$(get_major_from_version "${NEO4JVERSION}")
full_version_admin_image_tag="${REPOSITORY}:${NEO4JVERSION}-${NEO4JEDITION}-${IMAGE_OS}"
major_minor_admin_image_tag="${REPOSITORY}:${NEO4JVERSION%.*}-${NEO4JEDITION}-${IMAGE_OS}"
major_admin_image_tag="${REPOSITORY}:${MAJOR}-${NEO4JEDITION}-${IMAGE_OS}"
echo "Building neo4j-admin docker image for neo4j-admin-${NEO4JVERSION} ${NEO4JEDITION} on ${IMAGE_OS}."
echo "With tags: ${full_version_admin_image_tag},${major_minor_admin_image_tag},${major_admin_image_tag}"

docker buildx build --tag="${full_version_admin_image_tag}" --tag="${major_minor_admin_image_tag}" --tag="${major_admin_image_tag}" \
    --build-arg="NEO4J_URI=file:///startup/$(tarball_name "${NEO4JVERSION}" "${NEO4JEDITION}")" \
    "${ADMIN_LOCALCXT_DIR}" --platform linux/amd64,linux/arm64 --push


