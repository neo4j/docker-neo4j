include make-common.mk

NEO4J_BASE_IMAGE?="openjdk:11-jdk-slim"
TAG ?= neo4j

package-arm-experimental: TAG:=neo4j/neo4j-arm64-experimental
package-arm-experimental: tag-arm
> mkdir -p out
> docker save neo4j/neo4j-arm64-experimental:$(NEO4JVERSION) > out/neo4j-community-$(NEO4JVERSION)-arm64-docker-loadable.tar
> docker save neo4j/neo4j-arm64-experimental:$(NEO4JVERSION)-enterprise > out/neo4j-enterprise-$(NEO4JVERSION)-arm64-docker-loadable.tar
> docker save neo4j/neo4j-arm64-experimental:$(NEO4JVERSION)-alpine > out/neo4j-community-$(NEO4JVERSION)-arm64-alpine-docker-loadable.tar
> docker save neo4j/neo4j-arm64-experimental:$(NEO4JVERSION)-alpine-enterprise > out/neo4j-enterprise-$(NEO4JVERSION)-arm64-alpine-docker-loadable.tar
.PHONY: package-arm-experimental

tag-arm: build
> docker tag $$(cat tmp/.image-id-community) $(TAG):$(NEO4JVERSION)
> docker tag $$(cat tmp/.image-id-enterprise) $(TAG):$(NEO4JVERSION)-enterprise
> docker tag $$(cat tmp/.image-id-alpine-community) $(TAG):$(NEO4JVERSION)-alpine
> docker tag $$(cat tmp/.image-id-alpine-enterprise) $(TAG):$(NEO4JVERSION)-alpine-enterprise
.PHONY: tag-arm



