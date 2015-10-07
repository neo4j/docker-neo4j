SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

all: dev/runs-okay
.PHONY: all

include 2.2.2.mk 2.2.3.mk 2.2.4.mk 2.2.5.mk 2.3.0-M02.mk 2.3.0-M03.mk

%.mk: version.mk.template Makefile
> sed "s/%%VERSION%%/$*/g" $< >$@

dev/runs-okay: dev/image-id trapping-sigint
> @mkdir -p dev
> ./trapping-sigint \
    docker run --publish 7474:7474 --volume=/tmp/neo4j-data:/data \
        --env=NEO4J_AUTH=neo4j/foo --rm $$(cat $<)
> touch $@

shell: dev/image-id
> docker run --publish 7474:7474 --rm  --entrypoint sh --interactive --tty \
    $$(cat dev/image-id)
.PHONY: shell

dev/image-id: dev/Dockerfile dev/neo4j.sh dev/neo4j-package.tar.gz
> @mkdir -p dev
> image=test/$$RANDOM; docker build --tag=$$image dev; echo -n $$image >$@

clean::
> rm -rf dev
.PHONY: clean

%/Dockerfile: Dockerfile.template Makefile lookup
> @mkdir -p $*
> export VERSION=$*; <$< \
      sed "s|%%VERSION%%|$$(./lookup version)|" \
    | sed "s|%%DOWNLOAD_SHA%%|$$(./lookup sha)|" \
    | sed "s|%%DOWNLOAD_ROOT%%|$$(./lookup root)|" \
    | sed "s|%%INJECT_TARBALL%%|$$(./lookup inject)|" \
    >$@

%/neo4j.sh: neo4j.sh Makefile
> @mkdir -p $*
> cp $< $@

dev/neo4j-package.tar.gz: $(DEV_PACKAGE)
> @mkdir -p dev
> cp $< $@
