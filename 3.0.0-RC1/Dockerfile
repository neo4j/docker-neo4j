FROM java:openjdk-8-jre

ENV NEO4J_VERSION 3.0.0-RC1
ENV NEO4J_EDITION community
ENV NEO4J_DOWNLOAD_SHA256 19dbf31f2ccd7c060c713ffaf6827305a37fb689bcf39d0562d37bb6780e635c
ENV NEO4J_DOWNLOAD_ROOT http://dist.neo4j.org
ENV NEO4J_TARBALL neo4j-$NEO4J_EDITION-$NEO4J_VERSION-unix.tar.gz
ENV NEO4J_URI $NEO4J_DOWNLOAD_ROOT/$NEO4J_TARBALL



RUN curl --fail --silent --show-error --location --output neo4j.tar.gz $NEO4J_URI \
    && echo "$NEO4J_DOWNLOAD_SHA256 neo4j.tar.gz" | sha256sum --check --quiet - \
    && tar --extract --file neo4j.tar.gz --directory /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && rm neo4j.tar.gz

WORKDIR /var/lib/neo4j

RUN mv data /data \
    && ln --symbolic /data

VOLUME /data

COPY docker-entrypoint.sh /docker-entrypoint.sh

EXPOSE 7474 7473 7687

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["neo4j"]
