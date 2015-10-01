SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif

.RECIPEPREFIX = >

all: 2.2.5 dev/builds-okay
2.2.5: 2.2.5/Dockerfile 2.2.5/neo4j.sh
.PHONY: all 2.2.5 dev

2.2.5/Dockerfile: Dockerfile.template Makefile
> sed 's/%%VERSION%%/2.2.5/; s/%%DOWNLOAD_SHA%%/7fadc119f465a3d6adceb610401363fb158a5ed25081f9893d4f56ac4989a998/; s|%%DOWNLOAD_ROOT%%|http://dist.neo4j.org|; s/%%INJECT_TARBALL%%//' $< >$@

2.2.5/neo4j.sh: neo4j.sh Makefile
> cp $< $@

dev/builds-okay: dev/Dockerfile dev/neo4j.sh dev/neo4j-package.tar.gz
> docker build dev
> touch $@

dev/Dockerfile: Dockerfile.template dev/neo4j-package.tar.gz Makefile
> @mkdir -p dev
> sed 's/%%VERSION%%/2.3-SNAPSHOT/; s/%%DOWNLOAD_SHA%%/'$$(sha256sum dev/neo4j-package.tar.gz | cut -d' ' -f1)'/; s|%%DOWNLOAD_ROOT%%|file:///docker-test/|; s|%%INJECT_TARBALL%%|COPY ./neo4j-package.tar.gz /docker-test/$$NEO4J_TARBALL|' $< >$@

dev/neo4j.sh: neo4j.sh Makefile
> cp $< $@

dev/neo4j-package.tar.gz: $(DEV_ROOT)/neo4j-community-*-unix.tar.gz
> cp $< $@

clean:
> rm -rf dev
.PHONY: clean
