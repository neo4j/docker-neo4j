# Container with Neo4j Server
# Repository http://github.com/neo4j-contrib/docker-neo4j

FROM java:openjdk-8-jdk
MAINTAINER Michael Hunger, <michael.hunger@neotechnology.com>

# Install latest Neo4j Community Stable Version from http://debian.neo4j.org
RUN apt-get install -y curl
RUN curl http://dist.neo4j.org/neo4j-community-2.2.3-unix.tar.gz -o - | tar xzf - -C /var/lib && ln -s /var/lib/neo4j-* /var/lib/neo4j

## add launcher and set execute property
# enable shell server on all network interfaces
# change data directory to /data/graph.db for external linking

VOLUME ["/data"]

ADD neo4j.sh /

RUN chmod +x /neo4j.sh && \
    sed -i -e "s|#*remote_shell_enabled=.*|remote_shell_enabled=true|g" /var/lib/neo4j/conf/neo4j.properties && \
	echo "dbms.pagecache.memory=4G" >> /var/lib/neo4j/conf/neo4j.properties && \
	echo "keep_logical_logs=100M size" >> /var/lib/neo4j/conf/neo4j.properties && \
    echo "remote_shell_host=0.0.0.0" >> /var/lib/neo4j/conf/neo4j.properties && \
    sed -i -e "s|org.neo4j.server.webadmin.rrdb.location=.*|org.neo4j.server.webadmin.rrdb.location=/tmp/rrd|g" /var/lib/neo4j/conf/neo4j-server.properties && \
    sed -i -e "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=docker|g" /var/lib/neo4j/conf/neo4j-wrapper.conf && \
    touch /tmp/rrd

# HTTP
EXPOSE 7474
# HTTPS
EXPOSE 7473

# Shell
# EXPOSE 1337
# RMI
# EXPOSE 1099

WORKDIR /var/lib/neo4j

## Run start script
CMD ["/neo4j.sh"]
