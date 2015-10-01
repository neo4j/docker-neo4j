SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif

.RECIPEPREFIX = >

all: 2.2.5 dev/builds-okay
.PHONY: all

2.2.5: 2.2.5/Dockerfile 2.2.5/neo4j.sh
.PHONY: 2.2.5

clean:
> rm -rf dev 2.2.5
.PHONY: clean

%/Dockerfile: Dockerfile.template Makefile
> @mkdir -p $*
> export VERSION=$*; sed "s|%%VERSION%%|$$(./lookup version)|; s|%%DOWNLOAD_SHA%%|$$(./lookup sha)|; s|%%DOWNLOAD_ROOT%%|$$(./lookup root)|; s|%%INJECT_TARBALL%%|$$(./lookup inject)|" $< >$@

%/neo4j.sh: neo4j.sh Makefile
> @mkdir -p $*
> cp $< $@

dev/builds-okay: dev/Dockerfile dev/neo4j.sh dev/neo4j-package.tar.gz
> @mkdir -p dev
> docker build dev
> touch $@

dev/neo4j-package.tar.gz: $(DEV_ROOT)/neo4j-community-*-unix.tar.gz
> @mkdir -p dev
> cp $< $@
