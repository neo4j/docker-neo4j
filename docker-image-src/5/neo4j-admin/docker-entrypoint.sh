#!/bin/bash -eu

function running_as_root
{
    test "$(id -u)" = "0"
}

function is_writable
{
    ${exec_cmd} test -w "${1}"
}

function print_permissions_advice_and_fail
{
    local _directory=${1}
    echo >&2 "
Folder ${_directory} is not accessible for user: ${userid} or group ${groupid} or groups ${groups[@]}, this is commonly a file permissions issue on the mounted folder.

Hints to solve the issue:
1) Make sure the folder exists before mounting it. Docker will create the folder using root permissions before starting the Neo4j container. The root permissions disallow Neo4j from writing to the mounted folder.
2) Pass the folder owner's user ID and group ID to docker run, so that docker runs as that user.
If the folder is owned by the current user, this can be done by adding this flag to your docker run command:
  --user=\$(id -u):\$(id -g)
       "
    exit 1
}


function check_mounted_folder_writable_with_chown
{
    local mountFolder=${1}
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
            print_permissions_advice_and_fail "${mountFolder}"
        fi
    fi
}

# ==== SETUP WHICH USER TO RUN AS ====

if running_as_root; then
  userid="neo4j"
  groupid="neo4j"
  groups=($(id -G neo4j))
  exec_cmd="runuser -p -u neo4j -g neo4j --"
else
  userid="$(id -u)"
  groupid="$(id -g)"
  groups=($(id -G))
  exec_cmd="exec"
fi


# ==== CHECK LICENSE AGREEMENT ====

if [ "${NEO4J_EDITION}" == "enterprise" ]; then
    if [ "${NEO4J_ACCEPT_LICENSE_AGREEMENT:=no}" != "yes" ]; then
      echo >&2 "
In order to use Neo4j Enterprise Edition you must accept the license agreement.

(c) Neo4j Sweden AB. 2022.  All Rights Reserved.
Use of this Software without a proper commercial license with Neo4j,
Inc. or its affiliates is prohibited.

Email inquiries can be directed to: licensing@neo4j.com

More information is also available at: https://neo4j.com/licensing/


To accept the license agreement set the environment variable
NEO4J_ACCEPT_LICENSE_AGREEMENT=yes

To do this you can use the following docker argument:

    --env=NEO4J_ACCEPT_LICENSE_AGREEMENT=yes
"
      exit 1
    fi
fi

# ==== ENSURE MOUNT FOLDER READ/WRITABILITY ====

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

# ==== MAKE SURE NEO4J CANNOT BE RUN FROM THIS CONTAINER ====

if [[ "${1}" == "neo4j" ]]; then
    correct_image="neo4j:"$(neo4j-admin --version)"-${NEO4J_EDITION}"
    echo >&2 "
This is a neo4j-admin only image, and usage of Neo4j server is not supported from here.
If you wish to start a Neo4j database, use:

docker run ${correct_image}
    "
    exit 1
fi

# ==== START NEO4J-ADMIN COMMAND ====

${exec_cmd} "${@}"
