FROM java:openjdk-8-jre

ENV NEO4J_VERSION 2.2.2
ENV NEO4J_DOWNLOAD_SHA256 18d6d88c61d9077340adf774359bd1d8f458f68ec51bcf96deb3f00894db1ded
ENV NEO4J_DOWNLOAD_ROOT http://dist.neo4j.org
ENV NEO4J_TARBALL neo4j-community-$NEO4J_VERSION-unix.tar.gz



RUN curl --fail --silent --show-error --location --output $NEO4J_TARBALL $NEO4J_DOWNLOAD_ROOT/$NEO4J_TARBALL \
    && echo "$NEO4J_DOWNLOAD_SHA256 $NEO4J_TARBALL" | sha256sum --check --quiet - \
    && tar --extract --file $NEO4J_TARBALL --directory /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && rm $NEO4J_TARBALL

WORKDIR /var/lib/neo4j

RUN adduser --system --quiet neo4j \
    && chown --recursive neo4j: .
USER neo4j

VOLUME /var/lib/neo4j/data

RUN touch /tmp/rrd \
    && sed --in-place "s|org.neo4j.server.webadmin.rrdb.location=.*|org.neo4j.server.webadmin.rrdb.location=/tmp/rrd|g" conf/neo4j-server.properties

RUN ulimit -n 40000 >/dev/null

COPY neo4j.sh /neo4j.sh

EXPOSE 7474 7473

CMD ["/neo4j.sh"]
