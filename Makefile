SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SECONDEXPANSION:
.SECONDARY:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

ifndef NEO4J_VERSION
  $(error NEO4J_VERSION is not set)
endif

NETWORK_CONTAINER := "network"
COMPOSE_NETWORK := "neo4jcomposetest_lan"

tarball = neo4j-$(1)-$(2)-unix.tar.gz
dist_site := http://dist.neo4j.org
series := $(shell echo "$(NEO4J_VERSION)" | sed -E 's/^([0-9]+\.[0-9]+)\..*/\1/')

all: out/community/.sentinel out/enterprise/.sentinel
.PHONY: all

test: test-community test-enterprise
.PHONY: test

out/community/.sentinel: out/community-base/.sentinel out/community-algos/.sentinel

out/enterprise/.sentinel: out/enterprise-base/.sentinel out/enterprise-algos/.sentinel

out/%-base/.sentinel: tmp/image-%-base/.sentinel tmp/.tests-pass-%-base
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

out/%-algos/.sentinel: tmp/image-%-algos/.sentinel tmp/.tests-pass-%-algos
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

tmp/test-context/.sentinel: test/container/Dockerfile
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

tmp/.image-id-network-container: tmp/test-context/.sentinel
> mkdir -p $(@D)
> image=network-container
> docker rmi $$image || true
> docker build --tag=$$image $(<D)
> echo -n $$image >$@

tmp/.tests-pass-%-base: tmp/.image-id-%-base $(shell find test -name 'test-*') \
	$(shell find test -name '*.yml') $(shell find test -name '*.sh') \
	tmp/.image-id-network-container
> mkdir -p $(@D)
> image_id=$$(cat $<)
> for test in $(filter test/test-%,$^); do
>   echo "Running NETWORK_CONTAINER=$(NETWORK_CONTAINER)-"$*" \
COMPOSE_NETWORK=$(COMPOSE_NETWORK) $${test} $${image_id} ${series} $*"
>   NETWORK_CONTAINER=$(NETWORK_CONTAINER)-"$*" COMPOSE_NETWORK=$(COMPOSE_NETWORK) \
"$${test}" "$${image_id}" "${series}" "$*"
> done
> touch $@

tmp/.tests-pass-%-algos: tmp/.image-id-%-algos $(shell find test -name 'test-algos-*') \
	$(shell find test -name '*.yml') $(shell find test -name '*.sh') \
	tmp/.image-id-network-container
> mkdir -p $(@D)
> image_id=$$(cat $<)
> for test in $(filter test/test-algos-%,$^); do
>   echo "Running NETWORK_CONTAINER=$(NETWORK_CONTAINER)-"$*" \
COMPOSE_NETWORK=$(COMPOSE_NETWORK) $${test} $${image_id} ${series} $*"
>   NETWORK_CONTAINER=$(NETWORK_CONTAINER)-"$*" COMPOSE_NETWORK=$(COMPOSE_NETWORK) \
"$${test}" "$${image_id}" "${series}" "$*"
> done
> touch $@

tmp/.image-id-%-base: tmp/local-context-%-base/.sentinel
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image \
    --no-cache \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,$*,$(NEO4J_VERSION))" \
    $(<D)
> echo -n "$${image}" >$@

tmp/.image-id-%-algos: tmp/local-context-%-algos/.sentinel
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag="$${image}" \
	--no-cache \
	$(<D)
> echo -n "$${image}" >$@

tmp/local-context-%-base/.sentinel: tmp/image-%-base/.sentinel in/$(call tarball,%,$(NEO4J_VERSION))
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> touch $@

tmp/local-context-%-algos/.sentinel: tmp/image-%-algos/.sentinel
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

tmp/image-%-base/.sentinel: src/$(series)/Dockerfile src/$(series)/docker-entrypoint.sh \
                       in/$(call tarball,%,$(NEO4J_VERSION))
> mkdir -p $(@D)
> cp $(filter %/docker-entrypoint.sh,$^) $(@D)/docker-entrypoint.sh
> sha=$$(openssl sha -sha256 $(filter %.tar.gz,$^) | cut -d' ' -f2)
> <$(filter %/Dockerfile,$^) sed \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(call tarball,$*,$(NEO4J_VERSION))|" \
    -e "s|%%NEO4J_EDITION%%|$*|" \
    -e "s|%%NEO4J_DIST_SITE%%|$(dist_site)|" \
    >$(@D)/Dockerfile
> mkdir $(@D)/local-package
> touch $(@D)/local-package/.sentinel
> touch $@

tmp/image-%-algos/.sentinel: tmp/.image-id-%-base src/$(series)-algos/Dockerfile \
      src/$(series)-algos/apoc-version src/$(series)-algos/graph-algos-version
> mkdir -p $(@D)
> cp src/$(series)-algos/* $(@D)/
> <$(filter %/Dockerfile,$^) sed \
    -e "s|%%BASE_IMAGE%%|$$(cat $(<))|" \
    >$(@D)/Dockerfile
> mkdir -p $(@D)/local-package
> touch $(@D)/local-package/.sentinel
> touch $@

run = trapping-sigint \
    docker run --publish 7474:7474 --publish 7687:7687 \
    --env=NEO4J_ACCEPT_LICENSE_AGREEMENT=yes \
    --env=NEO4J_AUTH=neo4j/foo --rm $$(cat $1)
build-enterprise: tmp/.image-id-enterprise
> @echo "Neo4j $(NEO4J_VERSION)-enterprise available as: $$(cat $<)"
build-community: tmp/.image-id-community
> @echo "Neo4j $(NEO4J_VERSION)-community available as: $$(cat $<)"
build-enterprise-algos: tmp/.image-id-enterprise-algos
> @echo "Neo4j $(NEO4J_VERSION)-enterprise-algos available as: $$(cat $<)"
build-community-algos: tmp/.image-id-community-algos
> @echo "Neo4j $(NEO4J_VERSION)-community-algos available as: $$(cat $<)"
run-enterprise: tmp/.image-id-enterprise
> $(call run,$<)
run-community: tmp/.image-id-community
> $(call run,$<)
test-enterprise: tmp/.tests-pass-enterprise-base
test-community: tmp/.tests-pass-community-base
test-enterprise-algos: tmp/.tests-pass-enterprise-algos
test-community-algos: tmp/.tests-pass-community-algos
.PHONY: run-enterprise run-community build-enterprise build-community test-enterprise test-community build-community-algos build-enterprise-algos

fetch_tarball = curl --fail --silent --show-error --location --remote-name \
    $(dist_site)/$(call tarball,$(1),$(NEO4J_VERSION))

cache: in/neo4j-%-$(NEO4J_VERSION)-unix.tar.gz
.PHONY: cache

in/neo4j-community-$(NEO4J_VERSION)-unix.tar.gz:
> mkdir -p in
> cd in
> $(call fetch_tarball,community)

in/neo4j-enterprise-$(NEO4J_VERSION)-unix.tar.gz:
> mkdir -p in
> cd in
> $(call fetch_tarball,enterprise)

clean:
> rm -rf in
> rm -rf tmp
> rm -rf out
.PHONY: clean
