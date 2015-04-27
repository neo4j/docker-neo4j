#!/bin/bash

NEO4J_HOME=/var/lib/neo4j

sed -i "s|#org.neo4j.server.webserver.address=0.0.0.0|org.neo4j.server.webserver.address=$HOSTNAME|g" $NEO4J_HOME/conf/neo4j-server.properties
echo "wrapper.java.additional=-Djava.rmi.server.hostname=$HOSTNAME" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
#echo "wrapper.java.additional=-Dcom.sun.management.jmxremote.port=1099" >> $NEO4J_HOME/conf/neo4j-wrapper.conf
#echo "wrapper.java.additional=-Dcom.sun.management.jmxremote.rmi.port=1099" >> $NEO4J_HOME/conf/neo4j-wrapper.conf

# doing this conditionally in case there is already a limit higher than what
# we're setting here. neo4j recommends at least 40000.
# 
# (http://neo4j.com/docs/1.6.2/configuration-linux-notes.html#_setting_the_number_of_open_files)
limit=`ulimit -n`
if [ "$limit" -lt 65536 ]; then
    ulimit -n 65536;
fi

# override what's needed
cp /conf/* $NEO4J_HOME/conf/

$NEO4J_HOME/bin/neo4j console
