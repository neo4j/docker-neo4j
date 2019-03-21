FROM openjdk:8-jre-alpine

RUN addgroup -S neo4j && adduser -S -H -h /var/lib/neo4j -G neo4j neo4j

ENV NEO4J_SHA256=%%NEO4J_SHA%% \
    NEO4J_TARBALL=%%NEO4J_TARBALL%% \
    NEO4J_EDITION=%%NEO4J_EDITION%% \
    NEO4J_HOME="/var/lib/neo4j"
ARG NEO4J_URI=%%NEO4J_DIST_SITE%%/%%NEO4J_TARBALL%%

COPY ./local-package/* /tmp/

RUN apk add --no-cache --quiet \
    bash \
    curl \
    tini \
    su-exec \
    && curl --fail --silent --show-error --location --remote-name ${NEO4J_URI} \
    && echo "${NEO4J_SHA256}  ${NEO4J_TARBALL}" | sha256sum -csw - \
    && tar --extract --file ${NEO4J_TARBALL} --directory /var/lib \
    && mv /var/lib/neo4j-* "${NEO4J_HOME}" \
    && rm ${NEO4J_TARBALL} \
    && mv "${NEO4J_HOME}"/data /data \
    && chown -R neo4j:neo4j /data \
    && mv "${NEO4J_HOME}"/logs /logs \
    && chown -R neo4j:neo4j /logs \
    && chown -R neo4j:neo4j "${NEO4J_HOME}" \
    && ln -s /data "${NEO4J_HOME}"/data \
    && ln -s /logs "${NEO4J_HOME}"/logs \
    && for _dir in "/data /logs $NEO4J_HOME";do find ${_dir} -type d -exec chmod 750 {} ";";done \
    && for _dir in "/data /logs";do find ${_dir} -type f -exec chmod 640 {} ";";done \
    && apk del curl

ENV PATH "${NEO4J_HOME}"/bin:$PATH

WORKDIR "${NEO4J_HOME}"

VOLUME /data /logs

COPY docker-entrypoint.sh /docker-entrypoint.sh

EXPOSE 7474 7473 7687

ENTRYPOINT ["/sbin/tini", "-g", "--", "/docker-entrypoint.sh"]
CMD ["neo4j"]
