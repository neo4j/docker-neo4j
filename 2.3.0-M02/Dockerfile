# Neo4j Server
# Repository http://github.com/neo4j-contrib/docker-neo4j
FROM java:openjdk-8-jre

MAINTAINER Michael Hunger <michael.hunger@neotechnology.com>

ENV PATH $PATH:/var/lib/neo4j/bin

ENV NEO4J_VERSION 2.3.0-M02
ENV NEO4J_DOWNLOAD_SHA256 54047565659e1230c7a196ff696765e042da5679cf287966efad9e36a8f07046
ENV NEO4J_DOWNLOAD_ROOT http://dist.neo4j.org
ENV NEO4J_TARBALL neo4j-community-$NEO4J_VERSION-unix.tar.gz



RUN apt-get update \
    && apt-get install -y curl \
    && curl -fSL -o $NEO4J_TARBALL $NEO4J_DOWNLOAD_ROOT/$NEO4J_TARBALL \
    && apt-get purge -y --auto-remove curl && rm -rf /var/lib/apt/lists/* \
    && sha256sum $NEO4J_TARBALL \
    && echo "$NEO4J_DOWNLOAD_SHA256 $NEO4J_TARBALL" | sha256sum -c - \
    && tar xzf $NEO4J_TARBALL -C /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && ln -s /var/lib/neo4j/data /data \
    && touch /tmp/rrd \
    && rm $NEO4J_TARBALL

RUN sed -i -e "s|.*dbms.pagecache.memory=.*|dbms.pagecache.memory=512M|g" /var/lib/neo4j/conf/neo4j.properties \
    && sed -i -e "s|.*keep_logical_logs=.*|keep_logical_logs=100M size|g" /var/lib/neo4j/conf/neo4j.properties \
    && sed -i -e "s|#*remote_shell_enabled=.*|remote_shell_enabled=true|g" /var/lib/neo4j/conf/neo4j.properties \
    && sed -i -e "s|org.neo4j.server.webadmin.rrdb.location=.*|org.neo4j.server.webadmin.rrdb.location=/tmp/rrd|g" /var/lib/neo4j/conf/neo4j-server.properties \
    && sed -i -e "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=docker|g" /var/lib/neo4j/conf/neo4j-wrapper.conf \
    && ulimit -n 40000 > /dev/null

VOLUME /data

COPY neo4j.sh /neo4j.sh

EXPOSE 7474 7473

CMD ["/neo4j.sh"]
