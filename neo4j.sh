#!/bin/bash
set -e

NEO4J_HOME=/var/lib/neo4j
cd $NEO4J_HOME

if [ -n "$NEO4J_OPEN_FILES" ]; then
	ulimit -n $NEO4J_OPEN_FILES > /dev/null
else
	ulimit -n 40000 > /dev/null
fi

if [ -d /conf ]; then
	cp -R /conf/ $NEO4J_HOME/conf/
fi

sed -i "s|#org.neo4j.server.webserver.address=0.0.0.0|org.neo4j.server.webserver.address=$HOSTNAME|g" $NEO4J_HOME/conf/neo4j-server.properties
#echo "wrapper.java.additional=-Djava.rmi.server.hostname=$HOSTNAME" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
#echo "wrapper.java.additional=-Dcom.sun.management.jmxremote.port=1099" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
#echo "wrapper.java.additional=-Dcom.sun.management.jmxremote.rmi.port=1099" >> $NEO4J_HOME/conf/neo4j-wrapper.conf

if [ ! -z ${NEO4J_NO_AUTH+x} ]; then
	sed -i -e "s|dbms.security.auth_enabled=.*|dbms.security.auth_enabled=false|g" $NEO4J_HOME/conf/neo4j-server.properties
fi

exec neo4j console
