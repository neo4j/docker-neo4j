SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SECONDEXPANSION:

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

all: out/enterprise/.sentinel
.PHONY: all

out/enterprise/.sentinel: tmp/image-enterprise/.sentinel tmp/.tests-pass-enterprise
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

tmp/.tests-pass-enterprise: tmp/.image-id-enterprise $(shell find test -name 'test-*')
> mkdir -p $(@D)
> image_id=$$(cat $<)
> for test in $(filter test/test-%,$^); do
>   echo "Running $${test}"
>   "$${test}" $${image_id}
> done
> touch $@

tmp/.image-id-enterprise: tmp/local-context-enterprise/.sentinel $(env_NEO4J_VERSION)
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,enterprise,$(NEO4J_VERSION))" \
    $(<D)
> echo -n $$image >$@

tmp/local-context-enterprise/.sentinel: tmp/image-enterprise/.sentinel \
                                        in/$(call tarball,enterprise,$(NEO4J_VERSION))
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> touch $@

tmp/image-enterprise/.sentinel: src/Dockerfile src/docker-entrypoint.sh $(env_NEO4J_VERSION) \
                                in/$(call tarball,enterprise,$(NEO4J_VERSION))
> mkdir -p $(@D)
> cp src/docker-entrypoint.sh $(@D)/
> sha=$$(shasum --algorithm=256 $(filter %.tar.gz,$^) | cut --delimiter=' ' --fields=1)
> <src/Dockerfile sed \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(call tarball,enterprise,$(NEO4J_VERSION))|" \
    -e "s|%%NEO4J_DIST_SITE%%|$(dist_site)|" \
    >$(@D)/Dockerfile
> mkdir -p $(@D)/local-package
> touch $(@D)/local-package/.sentinel
> touch $@

run-enterprise: tmp/.image-id-enterprise
> image_id=$$(cat $<)
> trapping-sigint \
    docker run --publish 7474:7474 --publish 7687:7687 \
    --env=NEO4J_AUTH=neo4j/foo --rm $${image_id}
.PHONY: run-enterprise

fetch_tarball = curl --fail --silent --show-error --location --remote-name \
    $(dist_site)/$(call tarball,$(1),$(NEO4J_VERSION))

cache:
> mkdir -p in
> cd in
> $(call fetch_tarball,enterprise)
.PHONY: cache

clean:
> rm -rf tmp
> rm -rf out
.PHONY: clean
