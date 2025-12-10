#!/bin/bash
set -eu -o pipefail

function get_removed_in_version_5
{
    local image_os=${1}

    case ${image_os} in
        "ubi8" )
            echo "5.20.0"
            ;;
        "ubi9" )
            echo "5.26.21"
            ;;
        "bullseye" )
            echo "5.26.21"
            ;;
    esac
}

function get_removed_in_version_calver
{
    local image_os=${1}

    case ${image_os} in
        ubi9 )
            echo "2026.03.0"
            ;;
        bullseye )
            echo "2026.03.0"
            ;;
    esac
}

function set_deprecation_early_warning
{
    local image_os="${1}"
    local update_to="${2}"
    local coredb_entrypoint="${3}"
    local admin_entrypoint="${4}"

    case ${image_os} in
      ubi8 | ubi9 )
        echo "if [ \"\${NEO4J_DEPRECATION_WARNING:-yes}\" != \"suppress\" ]; then\n
\techo \>\&2 \"\n=======================================================\n
Neo4j Red Hat ${image_os^^} images are deprecated in favour of Red Hat ${update_to^^}.\n
Update your codebase to use Neo4j Docker image tags ending with -${update_to} instead of -${image_os}.\n\n
Neo4j $(get_removed_in_version_calver $image_os) will be the last version to get a Red Hat ${image_os^^} docker image release.\n\n
To suppress this warning set environment variable NEO4J_DEPRECATION_WARNING=suppress.\n
=======================================================\n\"\n
fi"
        ;;
    esac
}

ROOT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
source "$ROOT_DIR/build-utils-common-functions.sh"
get_removed_in_version_calver ubi9
set_deprecation_early_warning ubi9 ubi10 a b

#if [ "${IMAGE_OS}" = "ubi8" ]; then
#    dep_msg="if [ \"\${NEO4J_DEPRECATION_WARNING:-yes}\" != \"suppress\" ]; then\n
#\techo \>\&2 \"\n=======================================================\n
#Neo4j Red Hat UBI8 images are deprecated in favour of Red Hat UBI9.\n
#Update your codebase to use Neo4j Docker image tags ending with -ubi9 instead of -ubi8.\n\n
#Neo4j 5.20.0 will be the last version to get a Red Hat UBI8 docker image release.\n\n
#To suppress this warning set environment variable NEO4J_DEPRECATION_WARNING=suppress.\n
#=======================================================\n\"\n
#fi"
#    sed -i -e "s/#%%DEPRECATION_WARNING_PLACEHOLDER%%/$(echo ${dep_msg} | sed -z 's/\n/\\n/g')/" "${COREDB_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#    sed -i -e "s/#%%DEPRECATION_WARNING_PLACEHOLDER%%/$(echo ${dep_msg} | sed -z 's/\n/\\n/g')/" "${ADMIN_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#else
#    sed -i -e '/#%%DEPRECATION_WARNING_PLACEHOLDER%%/d' "${COREDB_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#    sed -i -e '/#%%DEPRECATION_WARNING_PLACEHOLDER%%/d' "${ADMIN_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#fi





#if [ "${IMAGE_OS}" = "ubi8" ]; then
#    dep_msg="echo \>\&2 \"\n=======================================================\n
#Neo4j Red Hat UBI8 images are deprecated in favour of Red Hat UBI9.\n
#Update your codebase to use Neo4j Docker image tags ending with -ubi9 instead of -ubi8.\n\n
#This is the last Neo4j image available on Red Hat UBI8.\n
#By continuing to use UBI8 tagged Neo4j images you will not get further updates, \n
#including new features and security fixes.\n\n
#This message can not be suppressed.\n
#=======================================================\n\"\n"
#    sed -i -e "s/#%%DEPRECATION_WARNING_PLACEHOLDER%%/$(echo ${dep_msg} | sed -z 's/\n/\\n/g')/" "${COREDB_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#    sed -i -e "s/#%%DEPRECATION_WARNING_PLACEHOLDER%%/$(echo ${dep_msg} | sed -z 's/\n/\\n/g')/" "${ADMIN_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#else
#    sed -i -e '/#%%DEPRECATION_WARNING_PLACEHOLDER%%/d' "${COREDB_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#    sed -i -e '/#%%DEPRECATION_WARNING_PLACEHOLDER%%/d' "${ADMIN_LOCALCXT_DIR}/local-package/docker-entrypoint.sh"
#fi
