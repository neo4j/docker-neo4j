#!/bin/bash -eu

# load useful utility functions
. /startup/utilities.sh

function check_mounted_folder_writable_with_chown
{
    local mountFolder=${1}
    debug_msg "checking ${mountFolder} is writable"
    if running_as_root; then
        # check folder permissions
        if ! is_writable "${mountFolder}" ;  then
            # warn that we're about to chown the folder and then chown it
            echo "Warning: Folder mounted to \"${mountFolder}\" is not writable from inside container. Changing folder owner to ${userid}."
            chown -R "${userid}":"${groupid}" "${mountFolder}"
        # check permissions on files in the folder
        elif [ $(${exec_cmd} find "${mountFolder}" -not -writable | wc -l) -gt 0 ]; then
            echo "Warning: Some files inside \"${mountFolder}\" are not writable from inside container. Changing folder owner to ${userid}."
            chown -R "${userid}":"${groupid}" "${mountFolder}"
        fi
    else
        if [[ ! -w "${mountFolder}" ]]  && [[ "$(stat -c %U ${mountFolder})" != "neo4j" ]]; then
            print_permissions_advice_and_fail "${mountFolder}" "${userid}" "${groupid}"
        fi
    fi
}

# ==== SETUP WHICH USER TO RUN AS ====
debug_msg "DEBUGGING ENABLED"

if running_as_root; then
  userid="neo4j"
  groupid="neo4j"
  groups=($(id -G neo4j))
  exec_cmd="runuser -p -u neo4j -g neo4j --"
  debug_msg "Running as root user inside neo4j-admin image"
else
  userid="$(id -u)"
  groupid="$(id -g)"
  groups=($(id -G))
  exec_cmd="exec"
  debug_msg "Running as user ${userid} inside neo4j-admin image"
fi

#%%DEPRECATION_WARNING_PLACEHOLDER%%

# ==== MAKE SURE NEO4J CANNOT BE RUN FROM THIS CONTAINER ====
debug_msg "checking neo4j was not requested"
if [[ "${1}" == "neo4j" ]]; then
    correct_image="neo4j:"$(neo4j-admin --version)"-${NEO4J_EDITION}"
    echo >&2 "
This is a neo4j-admin only image, and usage of Neo4j server is not supported from here.
If you wish to start a Neo4j database, use:

docker run ${correct_image}
    "
    exit 1
fi

# ==== MAKE SURE NEO4J-ADMIN REPORT CANNOT BE RUN FROM THIS CONTAINER ====
debug_msg "checking neo4j-admin report was not requested"
# maybe make sure the command is neo4j-admin server report rather than just anything mentioning reports?
if containsElement "report" "${@}"; then
    echo >&2 \
"neo4j-admin report must be run in the same container as neo4j
otherwise the report tool cannot access relevant files and processes required for generating the report.

To run the report tool inside a neo4j container, do:

docker exec <CONTAINER NAME> neo4j-admin-report

"
    exit 1
fi


# ==== CHECK LICENSE AGREEMENT ====

debug_msg "checking license"
# Only prompt for license agreement if command contains "neo4j" in it
if [[ "${1}" == *"neo4j"* ]]; then
    if [ "${NEO4J_EDITION}" == "enterprise" ]; then
        : ${NEO4J_ACCEPT_LICENSE_AGREEMENT:="not accepted"}
        if [[ "$NEO4J_ACCEPT_LICENSE_AGREEMENT" != "yes" && "$NEO4J_ACCEPT_LICENSE_AGREEMENT" != "eval" ]]; then
            echo >&2 "
In order to use Neo4j Enterprise Edition you must accept the license agreement.

The license agreement is available at https://neo4j.com/terms/licensing/
If you have a support contract the following terms apply https://neo4j.com/terms/support-terms/

If you do not have a commercial license and want to evaluate the Software
please read the terms of the evaluation agreement before you accept.
https://neo4j.com/terms/enterprise_us/

(c) Neo4j Sweden AB. All Rights Reserved.
Use of this Software without a proper commercial license, or evaluation license
with Neo4j, Inc. or its affiliates is prohibited.
Neo4j has the right to terminate your usage if you are not compliant.

More information is also available at: https://neo4j.com/licensing/
If you have further inquiries about licensing, please contact us via https://neo4j.com/contact-us/

To accept the commercial license agreement set the environment variable
NEO4J_ACCEPT_LICENSE_AGREEMENT=yes

To accept the terms of the evaluation agreement set the environment variable
NEO4J_ACCEPT_LICENSE_AGREEMENT=eval

To do this you can use the following docker argument:

        --env=NEO4J_ACCEPT_LICENSE_AGREEMENT=<yes|eval>
"
            exit 1
        fi
    fi
fi

# ==== ENSURE MOUNT FOLDER READ/WRITABILITY ====
debug_msg "Checking for mounted folder writability"

if [ -d /data ]; then
    check_mounted_folder_writable_with_chown "/data"
    if [ -d /data/databases ]; then
        check_mounted_folder_writable_with_chown "/data/databases"
    fi
    if [ -d /data/dbms ]; then
        check_mounted_folder_writable_with_chown "/data/dbms"
    fi
    if [ -d /data/transactions ]; then
        check_mounted_folder_writable_with_chown "/data/transactions"
    fi
fi
if [ -d /backups ]; then
    check_mounted_folder_writable_with_chown "/backups"
fi

# ==== START NEO4J-ADMIN COMMAND ====
if debugging_enabled; then
    echo ${exec_cmd} "${@}" --verbose
    ${exec_cmd} "${@}" --verbose
else
    ${exec_cmd} "${@}"
fi
