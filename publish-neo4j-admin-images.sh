#!/bin/bash
set -eu -o pipefail

ROOT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

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
"${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "enterprise" "debian" "${REPOSITORY}"
"${ROOT_DIR}/publish-neo4j-admin-image.sh" "${NEO4JVERSION}" "community" "debian" "${REPOSITORY}"

echo "Adding extra tags..."

docker buildx imagetools create "${REPOSITORY}":5-community-debian \
--tag "${REPOSITORY}:5-community" \
--tag "${REPOSITORY}:5" \
--tag "${REPOSITORY}:community-debian" \
--tag "${REPOSITORY}:community" \
--tag "${REPOSITORY}:debian" \
--tag "${REPOSITORY}:latest"

docker buildx imagetools create "${REPOSITORY}":5-community-ubi9 \
--tag "${REPOSITORY}:community-ubi9" \
--tag "${REPOSITORY}:ubi9" \

docker buildx imagetools create "${REPOSITORY}":5-enterprise-debian \
--tag "${REPOSITORY}:5-enterprise" \
--tag "${REPOSITORY}:enterprise-debian" \
--tag "${REPOSITORY}:enterprise"

docker buildx imagetools create "${REPOSITORY}":5-enterprise-ubi9 \
--tag "${REPOSITORY}:enterprise-ubi9"
