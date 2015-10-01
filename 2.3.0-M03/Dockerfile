FROM java:openjdk-8-jre

ENV PATH $PATH:/var/lib/neo4j/bin

ENV NEO4J_VERSION 2.3.0-M03
ENV NEO4J_DOWNLOAD_SHA256 cd47ae541a0c5da0d82212c30c1c2fb19ea91ddb1b174346c120e596e04b5dfc
ENV NEO4J_DOWNLOAD_ROOT http://dist.neo4j.org
ENV NEO4J_TARBALL neo4j-community-$NEO4J_VERSION-unix.tar.gz



RUN apt-get update \
    && apt-get install --yes curl \
    && curl --fail --show-error --location --output $NEO4J_TARBALL $NEO4J_DOWNLOAD_ROOT/$NEO4J_TARBALL \
    && apt-get purge --yes --auto-remove curl && rm --recursive --force /var/lib/apt/lists/* \
    && echo "$NEO4J_DOWNLOAD_SHA256 $NEO4J_TARBALL" | sha256sum --check - \
    && tar --extract --file $NEO4J_TARBALL --directory /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && rm $NEO4J_TARBALL

RUN ln --symbolic /var/lib/neo4j/data /data
RUN touch /tmp/rrd

RUN sed --in-place "s|.*dbms.pagecache.memory=.*|dbms.pagecache.memory=512M|g" /var/lib/neo4j/conf/neo4j.properties \
    && sed --in-place "s|.*keep_logical_logs=.*|keep_logical_logs=100M size|g" /var/lib/neo4j/conf/neo4j.properties \
    && sed --in-place "s|#*remote_shell_enabled=.*|remote_shell_enabled=true|g" /var/lib/neo4j/conf/neo4j.properties \
    && sed --in-place "s|org.neo4j.server.webadmin.rrdb.location=.*|org.neo4j.server.webadmin.rrdb.location=/tmp/rrd|g" /var/lib/neo4j/conf/neo4j-server.properties \
    && sed --in-place "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=docker|g" /var/lib/neo4j/conf/neo4j-wrapper.conf \
    && ulimit -n 40000 > /dev/null

VOLUME /data

COPY neo4j.sh /neo4j.sh

EXPOSE 7474 7473

CMD ["/neo4j.sh"]
