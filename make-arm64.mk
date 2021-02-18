include make-common.mk

NEO4J_BASE_IMAGE?="arm64v8/openjdk:11-jdk-slim"

package-arm: tag-arm
> mkdir -p out
> docker save neo4j/neo4j-arm64-experimental:$(NEO4JVERSION)-arm64 > out/neo4j-community-$(NEO4JVERSION)-arm64-docker-loadable.tar
> docker save neo4j/neo4j-arm64-experimental:$(NEO4JVERSION)-arm64-enterprise > out/neo4j-enterprise-$(NEO4JVERSION)-arm64-docker-loadable.tar
.PHONY: package-arm

tag-arm: build-arm
> docker tag $$(cat tmp/.image-id-community-arm) neo4j/neo4j-arm64-experimental:$(NEO4JVERSION)-arm64
> docker tag $$(cat tmp/.image-id-enterprise-arm) neo4j/neo4j-arm64-experimental:$(NEO4JVERSION)-arm64-enterprise
.PHONY: tag-arm

# create release images for arm architecture (not for production use!)
build-arm: tmp/.image-id-community-arm tmp/.image-id-enterprise-arm
.PHONY: build-arm

tmp/.image-id-%-arm: tmp/local-context-%/.sentinel in/$(call tarball,%,$(NEO4JVERSION))
> image=test/$$RANDOM-arm
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,$*,$(NEO4JVERSION))" \
    --build-arg="TINI_URI=https://github.com/krallin/tini/releases/download/v0.18.0/tini-arm64" \
    --build-arg="TINI_SHA256=7c5463f55393985ee22357d976758aaaecd08defb3c5294d353732018169b019" \
    $(<D)
> echo -n $$image >$@


