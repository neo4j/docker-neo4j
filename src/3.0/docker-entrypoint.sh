#!/bin/bash -eu

setting() {
    setting="${1}"
    value="${2}"
    file="${3:-neo4j.conf}"

    if [ ! -f "conf/${file}" ]; then
        if [ -f "conf/neo4j.conf" ]; then
            file="neo4j.conf"
        fi
    fi

    if [ -n "${value}" ]; then
        if grep --quiet --fixed-strings "${setting}=" conf/"${file}"; then
            sed --in-place "s|.*${setting}=.*|${setting}=${value}|" conf/"${file}"
        else
            echo "${setting}=${value}" >>conf/"${file}"
        fi
    fi
}

if [ "$1" == "neo4j" ]; then

    # Env variable naming convention:
    # - prefix NEO4J_
    # - double underscore char '__' instead of single underscore '_' char in the setting name
    # - underscore char '_' instead of dot '.' char in the setting name
    # Example:
    # NEO4J_dbms_tx__log_rotation_retention__policy env variable to set
    #       dbms.tx_log.rotation.retention_policy setting

    # Backward compatibility - map old hardcoded env variables into new naming convention (if they aren't set already)
    # Set some to default values if unset
    : ${NEO4J_dbms_tx__log_rotation_retention__policy:=${NEO4J_dbms_txLog_rotation_retentionPolicy:-"100M size"}}
    : ${NEO4J_wrapper_java_additional:=${NEO4J_UDC_SOURCE:-"-Dneo4j.ext.udc.source=docker"}}
    : ${NEO4J_dbms_memory_heap_initial__size:=${NEO4J_dbms_memory_heap_maxSize:-"512"}}
    : ${NEO4J_dbms_memory_heap_max__size:=${NEO4J_dbms_memory_heap_maxSize:-"512"}}
    : ${NEO4J_dbms_unmanaged__extension__classes:=${NEO4J_dbms_unmanagedExtensionClasses:-}}
    : ${NEO4J_dbms_allow__format__migration:=${NEO4J_dbms_allowFormatMigration:-}}
    : ${NEO4J_ha_server__id:=${NEO4J_ha_serverId:-}}
    : ${NEO4J_ha_initial__hosts:=${NEO4J_ha_initialHosts:-}}

    : ${NEO4J_dbms_connector_http_address:="0.0.0.0:7474"}
    : ${NEO4J_dbms_connector_https_address:="0.0.0.0:7473"}
    : ${NEO4J_dbms_connector_bolt_address:="0.0.0.0:7687"}
    : ${NEO4J_ha_host_coordination:="$(hostname):5001"}
    : ${NEO4J_ha_host_data:="$(hostname):6001"}

    # unset old hardcoded unsupported env variables
    unset NEO4J_dbms_txLog_rotation_retentionPolicy NEO4J_UDC_SOURCE \
        NEO4J_dbms_memory_heap_maxSize NEO4J_dbms_memory_heap_maxSize \
        NEO4J_dbms_unmanagedExtensionClasses NEO4J_dbms_allowFormatMigration \
        NEO4J_ha_initialHosts

    if [ -d /conf ]; then
        find /conf -type f -exec cp {} conf \;
    fi

    if [ -d /ssl ]; then
        NEO4J_dbms_directories_certificates="/ssl"
    fi

    if [ -d /plugins ]; then
        NEO4J_dbms_directories_plugins="/plugins"
    fi

    if [ -d /logs ]; then
        NEO4J_dbms_directories_logs="/logs"
    fi

    if [ -d /import ]; then
        NEO4J_dbms_directories_import="/import"
    fi

    if [ -d /metrics ]; then
        NEO4J_dbms_directories_metrics="/metrics"
    fi

    if [ "${NEO4J_AUTH:-}" == "none" ]; then
        NEO4J_dbms_security_auth__enabled=false
    elif [[ "${NEO4J_AUTH:-}" == neo4j/* ]]; then
        password="${NEO4J_AUTH#neo4j/}"
        if [ "${password}" == "neo4j" ]; then
            echo "Invalid value for password. It cannot be 'neo4j', which is the default."
            exit 1
        fi

        setting "dbms.connector.http.address" "127.0.0.1:7474"
        setting "dbms.connector.https.address" "127.0.0.1:7473"
        setting "dbms.connector.bolt.address" "127.0.0.1:7687"
        bin/neo4j start || \
            (cat logs/neo4j.log && echo "Neo4j failed to start for password change" && exit 1)

        end="$((SECONDS+100))"
        while true; do
            http_code="$(curl --silent --write-out %{http_code} --user "neo4j:${password}" --output /dev/null http://localhost:7474/db/data/ || true)"

            if [[ "${http_code}" = "200" ]]; then
                break;
            fi

            if [[ "${http_code}" = "401" ]]; then
                curl --fail --silent --show-error --user neo4j:neo4j \
                     --data '{"password": "'"${password}"'"}' \
                     --header 'Content-Type: application/json' \
                     http://localhost:7474/user/neo4j/password
                break;
            fi

            if [[ "${SECONDS}" -ge "${end}" ]]; then
                (cat logs/neo4j.log && echo "Neo4j failed to start" && exit 1)
            fi

            sleep 1
        done

        bin/neo4j stop
    elif [ -n "${NEO4J_AUTH:-}" ]; then
        echo "Invalid value for NEO4J_AUTH: '${NEO4J_AUTH}'"
        exit 1
    fi

    # list env variables with prefix NEO4J_ and create settings from them
    unset NEO4J_AUTH NEO4J_SHA256 NEO4J_TARBALL
    for i in $( set | grep ^NEO4J_ | awk -F'=' '{print $1}' | sort -rn ); do
        setting=$(echo ${i} | sed 's|^NEO4J_||' | sed 's|_|.|g' | sed 's|\.\.|_|g')
        value=$(echo ${!i})
        if [[ -n ${value} ]]; then
            if grep -q -F "${setting}=" conf/neo4j.conf; then
                # Remove any lines containing the setting already
                sed --in-place "/${setting}=.*/d" conf/neo4j.conf
            fi
            # Then always append setting to file
            echo "${setting}=${value}" >> conf/neo4j.conf
        fi
    done

    [ -f "${EXTENSION_SCRIPT:-}" ] && . ${EXTENSION_SCRIPT}

    exec bin/neo4j console
elif [ "$1" == "dump-config" ]; then
    if [ -d /conf ]; then
        cp --recursive conf/* /conf
    else
        echo "You must provide a /conf volume"
        exit 1
    fi
else
    exec "$@"
fi
