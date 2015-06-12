#!/bin/bash

NEO4J_HOME=/var/lib/neo4j

sed -i "s|#org.neo4j.server.webserver.address=0.0.0.0|org.neo4j.server.webserver.address=$HOSTNAME|g" $NEO4J_HOME/conf/neo4j-server.properties
echo "wrapper.java.additional=-Djava.rmi.server.hostname=$HOSTNAME" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
#echo "wrapper.java.additional=-Dcom.sun.management.jmxremote.port=1099" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
#echo "wrapper.java.additional=-Dcom.sun.management.jmxremote.rmi.port=1099" >> $NEO4J_HOME/conf/neo4j-wrapper.conf

if [ $NEO4J_NO_AUTH ]; then
   sed -i "s|dbms.security.auth_enabled=.+|dbms.security.auth_enabled=false|g" $NEO4J_HOME/conf/neo4j-server.properties
fi

limit=`ulimit -n`
if [ "$limit" -lt 65536 ]; then
    ulimit -n 65536;
fi

rm -rf $NEO4J_HOME/data
mkdir -p /data/log /data/dbms /data/graph.db
ln -s /data $NEO4J_HOME/data

# work around for memory mapping issue of boot2docker's virtualbox on Mac
rm -f /data/graph.db/rrd && ln -s /tmp/rrd /data/graph.db/rrd

# override what's needed
cp /conf/* $NEO4J_HOME/conf/

$NEO4J_HOME/bin/neo4j console
