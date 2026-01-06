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
            echo "5.26.23"
            ;;
        "bullseye" )
            echo "5.26.23"
            ;;
    esac
}

function get_removed_in_version_calver
{
    local image_os=${1}

    case ${image_os} in
        ubi9 )
            echo "2026.04.0"
            ;;
        bullseye )
            echo "2026.04.0"
            ;;
    esac
}

function get_update_to_image
{
    local image_os=${1}
    case ${image_os} in
        ubi9 )
            echo "ubi10"
            ;;
        bullseye )
            echo "trixie"
            ;;
    esac
}

function deprecation_early_warning_message
{
    local image_os="${1}"
    local update_to="$(get_update_to_image $image_os)"

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
      bullseye )
        echo "if [ \"\${NEO4J_DEPRECATION_WARNING:-yes}\" != \"suppress\" ]; then\n
\techo \>\&2 \"\n=======================================================\n
Neo4j Debian ${image_os^^} images are deprecated in favour of Debian ${update_to^^}.\n
Update your codebase to use Neo4j Docker image tags ending with -${update_to} instead of -${image_os}.\n\n
Neo4j $(get_removed_in_version_calver $image_os) will be the last version to get a Debian ${image_os^^} docker image release.\n\n
To suppress this warning set environment variable NEO4J_DEPRECATION_WARNING=suppress.\n
=======================================================\n\"\n
fi"
        ;;
    esac
}

function deprecation_final_warning_message
{
    local image_os="${1}"
    local update_to="$(get_update_to_image $image_os)"

    case ${image_os} in
      ubi8 | ubi9 )
        echo "echo \>\&2 \"\n=======================================================\n
Neo4j Red Hat ${image_os^^} images are deprecated in favour of Red Hat ${update_to^^}.\n
Update your codebase to use Neo4j Docker image tags ending with -${update_to} instead of -${image_os}.\n\n
This is the last Neo4j image available on Red Hat ${image_os^^}.\n
By continuing to use ${image_os^^} tagged Neo4j images you will not get further updates, \n
including new features and security fixes.\n\n
This message can not be suppressed.\n
=======================================================\n\"\n"
      ;;
      bullseye )
        echo "echo \>\&2 \"\n=======================================================\n
Neo4j Debian ${image_os^^} images are deprecated in favour of Debian ${update_to^^}.\n
Update your codebase to use Neo4j Docker image tags ending with -${update_to} instead of -${image_os}.\n\n
This is the last Neo4j image available on Debian ${image_os^^}.\n
By continuing to use ${image_os^^} tagged Neo4j images you will not get further updates, \n
including new features and security fixes.\n\n
This message can not be suppressed.\n
=======================================================\n\"\n"
      ;;
    esac
}

function deprecation_message
{
    local image_os="${1}"
    local neo4j_version="${2}"
    local branch=$(get_branch_from_version ${neo4j_version})
    local deprecated_in_version

    # Find which neo4j version the image OS will last appear in
    if [ "${branch}" == "calver" ]; then
        deprecated_in_version="$(get_removed_in_version_calver ${image_os})"
    elif [ "${branch}" == "5" ]; then
        deprecated_in_version="$(get_removed_in_version_5 ${image_os})"
    else
        echo >&2 "Cannot generate deprecation message for ${image_os} and neo4j ${branch}."
        return 1
    fi
    # if the deprecated_in_version is the one currently being built,
    # then give final warning instead of early warning.
    if [ "${neo4j_version}" == "${deprecated_in_version}" ]; then
        deprecation_final_warning_message "${image_os}"
    else
        deprecation_early_warning_message "${image_os}"
    fi
}