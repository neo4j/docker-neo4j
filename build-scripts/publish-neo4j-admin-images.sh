#!/bin/bash
set -eu -o pipefail

function usage
{
    echo >&2 "USAGE: $0 <version> <repository>
    For example:
        $0 4.4.10 neo4j/neo4j-admin
        $0 5.10.0 neo4j/neo4j-admin
    Version and repository can also be set in the environment.
    For example:
        NEO4JVERSION=4.4.10 REPOSITORY=neo4j/neo4j-admin $0
        NEO4JVERSION=5.10.0 REPOSITORY=neo4j/neo4j-admin $0
    "
    exit 1
}

if [[ $# -eq 2 ]]; then
    NEO4JVERSION=${1}
    REPOSITORY=${2}
else
    if [[ -z ${NEO4JVERSION:-""} ]]; then
        echo >&2 "NEO4JVERSION is unset. Either set it in the environment or pass as argument to this script."
        usage
    fi
    if [[ -z ${REPOSITORY:-""} ]]; then
        echo >&2 "REPOSITORY is unset. Either set it in the environment or pass as argument to this script."
        usage
    fi
fi

ROOT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
echo "root dir=$ROOT_DIR"
source "$ROOT_DIR/build-utils-common-functions.sh"

echo "Verifying access or failing fast, by re-tagging neo4j-admin:5, when successful it's a noop."
docker buildx imagetools create "${REPOSITORY}":5-community \
--tag "${REPOSITORY}:5"

echo "Publishing ${REPOSITORY}:${NEO4JVERSION}"

for os in "${SUPPORTED_IMAGE_OS[@]}"; do
  echo "Publishing ${REPOSITORY}:${NEO4JVERSION}-community-${os}"
  "${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "community" "${os}" "${REPOSITORY}"
  echo "Publishing ${REPOSITORY}:${NEO4JVERSION}-enterprise-${os}"
  "${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "enterprise" "${os}" "${REPOSITORY}"
done

echo "Adding extra tags..."

MAJOR=$(get_major_from_version "${NEO4JVERSION}")

if [[ "$MAJOR" == "5" ]]; then
    echo "Tagging ${MAJOR}..."
    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-community-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-community-debian" \
    --tag "${REPOSITORY}:${MAJOR}-community" \
    --tag "${REPOSITORY}:${MAJOR}"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-debian" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-community-bullseye"
    --tag "${REPOSITORY}:${MAJOR}-bullseye"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-bullseye"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-ubi10" \
    --tag "${REPOSITORY}:${MAJOR}-community-ubi10" \
    --tag "${REPOSITORY}:${MAJOR}-ubi10"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-ubi10" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-ubi10"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-community-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-ubi9"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-ubi9"


elif [[ "$MAJOR" -gt 2024 ]]; then
    echo "Tagging calver ${MAJOR}..."
    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-community-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-community-debian" \
    --tag "${REPOSITORY}:${MAJOR}-community" \
    --tag "${REPOSITORY}:${MAJOR}" \
    --tag "${REPOSITORY}:community-trixie" \
    --tag "${REPOSITORY}:community-debian" \
    --tag "${REPOSITORY}:community" \
    --tag "${REPOSITORY}:trixie" \
    --tag "${REPOSITORY}:debian" \
    --tag "${REPOSITORY}:latest"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-trixie" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-debian" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise" \
    --tag "${REPOSITORY}:enterprise-trixie" \
    --tag "${REPOSITORY}:enterprise-debian" \
    --tag "${REPOSITORY}:enterprise"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-community-bullseye" \
    --tag "${REPOSITORY}:community-bullseye" \
    --tag "${REPOSITORY}:bullseye"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-bullseye" \
    --tag "${REPOSITORY}:enterprise-bullseye"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-ubi10" \
    --tag "${REPOSITORY}:${MAJOR}-community-ubi10" \
    --tag "${REPOSITORY}:${MAJOR}-community-redhat" \
    --tag "${REPOSITORY}:community-ubi10" \
    --tag "${REPOSITORY}:community-redhat" \
    --tag "${REPOSITORY}:ubi10" \
    --tag "${REPOSITORY}:redhat"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-ubi10" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-ubi10" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-redhat" \
    --tag "${REPOSITORY}:enterprise-ubi10" \
    --tag "${REPOSITORY}:enterprise-redhat"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-community-ubi9" \
    --tag "${REPOSITORY}:community-ubi9" \
    --tag "${REPOSITORY}:ubi9"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-ubi9" \
    --tag "${REPOSITORY}:enterprise-ubi9"
fi
