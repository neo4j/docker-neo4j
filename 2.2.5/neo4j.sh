#!/bin/bash -eu

if [ -d /conf ]; then
    rm --recursive --force conf
    ln --symbolic /conf
else
    sed --in-place "s|.*keep_logical_logs=.*|keep_logical_logs=100M size|g" conf/neo4j.properties
    sed --in-place "s|#org.neo4j.server.webserver.address=0.0.0.0|org.neo4j.server.webserver.address=0.0.0.0|g" conf/neo4j-server.properties

    sed --in-place "s|.*dbms.pagecache.memory=.*|dbms.pagecache.memory=${NEO4J_CACHE_MEMORY:-512M}|g" conf/neo4j.properties
    sed --in-place "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=${NEO4J_UDC_SOURCE:-docker}|g" conf/neo4j-wrapper.conf

    if [ -n "${NEO4J_OPEN_FILES:-}" ]; then
        ulimit -n $NEO4J_OPEN_FILES >/dev/null
    fi

    if [ -n "${NEO4J_HEAP_MEMORY:-}" ]; then
        echo "wrapper.java.additional=-Xmx${NEO4J_HEAP_MEMORY}" >>conf/neo4j-wrapper.conf
        echo "wrapper.java.additional=-Xms${NEO4J_HEAP_MEMORY}" >>conf/neo4j-wrapper.conf
    fi

    if [ -n "${NEO4J_NO_AUTH:-}" ]; then
        sed --in-place "s|dbms.security.auth_enabled=.*|dbms.security.auth_enabled=false|g" conf/neo4j-server.properties
    fi
fi

exec bin/neo4j console
