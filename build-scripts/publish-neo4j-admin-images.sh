#!/bin/bash
set -eu -o pipefail

ROOT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
source "$ROOT_DIR/build-utils-common-functions.sh"

if [[ $# -eq 2 ]]; then
    NEO4JVERSION=${1}
    REPOSITORY=${2}
elif [[ -z ${NEO4JVERSION:-""} ]]; then
    echo >&2 "NEO4JVERSION is unset. Either set it in the environment or pass as argument to this script."
    exit 1
elif [[ -z ${REPOSITORY:-""} ]]; then
    echo >&2 "REPOSITORY is unset. Either set it in the environment or pass as argument to this script."
    exit 1
fi

echo "Verifying access or failing fast, by re-tagging neo4j-admin:5, when successful it's a noop."
docker buildx imagetools create "${REPOSITORY}":5-community \
--tag "${REPOSITORY}:5"

echo "Publishing ${REPOSITORY}:${NEO4JVERSION}"
"${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "enterprise" "ubi9" "${REPOSITORY}"
"${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "community" "ubi9" "${REPOSITORY}"
"${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "enterprise" "bullseye" "${REPOSITORY}"
"${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "community" "bullseye" "${REPOSITORY}"

echo "Adding extra tags..."

MAJOR=$(get_major_from_version "${NEO4JVERSION}")

if [[ "$MAJOR" == "5" ]]; then
    echo "Tagging ${MAJOR}..."
    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-community-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-community-debian" \
    --tag "${REPOSITORY}:${MAJOR}-community" \
    --tag "${REPOSITORY}:${MAJOR}"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-debian" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise"

elif [[ "$MAJOR" -gt 2024 ]]; then
    echo "Tagging calver ${MAJOR}..."
    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-community-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-community-debian" \
    --tag "${REPOSITORY}:${MAJOR}-community" \
    --tag "${REPOSITORY}:${MAJOR}" \
    --tag "${REPOSITORY}:community-bullseye" \
    --tag "${REPOSITORY}:community-debian" \
    --tag "${REPOSITORY}:community" \
    --tag "${REPOSITORY}:bullseye" \
    --tag "${REPOSITORY}:debian" \
    --tag "${REPOSITORY}:latest"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-community-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-community-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-community-redhat" \
    --tag "${REPOSITORY}:community-ubi9" \
    --tag "${REPOSITORY}:community-redhat" \
    --tag "${REPOSITORY}:ubi9" \
    --tag "${REPOSITORY}:redhat"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-bullseye" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-debian" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise" \
    --tag "${REPOSITORY}:enterprise-bullseye" \
    --tag "${REPOSITORY}:enterprise-debian" \
    --tag "${REPOSITORY}:enterprise"

    docker buildx imagetools create "${REPOSITORY}:${NEO4JVERSION}-enterprise-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-ubi9" \
    --tag "${REPOSITORY}:${MAJOR}-enterprise-redhat" \
    --tag "${REPOSITORY}:enterprise-ubi9" \
    --tag "${REPOSITORY}:enterprise-redhat"
fi
