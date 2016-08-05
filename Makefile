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
env_NEO4J_VERSION := $(shell record-env NEO4J_VERSION)

tarball = neo4j-$(1)-$(2)-unix.tar.gz
dist_site := http://dist.neo4j.org
series := $(shell echo "$(NEO4J_VERSION)" | sed --regexp-extended 's/^([0-9]+\.[0-9]+)\..*/\1/')

all: out/enterprise/.sentinel out/community/.sentinel
.PHONY: all

out/%/.sentinel: tmp/image-%/.sentinel tmp/.tests-pass-%
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

tmp/.tests-pass-%: tmp/.image-id-% $(shell find test -name 'test-*')
> mkdir -p $(@D)
> image_id=$$(cat $<)
> for test in $(filter test/test-%,$^); do
>   echo "Running $${test}"
>   "$${test}" $${image_id}
> done
> touch $@

tmp/.image-id-%: tmp/local-context-%/.sentinel $(env_NEO4J_VERSION)
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,$*,$(NEO4J_VERSION))" \
    $(<D)
> echo -n $$image >$@

tmp/local-context-%/.sentinel: tmp/image-%/.sentinel in/$(call tarball,%,$(NEO4J_VERSION))
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> touch $@

tmp/image-%/.sentinel: src/$(series)/Dockerfile src/$(series)/docker-entrypoint.sh \
                       $(env_NEO4J_VERSION) in/$(call tarball,%,$(NEO4J_VERSION))
> mkdir -p $(@D)
> cp $(filter %/docker-entrypoint.sh,$^) $(@D)/docker-entrypoint.sh
> sha=$$(shasum --algorithm=256 $(filter %.tar.gz,$^) | cut --delimiter=' ' --fields=1)
> <$(filter %/Dockerfile,$^) sed \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(call tarball,$*,$(NEO4J_VERSION))|" \
    -e "s|%%NEO4J_DIST_SITE%%|$(dist_site)|" \
    >$(@D)/Dockerfile
> mkdir -p $(@D)/local-package
> touch $(@D)/local-package/.sentinel
> touch $@

run = trapping-sigint \
    docker run --publish 7474:7474 --publish 7687:7687 \
    --env=NEO4J_AUTH=neo4j/foo --rm $$(cat $1)
run-enterprise: tmp/.image-id-enterprise
> $(call run,$<)
run-community: tmp/.image-id-community
> $(call run,$<)
.PHONY: run-enterprise run-community

fetch_tarball = curl --fail --silent --show-error --location --remote-name \
    $(dist_site)/$(call tarball,$(1),$(NEO4J_VERSION))

cache:
> mkdir -p in
> cd in
> $(call fetch_tarball,community)
> $(call fetch_tarball,enterprise)
.PHONY: cache

clean:
> rm -rf tmp
> rm -rf out
.PHONY: clean
