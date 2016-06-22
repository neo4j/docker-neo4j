SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SECONDEXPANSION:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

NEO4J_EDITION ?= community
NEO4J_VERSION ?= 3.0.1

dist_uri := http://dist.neo4j.org/neo4j-$(NEO4J_EDITION)-$(NEO4J_VERSION)-unix.tar.gz
NEO4J_URI ?= $(dist_uri)
env_NEO4J_URI := $(shell record-env NEO4J_URI $(NEO4J_URI))

all: out/image/.sentinel
.PHONY: complete

run: tmp/.image-id
> image_id=$$(cat $<)
> trapping-sigint \
    docker run --publish 7474:7474 --publish 7687:7687 \
        --env=NEO4J_AUTH=neo4j/foo --rm $${image_id}
.PHONY: run

out/image/.sentinel: tmp/image-with-sha/.sentinel tmp/.tests-pass $(env_NEO4J_URI)
> mkdir -p $(@D)
> cp $(<D)/docker-entrypoint.sh $(@D)/
> uri=$$(prepare-injection uri $(dist_uri))
> command=$$(prepare-injection command $(dist_uri))
> <$(<D)/Dockerfile \
    sed -e "s|%%NEO4J_URI%%|$${uri}|" -e "s|%%INJECT_TARBALL%%|$${command}|" \
    >$(@D)/Dockerfile
> touch $@

tmp/.tests-pass: tmp/.image-id $(shell find test -name 'test-*')
> image_id=$$(cat $<)
> for test in $(filter test/test-%,$^); do
>   echo "Running $${test}"
>   "$${test}" $${image_id}
> done
> touch $@

tmp/.image-id: tmp/image-with-uri/.sentinel
> image=test/$$RANDOM; docker build --tag=$$image $(<D); echo -n $$image >$@

tmp/image-with-uri/.sentinel: tmp/image-with-sha/.sentinel $(env_NEO4J_URI)
> mkdir -p $(@D)
> cp $(<D)/docker-entrypoint.sh $(@D)/
> uri=$$(prepare-injection uri $(NEO4J_URI))
> command=$$(prepare-injection command $(NEO4J_URI))
> <$(<D)/Dockerfile \
    sed -e "s|%%NEO4J_URI%%|$${uri}|" -e "s|%%INJECT_TARBALL%%|$${command}|" \
    >$(@D)/Dockerfile
> prepare-injection copy $(NEO4J_URI) $(@D)
> touch $@

tmp/image-with-sha/.sentinel: src/Dockerfile src/docker-entrypoint.sh $(env_NEO4J_URI)
> mkdir -p $(@D)
> cp src/docker-entrypoint.sh $(@D)/
> sha=$$(prepare-injection sha $(NEO4J_URI))
> <src/Dockerfile sed "s|%%NEO4J_SHA%%|$${sha}|" >$(@D)/Dockerfile
> touch $@

clean:
> rm -rf tmp
> rm -rf out
.PHONY: clean
