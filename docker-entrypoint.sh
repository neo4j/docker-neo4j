#!/bin/bash -eu

setting() {
    setting="${1}"
    value="${2}"
    file="${3}"

    if [ ! -f "conf/${file}" ]; then
        if [ -f "conf/neo4j.conf" ]; then
            file="neo4j.conf"
        fi
    fi

    if [ -n "${value}" ]; then
        sed --in-place "s|.*${setting}=.*|${setting}=${value}|" conf/"${file}"
    fi
}

if [ "$1" == "neo4j" ]; then
    setting "dbms.tx_log.rotation.retention_policy" "${NEO4J_KEEP_LOGICAL_LOGS:-100M size}" neo4j.properties
    setting "dbms.memory.pagecache.size" "${NEO4J_CACHE_MEMORY:-512M}" neo4j.properties
    setting "wrapper.java.additional=-Dneo4j.ext.udc.source" "${NEO4J_UDC_SOURCE:-docker}" neo4j-wrapper.conf
    setting "dbms.memory.heap.initial_size" "${NEO4J_HEAP_MEMORY:-512}" neo4j-wrapper.conf
    setting "dbms.memory.heap.max_size" "${NEO4J_HEAP_MEMORY:-512}" neo4j-wrapper.conf
    setting "dbms.unmanaged_extension_classes" "${NEO4J_THIRDPARTY_JAXRS_CLASSES:-}" neo4j-server.properties
    setting "dbms.allow_format_migration" "${NEO4J_ALLOW_STORE_UPGRADE:-}" neo4j.properties

    if [ "${NEO4J_AUTH:-}" == "none" ]; then
        setting "dbms.security.auth_enabled" "false" neo4j-server.properties
    elif [[ "${NEO4J_AUTH:-}" == neo4j/* ]]; then
        password="${NEO4J_AUTH#neo4j/}"
        bin/neo4j start || \
            (cat logs/neo4j.log && echo "Neo4j failed to start" && exit 1)

        end="$((SECONDS+10))"
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

    setting "dbms.connector.http.address" "0.0.0.0:7474" neo4j-server.properties
    setting "dbms.connector.https.address" "0.0.0.0:7473" neo4j-server.properties
    setting "dbms.connector.bolt.address" "0.0.0.0:7687" neo4j-server.properties
    setting "dbms.mode" "${NEO4J_DATABASE_MODE:-}" neo4j-server.properties
    setting "ha.server_id" "${NEO4J_SERVER_ID:-}" neo4j.properties
    setting "ha.host.data" "${NEO4J_HA_ADDRESS:-}:6001" neo4j.properties
    setting "ha.host.coordination" "${NEO4J_HA_ADDRESS:-}:5001" neo4j.properties
    setting "ha.initial_hosts" "${NEO4J_INITIAL_HOSTS:-}" neo4j.properties

    [ -f "${EXTENSION_SCRIPT:-}" ] && . ${EXTENSION_SCRIPT}

    if [ -d /conf ]; then
        find /conf -type f -exec cp {} conf \;
    fi

    if [ -d /ssl ]; then
        setting "dbms.directories.certificates" "/ssl" neo4j.conf
    fi

    if [ -d /plugins ]; then
        setting "dbms.directories.plugins" "/plugins" neo4j.conf
    fi

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
