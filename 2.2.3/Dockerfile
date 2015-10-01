FROM java:openjdk-8-jre

ENV PATH $PATH:/var/lib/neo4j/bin

ENV NEO4J_VERSION 2.2.3
ENV NEO4J_DOWNLOAD_SHA256 b170e54a8af540a30b915a85bdc6e3670c0790466fb90cf7fd9555097165c67c
ENV NEO4J_DOWNLOAD_ROOT http://dist.neo4j.org
ENV NEO4J_TARBALL neo4j-community-$NEO4J_VERSION-unix.tar.gz



RUN curl --fail --silent --show-error --location --output $NEO4J_TARBALL $NEO4J_DOWNLOAD_ROOT/$NEO4J_TARBALL \
    && echo "$NEO4J_DOWNLOAD_SHA256 $NEO4J_TARBALL" | sha256sum --check --quiet - \
    && tar --extract --file $NEO4J_TARBALL --directory /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && rm $NEO4J_TARBALL

RUN ln --symbolic /var/lib/neo4j/data /data
RUN touch /tmp/rrd

RUN sed --in-place "s|.*dbms.pagecache.memory=.*|dbms.pagecache.memory=512M|g" /var/lib/neo4j/conf/neo4j.properties
RUN sed --in-place "s|.*keep_logical_logs=.*|keep_logical_logs=100M size|g" /var/lib/neo4j/conf/neo4j.properties
RUN sed --in-place "s|#*remote_shell_enabled=.*|remote_shell_enabled=true|g" /var/lib/neo4j/conf/neo4j.properties
RUN sed --in-place "s|org.neo4j.server.webadmin.rrdb.location=.*|org.neo4j.server.webadmin.rrdb.location=/tmp/rrd|g" /var/lib/neo4j/conf/neo4j-server.properties
RUN sed --in-place "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=docker|g" /var/lib/neo4j/conf/neo4j-wrapper.conf

RUN ulimit -n 40000 >/dev/null

VOLUME /data

COPY neo4j.sh /neo4j.sh

EXPOSE 7474 7473

CMD ["/neo4j.sh"]
