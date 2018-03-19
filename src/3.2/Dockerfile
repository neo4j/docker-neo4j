FROM openjdk:8-jre-alpine

RUN apk add --no-cache --quiet \
    bash \
    curl \
    tini \
    su-exec

ENV NEO4J_SHA256=%%NEO4J_SHA%% \
    NEO4J_TARBALL=%%NEO4J_TARBALL%%
ARG NEO4J_URI=%%NEO4J_DIST_SITE%%/%%NEO4J_TARBALL%%

COPY ./local-package/* /tmp/

RUN curl --fail --silent --show-error --location --remote-name ${NEO4J_URI} \
    && echo "${NEO4J_SHA256}  ${NEO4J_TARBALL}" | sha256sum -csw - \
    && tar --extract --file ${NEO4J_TARBALL} --directory /var/lib \
    && mv /var/lib/neo4j-* /var/lib/neo4j \
    && rm ${NEO4J_TARBALL} \
    && mv /var/lib/neo4j/data /data \
    && chown -R root:root /data \
    && chown -R root:root /var/lib/neo4j \
    && ln -s /data /var/lib/neo4j/data

ENV PATH /var/lib/neo4j/bin:$PATH

WORKDIR /var/lib/neo4j

VOLUME /data

COPY docker-entrypoint.sh /docker-entrypoint.sh

EXPOSE 7474 7473 7687

ENTRYPOINT ["/sbin/tini", "-g", "--", "/docker-entrypoint.sh"]
CMD ["neo4j"]
