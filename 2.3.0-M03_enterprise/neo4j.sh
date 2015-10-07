#!/bin/bash -eu

if [ -d /conf ]; then
    rm --recursive --force conf
    ln --symbolic /conf
else
    sed --in-place "s|#org.neo4j.server.webserver.address=0.0.0.0|org.neo4j.server.webserver.address=0.0.0.0|g" conf/neo4j-server.properties

    sed --in-place "s|.*keep_logical_logs=.*|keep_logical_logs=${NEO4J_KEEP_LOGICAL_LOGS:-100M size}|g" conf/neo4j.properties
    sed --in-place "s|.*dbms.pagecache.memory=.*|dbms.pagecache.memory=${NEO4J_CACHE_MEMORY:-512M}|g" conf/neo4j.properties
    sed --in-place "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=${NEO4J_UDC_SOURCE:-docker}|g" conf/neo4j-wrapper.conf

    if [ -n "${NEO4J_HEAP_MEMORY:-}" ]; then
        echo "wrapper.java.additional=-Xmx${NEO4J_HEAP_MEMORY}" >>conf/neo4j-wrapper.conf
        echo "wrapper.java.additional=-Xms${NEO4J_HEAP_MEMORY}" >>conf/neo4j-wrapper.conf
    fi

    if [ "${NEO4J_AUTH:-}" == "none" ]; then
        sed --in-place "s|dbms.security.auth_enabled=.*|dbms.security.auth_enabled=false|g" \
            conf/neo4j-server.properties
    elif [[ "${NEO4J_AUTH:-}" == neo4j/* ]]; then
        password="${NEO4J_AUTH#neo4j/}"
        bin/neo4j start
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
fi

exec bin/neo4j console
