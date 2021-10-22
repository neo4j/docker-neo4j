include make-common.mk

NEO4J_BASE_IMAGE?="arm64v8/openjdk:11-jdk-slim"
TAG ?= neo4j

package-arm-experimental: TAG:=neo4j/neo4j-arm64-experimental
package-arm-experimental: tag-arm
> mkdir -p out
> docker save $(TAG):$(NEO4JVERSION) > out/neo4j-community-$(NEO4JVERSION)-arm64-docker-loadable.tar
> docker save $(TAG):$(NEO4JVERSION)-enterprise > out/neo4j-enterprise-$(NEO4JVERSION)-arm64-docker-loadable.tar
.PHONY: package-arm-experimental

package-arm: TAG:=neo4j
package-arm: tag-arm out/community/.sentinel out/enterprise/.sentinel
> mkdir -p out
> docker save $(TAG):$(NEO4JVERSION) > out/neo4j-community-$(NEO4JVERSION)-arm64-docker-loadable.tar
> docker save $(TAG):$(NEO4JVERSION)-enterprise > out/neo4j-enterprise-$(NEO4JVERSION)-arm64-docker-loadable.tar
.PHONY: package-arm

tag-arm: build-arm
> docker tag $$(cat tmp/.image-id-community-arm) $(TAG):$(NEO4JVERSION)
> docker tag $$(cat tmp/.image-id-enterprise-arm) $(TAG):$(NEO4JVERSION)-enterprise
.PHONY: tag-arm

test-arm: build-arm
> mvn test -Dimage=$$(cat tmp/.image-id-community-arm) -Dadminimage=$$(cat tmp/.image-id-neo4j-admin-community) -Dedition=community -Dversion=$(NEO4JVERSION) -Dtest=com.neo4j.docker.neo4jserver.TestBasic
> mvn test -Dimage=$$(cat tmp/.image-id-enterprise-arm) -Dadminimage=$$(cat tmp/.image-id-neo4j-admin-enterprise) -Dedition=enterprise -Dversion=$(NEO4JVERSION) -Dtest=com.neo4j.docker.neo4jserver.TestBasic
.PHONY: test-arm

# neo4j-admin builds don't need tini so we don't need to have an architecture specific target for them
build-arm: tmp/.image-id-community-arm tmp/.image-id-enterprise-arm tmp/.image-id-neo4j-admin-community tmp/.image-id-neo4j-admin-enterprise
.PHONY: build-arm

tmp/.image-id-%-arm: tmp/local-context-%/.sentinel in/$(call tarball,%,$(NEO4JVERSION))
> image=test/$$RANDOM-arm
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,$*,$(NEO4JVERSION))" \
    --build-arg="TINI_URI=https://github.com/krallin/tini/releases/download/v0.18.0/tini-arm64" \
    --build-arg="TINI_SHA256=7c5463f55393985ee22357d976758aaaecd08defb3c5294d353732018169b019" \
    $(<D)
> echo -n $$image >$@


