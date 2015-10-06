SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

all:
.PHONY: all

dev/runs-okay: dev/image-id
> @mkdir -p dev
> trap "touch $@; exit 0" SIGINT; docker run --publish 7474:7474 --volume=/tmp/neo4j-data:/data --env=NEO4J_AUTH=neo4j/foo --rm $$(cat $<)

shell: dev/image-id
> docker run --publish 7474:7474 --rm  --entrypoint sh --interactive --tty $$(cat dev/image-id)
.PHONY: shell

dev/image-id: dev/Dockerfile dev/neo4j.sh dev/neo4j-package.tar.gz
> @mkdir -p dev
> set -e; image=test/$$RANDOM; docker build --tag=$$image dev; echo -n $$image >$@

clean::
> rm -rf dev
.PHONY: clean

%/Dockerfile: Dockerfile.template Makefile lookup
> @mkdir -p $*
> export VERSION=$*; sed "s|%%VERSION%%|$$(./lookup version)|; s|%%DOWNLOAD_SHA%%|$$(./lookup sha)|; s|%%DOWNLOAD_ROOT%%|$$(./lookup root)|; s|%%INJECT_TARBALL%%|$$(./lookup inject)|" $< >$@

%/neo4j.sh: neo4j.sh Makefile
> @mkdir -p $*
> cp $< $@

dev/neo4j-package.tar.gz: $(DEV_PACKAGE)
> @mkdir -p dev
> cp $< $@

all: 2.3.0-M03
2.3.0-M03: 2.3.0-M03/Dockerfile 2.3.0-M03/neo4j.sh
.PHONY: 2.3.0-M03
clean::
> rm -rf 2.3.0-M03

all: 2.3.0-M02
2.3.0-M02: 2.3.0-M02/Dockerfile 2.3.0-M02/neo4j.sh
.PHONY: 2.3.0-M02
clean::
> rm -rf 2.3.0-M02

all: 2.2.5
2.2.5: 2.2.5/Dockerfile 2.2.5/neo4j.sh
.PHONY: 2.2.5
clean::
> rm -rf 2.2.5

all: 2.2.4
2.2.4: 2.2.4/Dockerfile 2.2.4/neo4j.sh
.PHONY: 2.2.4
clean::
> rm -rf 2.2.4

all: 2.2.3
2.2.3: 2.2.3/Dockerfile 2.2.3/neo4j.sh
.PHONY: 2.2.3
clean::
> rm -rf 2.2.3

all: 2.2.2
2.2.2: 2.2.2/Dockerfile 2.2.2/neo4j.sh
.PHONY: 2.2.2
clean::
> rm -rf 2.2.2

all: dev/runs-okay
