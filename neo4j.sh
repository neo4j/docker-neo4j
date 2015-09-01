#!/bin/bash
set -e

NEO4J_HOME=/var/lib/neo4j
cd $NEO4J_HOME

if [ -n "$NEO4J_OPEN_FILES" ]; then
	ulimit -n $NEO4J_OPEN_FILES > /dev/null
else
	ulimit -n 40000 > /dev/null
fi

# NEO4J_HEAP_MEMORY=2G
if [ -n "$NEO4J_HEAP_MEMORY" ]; then
	echo "wrapper.java.additional=-Xmx${NEO4J_HEAP_MEMORY}" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
	echo "wrapper.java.additional=-Xms${NEO4J_HEAP_MEMORY}" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
fi

# NEO4J_CACHE_MEMORY=2G
if [ -n "$NEO4J_CACHE_MEMORY" ]; then
	sed -i -e "s|.*dbms.pagecache.memory=.*|dbms.pagecache.memory=${NEO4J_CACHE_MEMORY}|g" $NEO4J_HOME/conf/neo4j.properties
fi

if [ ! -z ${NEO4J_NO_AUTH+x} ]; then
	sed -i -e "s|dbms.security.auth_enabled=.*|dbms.security.auth_enabled=false|g" $NEO4J_HOME/conf/neo4j-server.properties
fi

if [ ! -z ${NEO4J_UDC_SOURCE+x} ]; then
	sed -i -e "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=${NEO4J_UDC_SOURCE}|g" $NEO4J_HOME/conf/neo4j-wrapper.conf
fi

if [ -d /conf ]; then
	cp -R /conf/ $NEO4J_HOME/conf/
fi

sed -i "s|#org.neo4j.server.webserver.address=0.0.0.0|org.neo4j.server.webserver.address=$HOSTNAME|g" $NEO4J_HOME/conf/neo4j-server.properties

exec neo4j console
