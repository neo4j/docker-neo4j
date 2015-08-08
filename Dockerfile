# Neo4j Server
# Repository http://github.com/neo4j-contrib/docker-neo4j
FROM java:openjdk-8-jre

MAINTAINER Michael Hunger <michael.hunger@neotechnology.com>

ENV PATH $PATH:/var/lib/neo4j/bin

ENV NEO4J_VERSION 2.2.3
ENV NEO4J_DOWNLOAD_SHA256 b170e54a8af540a30b915a85bdc6e3670c0790466fb90cf7fd9555097165c67c

RUN apt-get update \
	&& apt-get install -y curl \
	&& curl -fSL -o neo4j-community.tar.gz http://dist.neo4j.org/neo4j-community-$NEO4J_VERSION-unix.tar.gz \
	&& apt-get purge -y --auto-remove curl && rm -rf /var/lib/apt/lists/* \
	&& echo "$NEO4J_DOWNLOAD_SHA256 neo4j-community.tar.gz" | sha256sum -c - \
	&& tar xzf neo4j-community.tar.gz -C /var/lib \
	&& mv /var/lib/neo4j-* /var/lib/neo4j \
	&& ln -s /var/lib/neo4j/data /data \
	&& rm neo4j-community.tar.gz

RUN echo "dbms.pagecache.memory=4G" >> /var/lib/neo4j/conf/neo4j.properties \
	&& echo "keep_logical_logs=100M size" >> /var/lib/neo4j/conf/neo4j.properties \
	&& sed -i -e "s|Dneo4j.ext.udc.source=.*|Dneo4j.ext.udc.source=docker|g" /var/lib/neo4j/conf/neo4j-wrapper.conf

VOLUME /data

COPY neo4j.sh /neo4j.sh

EXPOSE 7474 7473

CMD ["/neo4j.sh"]
