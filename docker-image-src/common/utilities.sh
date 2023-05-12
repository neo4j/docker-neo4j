
function running_as_root
{
    test "$(id -u)" = "0"
}

function secure_mode_enabled
{
    test "${SECURE_FILE_PERMISSIONS:=no}" = "yes"
}

function debugging_enabled
{
    test "${NEO4J_DEBUG+yes}" = "yes"
}

function debug_msg
{
    if debugging_enabled; then
        echo "$@"
    fi
}

function containsElement
{
  local e match="$1"
  shift
  for e; do [[ "$e" == "$match" ]] && return 0; done
  return 1
}

function is_writable
{
    ${exec_cmd} test -w "${1}"
}

function print_permissions_advice_and_fail
{
    local _directory=${1}
    local _userid=${2}
    local _groupid=${3}
    echo >&2 "
Folder ${_directory} is not accessible for user: ${_userid} or group ${_groupid}. This is commonly a file permissions issue on the mounted folder.

Hints to solve the issue:
1) Make sure the folder exists before mounting it. Docker will create the folder using root permissions before starting the Neo4j container. The root permissions disallow Neo4j from writing to the mounted folder.
2) Pass the folder owner's user ID and group ID to docker run, so that docker runs as that user.
If the folder is owned by the current user, this can be done by adding this flag to your docker run command:
  --user=\$(id -u):\$(id -g)
       "
    exit 1
}


