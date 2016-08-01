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

dist_uri = http://dist.neo4j.org/neo4j-$(1)-$(2)-unix.tar.gz
generic_package := neo4j.tar.gz

all: out/image/.sentinel
.PHONY: complete

out/image/.sentinel: tmp/image/.sentinel tmp/.tests-pass
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

tmp/.tests-pass: tmp/.image-id $(shell find test -name 'test-*')
> mkdir -p $(@D)
> image_id=$$(cat $<)
> for test in $(filter test/test-%,$^); do
>   echo "Running $${test}"
>   "$${test}" $${image_id}
> done
> touch $@

tmp/.image-id: tmp/local-context/.sentinel
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image --build-arg="NEO4J_URI=file:///tmp/$(generic_package)" $(<D)
> echo -n $$image >$@

tmp/local-context/.sentinel: tmp/image/.sentinel tmp/$(generic_package)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp tmp/$(generic_package) $(@D)/local-package
> touch $@

tmp/image/.sentinel: src/Dockerfile src/docker-entrypoint.sh tmp/$(generic_package) \
                     $(env_NEO4J_VERSION)
> mkdir -p $(@D)
> cp src/docker-entrypoint.sh $(@D)/
> sha=$$(shasum --algorithm=256 tmp/$(generic_package) | cut --delimiter=' ' --fields=1)
> <src/Dockerfile sed \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_PUBLICATION_URI%%|$(call dist_uri,enterprise,$(NEO4J_VERSION))|" \
    >$(@D)/Dockerfile
> mkdir -p $(@D)/local-package
> touch $(@D)/local-package/.sentinel
> touch $@

tarball := $(wildcard in/neo4j-*-unix.tar.gz)
tmp/$(generic_package): $(tarball)
> mkdir -p $(@D)
> found="f"
> for tarball in $(tarball); do
>   [[ $${found} == "t" ]] && echo >&2 "ERROR: more than one tarball in in/" && exit 1
>   cp $${tarball} $@
>   found="t"
> done
> if [[ $${found} == "f" ]]; then
>   echo >&2 "ERROR: no tarball in in/" && exit 1
> fi

run: tmp/.image-id
> image_id=$$(cat $<)
> trapping-sigint \
    docker run --publish 7474:7474 --publish 7687:7687 \
    --env=NEO4J_AUTH=neo4j/foo --rm $${image_id}
.PHONY: run

cache: $(env_NEO4J_VERSION)
> rm -rf in
> mkdir -p in
> cd in
> curl --fail --silent --show-error --location --remote-name \
    $(call dist_uri,enterprise,$(NEO4J_VERSION))
.PHONY: cache

clean:
> rm -rf tmp
> rm -rf out
.PHONY: clean
