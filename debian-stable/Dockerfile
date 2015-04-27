# Container with Neo4j Server
# Repository http://github.com/neo4j/docker-neo4j

FROM dockerfile/java:openjdk-7-jre
MAINTAINER Michael Hunger, <michael.hunger@neotechnology.com>

# Install latest Neo4j _Milestone_ from http://debian.neo4j.org

RUN curl http://debian.neo4j.org/neotechnology.gpg.key | apt-key add - && \
    echo 'deb http://debian.neo4j.org/repo stable/' > /etc/apt/sources.list.d/neo4j.list && \
    apt-get update && \
    apt-get install neo4j -y && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

## add launcher and set execute property
# enable shell server on all network interfaces
# change data directory to /data/graph.db for external linking

VOLUME ["/data/graph.db"]

ADD neo4j.sh /

RUN chmod +x /neo4j.sh && \
    sed -i "s|remote_shell_enabled=.+|remote_shell_enabled=true|g" /var/lib/neo4j/conf/neo4j.properties && \
    sed -i "s|data/graph.db|/data/graph.db|g" /var/lib/neo4j/conf/neo4j-server.properties && \
    echo "remote_shell_host=0.0.0.0" >> /var/lib/neo4j/conf/neo4j.properties

# HTTP
EXPOSE 7474
# Shell
EXPOSE 1337
# HTTPS
EXPOSE 7473
# RMI
EXPOSE 1099

WORKDIR /etc/neo4j

## Run start script
CMD ["/neo4j.sh"]
