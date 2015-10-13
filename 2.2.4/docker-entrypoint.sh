#!/bin/bash -eu

setting() {
    setting="${1}"
    value="${2}"
    file="${3}"
    if [ -n "${value}" ]; then
        sed --in-place "s|.*${setting}=.*|${setting}=${value}|" conf/"${file}"
    fi
}

if [ "$1" == "neo4j" ]; then
    if [ -d /conf ]; then
        rm --recursive --force conf
        ln --symbolic /conf
    else
        setting "keep_logical_logs" "${NEO4J_KEEP_LOGICAL_LOGS:-100M size}" neo4j.properties
        setting "dbms.pagecache.memory" "${NEO4J_CACHE_MEMORY:-512M}" neo4j.properties
        setting "wrapper.java.additional=-Dneo4j.ext.udc.source" "${NEO4J_UDC_SOURCE:-docker}" neo4j-wrapper.conf
        setting "wrapper.java.initmemory" "${NEO4J_HEAP_MEMORY:-}" neo4j-wrapper.conf
        setting "wrapper.java.maxmemory" "${NEO4J_HEAP_MEMORY:-}" neo4j-wrapper.conf
        setting "org.neo4j.server.thirdparty_jaxrs_classes" "${NEO4J_THIRDPARTY_JAXRS_CLASSES:-}" neo4j-server.properties

        if [ "${NEO4J_AUTH:-}" == "none" ]; then
            setting "dbms.security.auth_enabled" "false" neo4j-server.properties
        elif [[ "${NEO4J_AUTH:-}" == neo4j/* ]]; then
            password="${NEO4J_AUTH#neo4j/}"
            bin/neo4j start || \
                (cat data/log/console.log && echo "Neo4j failed to start" && exit 1)
            if ! curl --fail --silent --user "neo4j:${password}" http://localhost:7474/db/data/ >/dev/null ; then
                curl --fail --silent --show-error --user neo4j:neo4j \
                     --data '{"password": "'"${password}"'"}' \
                     --header 'Content-Type: application/json' \
                     http://localhost:7474/user/neo4j/password
            fi
            bin/neo4j stop
        elif [ -n "${NEO4J_AUTH:-}" ]; then
            echo "Invalid value for NEO4J_AUTH: '${NEO4J_AUTH}'"
            exit 1
        fi

        setting "org.neo4j.server.webserver.address" "0.0.0.0" neo4j-server.properties
        setting "org.neo4j.server.database.mode" "${NEO4J_DATABASE_MODE:-}" neo4j-server.properties
        setting "ha.server_id" "${NEO4J_SERVER_ID:-}" neo4j.properties
        setting "ha.server" "${NEO4J_HA_ADDRESS:-}:6001" neo4j.properties
        setting "ha.cluster_server" "${NEO4J_HA_ADDRESS:-}:5001" neo4j.properties
        setting "ha.initial_hosts" "${NEO4J_INITIAL_HOSTS:-}" neo4j.properties
    fi

    if [ -d /plugins ]; then
        rm --recursive --force plugins
        ln --symbolic /plugins
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
