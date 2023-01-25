FROM alpine:3.17 as dist
# Tar expantsion of Neo4j distribution. Separate stage makes sure that the tarball layer is not included in the final image, saves apx. 220MB
ENV NEO4J_SHA256=%%NEO4J_SHA%% \
    NEO4J_TARBALL=%%NEO4J_TARBALL%% \
    NEO4J_HOME="/var/lib/neo4j" 

COPY ./local-package/${NEO4J_TARBALL} /tmp
RUN  tar --extract --file /tmp/${NEO4J_TARBALL} --directory /var/lib \
    && mv /var/lib/neo4j-* ${NEO4J_HOME} \
    && rm /tmp/${NEO4J_TARBALL}

FROM alpine:3.17 as runt
# Runtime for Neo4j
ENV NEO4J_SHA256=%%NEO4J_SHA%% \
    NEO4J_TARBALL=%%NEO4J_TARBALL%% \
    NEO4J_HOME="/var/lib/neo4j" \
    NEO4J_EDITION=%%NEO4J_EDITION%% \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
    PATH="${JAVA_HOME}/bin:${PATH}"
ARG NEO4J_URI=%%NEO4J_DIST_SITE%%/%%NEO4J_TARBALL%%

RUN addgroup -g 7474 -S neo4j && adduser -u 7474 -S -D -h "${NEO4J_HOME}" -G neo4j neo4j

COPY --from=dist --chown=neo4j:neo4j ${NEO4J_HOME} ${NEO4J_HOME}

RUN apk update \
    && apk add --no-cache openjdk17-jre tini su-exec curl bash findutils   \
    && ls -l "${NEO4J_HOME}" \
    && mv "${NEO4J_HOME}"/data/ /data \
    && mv "${NEO4J_HOME}"/logs/ /logs \
    && chown -R neo4j:neo4j /data \
    && chmod -R 777 /data \
    && chown -R neo4j:neo4j /logs \
    && chmod -R 777 /logs \
    && ln -s /data "${NEO4J_HOME}"/data \
    && ln -s /logs "${NEO4J_HOME}"/logs

COPY ./local-package/*.sh  ./local-package/*.json ./local-package/*.jq /startup/

ENV PATH=${NEO4J_HOME}/bin:$PATH

WORKDIR "${NEO4J_HOME}"

VOLUME /data /logs

EXPOSE 7474 7473 7687

ENTRYPOINT ["tini", "-g", "--", "/startup/docker-entrypoint.sh"]
CMD ["neo4j"]