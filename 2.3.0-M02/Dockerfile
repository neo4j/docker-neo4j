FROM java:openjdk-8-jre

ENV NEO4J_VERSION 2.3.0-M02
ENV NEO4J_DOWNLOAD_SHA256 54047565659e1230c7a196ff696765e042da5679cf287966efad9e36a8f07046
ENV NEO4J_DOWNLOAD_ROOT http://dist.neo4j.org
ENV NEO4J_TARBALL neo4j-community-$NEO4J_VERSION-unix.tar.gz



RUN curl --fail --silent --show-error --location --output $NEO4J_TARBALL $NEO4J_DOWNLOAD_ROOT/$NEO4J_TARBALL \
    && echo "$NEO4J_DOWNLOAD_SHA256 $NEO4J_TARBALL" | sha256sum --check --quiet - \
    && tar --extract --file $NEO4J_TARBALL --directory /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && rm $NEO4J_TARBALL

WORKDIR /var/lib/neo4j

RUN mv data /data \
    && ln --symbolic /data

VOLUME /data

RUN touch /tmp/rrd \
    && sed --in-place "s|org.neo4j.server.webadmin.rrdb.location=.*|org.neo4j.server.webadmin.rrdb.location=/tmp/rrd|g" conf/neo4j-server.properties

COPY neo4j.sh /neo4j.sh

EXPOSE 7474 7473

CMD ["/neo4j.sh"]
