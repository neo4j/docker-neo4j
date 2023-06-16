#!/bin/bash -eu

cmd="$1"

function running_as_root
{
    test "$(id -u)" = "0"
}

function secure_mode_enabled
{
    test "${SECURE_FILE_PERMISSIONS:=no}" = "yes"
}

function containsElement
{
  local e match="$1"
  shift
  for e; do [[ "$e" == "$match" ]] && return 0; done
  return 1
}

function is_readable
{
    # this code is fairly ugly but works no matter who this script is running as.
    # It would be nice if the writability tests could use this logic somehow.
    local _file=${1}
    perm=$(stat -c %a "${_file}")

    # everyone permission
    if [[ ${perm:2:1} -ge 4 ]]; then
        return 0
    fi
    # owner permissions
    if [[ ${perm:0:1} -ge 4 ]]; then
        if [[ "$(stat -c %U ${_file})" = "${userid}" ]] || [[ "$(stat -c %u ${_file})" = "${userid}" ]]; then
            return 0
        fi
    fi
    # group permissions
    if [[ ${perm:1:1} -ge 4 ]]; then
        if containsElement "$(stat -c %g ${_file})" "${groups[@]}" || containsElement "$(stat -c %G ${_file})" "${groups[@]}" ; then
            return 0
        fi
    fi
    return 1
}

function is_writable
{
    # It would be nice if this and the is_readable function could combine somehow
    local _file=${1}
    perm=$(stat -c %a "${_file}")

    # everyone permission
    if containsElement ${perm:2:1} 2 3 6 7; then
        return 0
    fi
    # owner permissions
    if containsElement ${perm:0:1} 2 3 6 7; then
        if [[ "$(stat -c %U ${_file})" = "${userid}" ]] || [[ "$(stat -c %u ${_file})" = "${userid}" ]]; then
            return 0
        fi
    fi
    # group permissions
    if containsElement ${perm:1:1} 2 3 6 7; then
        if containsElement "$(stat -c %g ${_file})" "${groups[@]}" || containsElement "$(stat -c %G ${_file})" "${groups[@]}" ; then
            return 0
        fi
    fi
    return 1
}

function expand_commands_optionally
{
    if [ "${EXTENDED_CONF+"yes"}" == "yes" ]; then
        echo "--expand-commands"
    fi
}

function print_permissions_advice_and_fail
{
    _directory=${1}
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

function check_mounted_folder_readable
{
    local _directory=${1}
    if ! is_readable "${_directory}"; then
        print_permissions_advice_and_fail "${_directory}"
    fi
}

function check_mounted_folder_writable_with_chown
{
# The /data and /log directory are a bit different because they are very likely to be mounted by the user but not
# necessarily writable.
# This depends on whether a user ID is passed to the container and which folders are mounted.
#
#   No user ID passed to container:
#   1) No folders are mounted.
#      The /data and /log folder are owned by neo4j by default, so should be writable already.
#   2) Both /log and /data are mounted.
#      This means on start up, /data and /logs are owned by an unknown user and we should chown them to neo4j for
#      backwards compatibility.
#
#   User ID passed to container:
#   1) Both /data and /logs are mounted
#      The /data and /logs folders are owned by an unknown user but we *should* have rw permission to them.
#      That should be verified and error (helpfully) if not.
#   2) User mounts /data or /logs *but not both*
#      The  unmounted folder is still owned by neo4j, which should already be writable. The mounted folder should
#      have rw permissions through user id. This should be verified.
#   3) No folders are mounted.
#      The /data and /log folder are owned by neo4j by default, and these are already writable by the user.
#      (This is a very unlikely use case).

    local mountFolder=${1}
    if running_as_root && ! secure_mode_enabled; then
        # check folder permissions
        if ! is_writable "${mountFolder}" ;  then
            # warn that we're about to chown the folder and then chown it
            echo "Warning: Folder mounted to \"${mountFolder}\" is not writable from inside container. Changing folder owner to ${userid}."
            chown -R "${userid}":"${groupid}" "${mountFolder}"
        # check permissions on files in the folder
        elif [ $(gosu "${userid}":"${groupid}" find "${mountFolder}" -not -writable | wc -l) -gt 0 ]; then
            echo "Warning: Some files inside \"${mountFolder}\" are not writable from inside container. Changing folder owner to ${userid}."
            chown -R "${userid}":"${groupid}" "${mountFolder}"
        fi
    else
        if ! is_writable "${mountFolder}"; then
        #if [[ ! -w "${mountFolder}" ]]  && [[ "$(stat -c %U ${mountFolder})" != "neo4j" ]]; then
            echo >&2 "Consider unsetting SECURE_FILE_PERMISSIONS environment variable, to enable docker to write to ${mountFolder}."
            print_permissions_advice_and_fail "${mountFolder}"
        fi
    fi
}

function load_plugin_from_github
{
  # Load a plugin at runtime. The provided github repository must have a versions.json on the master branch with the
  # correct format.
  local _plugin_name="${1}" #e.g. apoc, graph-algorithms, graph-ql

  local _plugins_dir="${NEO4J_HOME}/plugins"
  if [ -d /plugins ]; then
    local _plugins_dir="/plugins"
  fi
  local _versions_json_url="$(jq --raw-output "with_entries( select(.key==\"${_plugin_name}\") ) | to_entries[] | .value.versions" /startup/neo4j-plugins.json )"
  # Using the same name for the plugin irrespective of version ensures we don't end up with different versions of the same plugin
  local _destination="${_plugins_dir}/${_plugin_name}.jar"
  local _neo4j_version="$(neo4j --version | cut -d' ' -f2)"

  # Now we call out to github to get the versions.json for this plugin and we parse that to find the url for the correct plugin jar for our neo4j version
  echo "Fetching versions.json for Plugin '${_plugin_name}' from ${_versions_json_url}"
  local _versions_json="$(wget -q --timeout 300 --tries 30 -O - "${_versions_json_url}")"
  local _plugin_jar_url="$(echo "${_versions_json}" | jq -L/startup --raw-output "import \"semver\" as lib; [ .[] | select(.neo4j|lib::semver(\"${_neo4j_version}\")) ] | min_by(.neo4j) | .jar")"
  if [[ -z "${_plugin_jar_url}" ]]; then
    echo >&2 "Error: No jar URL found for version '${_neo4j_version}' in versions.json from '${_versions_json_url}'"
    exit 1
  fi
  echo "Installing Plugin '${_plugin_name}' from ${_plugin_jar_url} to ${_destination} "
  wget -q --timeout 300 --tries 30 --output-document="${_destination}" "${_plugin_jar_url}"

  if ! is_readable "${_destination}"; then
    echo >&2 "Plugin at '${_destination}' is not readable"
    exit 1
  fi
}

function apply_plugin_default_configuration
{
  # Set the correct Load a plugin at runtime. The provided github repository must have a versions.json on the master branch with the
  # correct format.
  local _plugin_name="${1}" #e.g. apoc, graph-algorithms, graph-ql
  local _reference_conf="${2}" # used to determine if we can override properties
  local _neo4j_conf="${NEO4J_HOME}/conf/neo4j.conf"

  local _property _value
  echo "Applying default values for plugin ${_plugin_name} to neo4j.conf"
  for _entry in $(jq  --compact-output --raw-output "with_entries( select(.key==\"${_plugin_name}\") ) | to_entries[] | .value.properties | to_entries[]" /startup/neo4j-plugins.json); do
    _property="$(jq --raw-output '.key' <<< "${_entry}")"
    _value="$(jq --raw-output '.value' <<< "${_entry}")"

    # the first grep strips out comments
    if grep -o "^[^#]*" "${_reference_conf}" | grep -q --fixed-strings "${_property}=" ; then
      # property is already set in the user provided config. In this case we don't override what has been set explicitly by the user.
      echo "Skipping ${_property} for plugin ${_plugin_name} because it is already set"
    else
      if grep -o "^[^#]*" "${_neo4j_conf}" | grep -q --fixed-strings "${_property}=" ; then
        sed --in-place "s/${_property}=/&${_value},/" "${_neo4j_conf}"
      else
        echo "${_property}=${_value}" >> "${_neo4j_conf}"
      fi
    fi
  done
}

function install_neo4j_labs_plugins
{
  # We store a copy of the config before we modify it for the plugins to allow us to see if there are user-set values in the input config that we shouldn't override
  local _old_config="$(mktemp)"
  cp "${NEO4J_HOME}"/conf/neo4j.conf "${_old_config}"
  for plugin_name in $(echo "${NEO4JLABS_PLUGINS}" | jq --raw-output '.[]'); do
    load_plugin_from_github "${plugin_name}"
    apply_plugin_default_configuration "${plugin_name}" "${_old_config}"
  done
  rm "${_old_config}"
}

function add_docker_default_to_conf
{
    # docker defaults should NOT overwrite values already in the conf file
    local _setting="${1}"
    local _value="${2}"
    local _neo4j_home="${3}"

    if ! grep -q "^${_setting}=" "${_neo4j_home}"/conf/neo4j.conf
    then
        echo -e "\n"${_setting}=${_value} >> "${_neo4j_home}"/conf/neo4j.conf
    fi
}

function add_env_setting_to_conf
{
    # settings from environment variables should overwrite values already in the conf
    local _setting=${1}
    local _value=${2}
    local _neo4j_home=${3}

    if grep -q -F "${_setting}=" "${_neo4j_home}"/conf/neo4j.conf; then
        # Remove any lines containing the setting already
        sed --in-place "/^${_setting}=.*/d" "${_neo4j_home}"/conf/neo4j.conf
    fi
    # Then always append setting to file
    echo "${_setting}=${_value}" >> "${_neo4j_home}"/conf/neo4j.conf
}

function set_initial_password
{
    local _neo4j_auth="${1}"

    # set the neo4j initial password only if you run the database server
    if [ "${cmd}" == "neo4j" ]; then
        if [ "${_neo4j_auth:-}" == "none" ]; then
            add_env_setting_to_conf "dbms.security.auth_enabled" "false" "${NEO4J_HOME}"
            # NEO4J_dbms_security_auth__enabled=false
        elif [[ "${_neo4j_auth:-}" =~ ^([^/]+)\/([^/]+)/?([tT][rR][uU][eE])?$ ]]; then
            admin_user="${BASH_REMATCH[1]}"
            password="${BASH_REMATCH[2]}"
            do_reset="${BASH_REMATCH[3]}"

            if [ "${password}" == "neo4j" ]; then
                echo >&2 "Invalid value for password. It cannot be 'neo4j', which is the default."
                exit 1
            fi
            if [ "${admin_user}" != "neo4j" ]; then
                echo >&2 "Invalid admin username, it must be neo4j"
                exit 1
            fi

            if running_as_root; then
                # running set-initial-password as root will create subfolders to /data as root, causing startup fail when neo4j can't read or write the /data/dbms folder
                # creating the folder first will avoid that
                mkdir -p /data/dbms
                chown "${userid}":"${groupid}" /data/dbms
            fi

            # Will exit with error if users already exist (and print a message explaining that)
            # we probably don't want the message though, since it throws an error message on restarting the container.
            if [ "${do_reset}" == "true" ]; then
                ${neo4j_admin_cmd} set-initial-password "${password}" --require-password-change $(expand_commands_optionally) 2>/dev/null || true
            else
                ${neo4j_admin_cmd} set-initial-password "${password}" $(expand_commands_optionally) 2>/dev/null || true
            fi
        elif [ -n "${_neo4j_auth:-}" ]; then
            echo "$_neo4j_auth is invalid"
            echo >&2 "Invalid value for NEO4J_AUTH: '${_neo4j_auth}'"
            exit 1
        fi
    fi
}

# If we're running as root, then run as the neo4j user. Otherwise
# docker is running with --user and we simply use that user.  Note
# that su-exec, despite its name, does not replicate the functionality
# of exec, so we need to use both
if running_as_root; then
  userid="neo4j"
  groupid="neo4j"
  groups=($(id -G neo4j))
  exec_cmd="exec gosu neo4j:neo4j"
  neo4j_admin_cmd="gosu neo4j:neo4j neo4j-admin"
else
  userid="$(id -u)"
  groupid="$(id -g)"
  groups=($(id -G))
  exec_cmd="exec"
  neo4j_admin_cmd="neo4j-admin"
fi
readonly userid
readonly groupid
readonly groups
readonly exec_cmd
readonly neo4j_admin_cmd


# Need to chown the home directory
if running_as_root; then
    chown -R "${userid}":"${groupid}" "${NEO4J_HOME}"
    chmod 700 "${NEO4J_HOME}"
    find "${NEO4J_HOME}" -mindepth 1 -maxdepth 1 -type d -exec chmod -R 700 {} \;
    find "${NEO4J_HOME}"/conf -type f -exec chmod -R 600 {} \;
fi

# ==== CHECK LICENSE AGREEMENT ====

# Only prompt for license agreement if command contains "neo4j" in it
if [[ "${cmd}" == *"neo4j"* ]]; then
  if [ "${NEO4J_EDITION}" == "enterprise" ]; then
    if [ "${NEO4J_ACCEPT_LICENSE_AGREEMENT:=no}" != "yes" ]; then
      echo >&2 "
In order to use Neo4j Enterprise Edition you must accept the license agreement.

(c) Neo4j Sweden AB. 2021.  All Rights Reserved.
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
fi

# ==== RENAME LEGACY ENVIRONMENT CONF VARIABLES ====

# Env variable naming convention:
# - prefix NEO4J_
# - double underscore char '__' instead of single underscore '_' char in the setting name
# - underscore char '_' instead of dot '.' char in the setting name
# Example:
# NEO4J_dbms_tx__log_rotation_retention__policy env variable to set
#       dbms.tx_log.rotation.retention_policy setting

# Backward compatibility - map old hardcoded env variables into new naming convention (if they aren't set already)
# Set some to default values if unset
: ${NEO4J_dbms_tx__log_rotation_retention__policy:=${NEO4J_dbms_txLog_rotation_retentionPolicy:-}}
: ${NEO4J_dbms_unmanaged__extension__classes:=${NEO4J_dbms_unmanagedExtensionClasses:-}}
: ${NEO4J_dbms_allow__format__migration:=${NEO4J_dbms_allowFormatMigration:-}}
: ${NEO4J_dbms_connectors_default__advertised__address:=${NEO4J_dbms_connectors_defaultAdvertisedAddress:-}}

if [ "${NEO4J_EDITION}" == "enterprise" ];
  then
   : ${NEO4J_causal__clustering_expected__core__cluster__size:=${NEO4J_causalClustering_expectedCoreClusterSize:-}}
   : ${NEO4J_causal__clustering_initial__discovery__members:=${NEO4J_causalClustering_initialDiscoveryMembers:-}}
   : ${NEO4J_causal__clustering_discovery__advertised__address:=${NEO4J_causalClustering_discoveryAdvertisedAddress:-}}
   : ${NEO4J_causal__clustering_transaction__advertised__address:=${NEO4J_causalClustering_transactionAdvertisedAddress:-}}
   : ${NEO4J_causal__clustering_raft__advertised__address:=${NEO4J_causalClustering_raftAdvertisedAddress:-}}
fi

# unset old hardcoded unsupported env variables
unset NEO4J_dbms_txLog_rotation_retentionPolicy NEO4J_UDC_SOURCE \
    NEO4J_dbms_unmanagedExtensionClasses NEO4J_dbms_allowFormatMigration \
    NEO4J_dbms_connectors_defaultAdvertisedAddress NEO4J_ha_serverId \
    NEO4J_ha_initialHosts NEO4J_causalClustering_expectedCoreClusterSize \
    NEO4J_causalClustering_initialDiscoveryMembers \
    NEO4J_causalClustering_discoveryListenAddress \
    NEO4J_causalClustering_discoveryAdvertisedAddress \
    NEO4J_causalClustering_transactionListenAddress \
    NEO4J_causalClustering_transactionAdvertisedAddress \
    NEO4J_causalClustering_raftListenAddress \
    NEO4J_causalClustering_raftAdvertisedAddress


# ==== CHECK FILE PERMISSIONS ON MOUNTED FOLDERS ====

if [ -d /conf ]; then
    check_mounted_folder_readable "/conf"
    rm -rf "${NEO4J_HOME}"/conf/*
    find /conf -type f -exec cp --preserve=ownership,mode {} "${NEO4J_HOME}"/conf \;
fi

if [ -d /ssl ]; then
    check_mounted_folder_readable "/ssl"
    rm -rf "${NEO4J_HOME}"/certificates
    ln -s /ssl "${NEO4J_HOME}"/certificates
fi

if [ -d /plugins ]; then
    if [[ -n "${NEO4JLABS_PLUGINS:-}" ]]; then
        # We need write permissions
        check_mounted_folder_writable_with_chown "/plugins"
    fi
    check_mounted_folder_readable "/plugins"
    : ${NEO4J_dbms_directories_plugins:="/plugins"}
fi

if [ -d /import ]; then
    check_mounted_folder_readable "/import"
    : ${NEO4J_dbms_directories_import:="/import"}
fi

if [ -d /metrics ]; then
    # metrics is enterprise only
    if [ "${NEO4J_EDITION}" == "enterprise" ];
    then
        check_mounted_folder_writable_with_chown "/metrics"
        : ${NEO4J_dbms_directories_metrics:="/metrics"}
    fi
fi

if [ -d /logs ]; then
    check_mounted_folder_writable_with_chown "/logs"
    : ${NEO4J_dbms_directories_logs:="/logs"}
fi

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

if [ -d /licenses ]; then
    check_mounted_folder_readable "/licenses"
    : ${NEO4J_dbms_directories_licenses:="/licenses"}
fi

# ==== SET CONFIGURATIONS ====

## == DOCKER SPECIFIC DEFAULT CONFIGURATIONS ===
## these should not override *any* configurations set by the user

add_docker_default_to_conf "dbms.tx_log.rotation.retention_policy" "100M size" "${NEO4J_HOME}"
add_docker_default_to_conf "dbms.memory.pagecache.size" "512M" "${NEO4J_HOME}"
add_docker_default_to_conf "dbms.default_listen_address" "0.0.0.0" "${NEO4J_HOME}"
# set enterprise only docker defaults
if [ "${NEO4J_EDITION}" == "enterprise" ];
then
    add_docker_default_to_conf "causal_clustering.discovery_advertised_address" "$(hostname):5000" "${NEO4J_HOME}"
    add_docker_default_to_conf "causal_clustering.transaction_advertised_address" "$(hostname):6000" "${NEO4J_HOME}"
    add_docker_default_to_conf "causal_clustering.raft_advertised_address" "$(hostname):7000" "${NEO4J_HOME}"
fi

## == ENVIRONMENT VARIABLE CONFIGURATIONS ===
## these override BOTH defaults and any existing values in the neo4j.conf file

# save NEO4J_HOME and NEO4J_AUTH to temp variables that don't begin with NEO4J_ so they don't get added to the conf
temp_neo4j_home="${NEO4J_HOME}"
temp_neo4j_auth="${NEO4J_AUTH:-}"
# list env variables with prefix NEO4J_ and create settings from them
unset NEO4J_AUTH NEO4J_SHA256 NEO4J_TARBALL NEO4J_EDITION NEO4J_ACCEPT_LICENSE_AGREEMENT NEO4J_HOME
for i in $( set | grep ^NEO4J_ | awk -F'=' '{print $1}' | sort -rn ); do
    setting=$(echo "${i}" | sed 's|^NEO4J_||' | sed 's|_|.|g' | sed 's|\.\.|_|g')
    value=$(echo "${!i}")
    # Don't allow settings with no value or settings that start with a number (neo4j converts settings to env variables and you cannot have an env variable that starts with a number)
    if [[ -n ${value} ]]; then
        if [[ ! "${setting}" =~ ^[0-9]+.*$ ]]; then
            add_env_setting_to_conf "${setting}" "${value}" "${temp_neo4j_home}"
        else
            echo >&2 "WARNING: ${setting} not written to conf file because settings that start with a number are not permitted"
        fi
    fi
done
export NEO4J_HOME="${temp_neo4j_home}"
unset temp_neo4j_home

# ==== SET PASSWORD AND PLUGINS ====

set_initial_password "${temp_neo4j_auth}"


if [[ ! -z "${NEO4JLABS_PLUGINS:-}" ]]; then
  # NEO4JLABS_PLUGINS should be a json array of plugins like '["graph-algorithms", "apoc", "streams", "graphql"]'
  install_neo4j_labs_plugins
fi

# ==== INVOKE NEO4J STARTUP ====

[ -f "${EXTENSION_SCRIPT:-}" ] && . ${EXTENSION_SCRIPT}

if [ "${cmd}" == "dump-config" ]; then
    if [ ! -d "/conf" ]; then
        echo >&2 "You must mount a folder to /conf so that the configuration file(s) can be dumped to there."
        exit 1
    fi
    check_mounted_folder_writable_with_chown "/conf"
    cp --recursive "${NEO4J_HOME}"/conf/* /conf
    echo "Config Dumped"
    exit 0
fi

# Use su-exec to drop privileges to neo4j user
# Note that su-exec, despite its name, does not replicate the
# functionality of exec, so we need to use both
if [ "${cmd}" == "neo4j" ]; then
    if [ "${EXTENDED_CONF+"yes"}" == "yes" ]; then
        ${exec_cmd} neo4j console --expand-commands
    else
        ${exec_cmd} neo4j console
    fi
else
  ${exec_cmd} "$@"
fi
