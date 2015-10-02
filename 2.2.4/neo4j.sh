#!/bin/bash -eu

if [ -n "${NEO4J_OPEN_FILES:-}" ]; then
    ulimit -n $NEO4J_OPEN_FILES >/dev/null
fi

if [ -n "${NEO4J_HEAP_MEMORY:-}" ]; then
    echo "wrapper.java.additional=-Xmx${NEO4J_HEAP_MEMORY}" >>conf/neo4j-wrapper.conf
    echo "wrapper.java.additional=-Xms${NEO4J_HEAP_MEMORY}" >>conf/neo4j-wrapper.conf
fi

if [ -n "${NEO4J_CACHE_MEMORY:-}" ]; then
    sed --in-place "s|.*dbms.pagecache.memory=.*|dbms.pagecache.memory=${NEO4J_CACHE_MEMORY}|g" conf/neo4j.properties
fi

if [ ! -z ${NEO4J_NO_AUTH+x} ]; then
    sed --in-place "s|dbms.security.auth_enabled=.*|dbms.security.auth_enabled=false|g" conf/neo4j-server.properties
fi

if [ ! -z ${NEO4J_UDC_SOURCE+x} ]; then
    sed --in-place "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=${NEO4J_UDC_SOURCE}|g" conf/neo4j-wrapper.conf
fi

if [ -d /conf ]; then
    cp --recursive /conf/ conf/
fi

sed --in-place "s|#org.neo4j.server.webserver.address=0.0.0.0|org.neo4j.server.webserver.address=$HOSTNAME|g" conf/neo4j-server.properties

exec bin/neo4j console
