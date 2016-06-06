FROM java:openjdk-8-jre

ENV NEO4J_VERSION 3.0.2
ENV NEO4J_EDITION enterprise
ENV NEO4J_DOWNLOAD_SHA256 7e3cb1cb29c51ac267e57ec3d12c425bd31a4c92a2ecba70bb78360670b4b972
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
