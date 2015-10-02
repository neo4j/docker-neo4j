FROM java:openjdk-8-jre

ENV NEO4J_VERSION 2.2.4
ENV NEO4J_DOWNLOAD_SHA256 b3fa5d547e50c3f619e39290266979e72f7222be7644fbb3bad2fc31d074aab9
ENV NEO4J_DOWNLOAD_ROOT http://dist.neo4j.org
ENV NEO4J_TARBALL neo4j-community-$NEO4J_VERSION-unix.tar.gz



RUN curl --fail --silent --show-error --location --output $NEO4J_TARBALL $NEO4J_DOWNLOAD_ROOT/$NEO4J_TARBALL \
    && echo "$NEO4J_DOWNLOAD_SHA256 $NEO4J_TARBALL" | sha256sum --check --quiet - \
    && tar --extract --file $NEO4J_TARBALL --directory /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && rm $NEO4J_TARBALL

VOLUME /var/lib/neo4j/data

WORKDIR /var/lib/neo4j

RUN touch /tmp/rrd
RUN sed --in-place "s|org.neo4j.server.webadmin.rrdb.location=.*|org.neo4j.server.webadmin.rrdb.location=/tmp/rrd|g" conf/neo4j-server.properties

RUN ulimit -n 40000 >/dev/null

COPY neo4j.sh /neo4j.sh

EXPOSE 7474 7473

CMD ["/neo4j.sh"]
