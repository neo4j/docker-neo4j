SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

all: dev/runs-okay
.PHONY: all

include 2.2.2.mk 2.2.3.mk 2.2.4.mk 2.2.5.mk 2.3.0-M02.mk 2.3.0-M03.mk \
        2.3.0-M03_enterprise.mk

%.mk: version.mk.template Makefile
> sed "s/%%VERSION%%/$*/g" $< >$@

dev/runs-okay: dev/image-id trapping-sigint
> @mkdir -p dev
> ./trapping-sigint \
    docker run --publish 7474:7474 --volume=/tmp/neo4j-data:/data \
        --env=NEO4J_AUTH=neo4j/foo --rm $$(cat $<)
> touch $@

start-cluster: dev/image-id
> docker run --name=instance1 --detach --publish 7474:7474 --env=NEO4J_AUTH=neo4j/foo\
    --env=NEO4J_DATABASE_MODE=HA --env=NEO4J_SERVER_ID=1 \
    --env=NEO4J_HA_ADDRESS=instance1 \
    --env=NEO4J_INITIAL_HOSTS=instance1:5001,instance2:5001 $$(cat $<)
> docker run --name=instance2 --detach --publish 7475:7474 --env=NEO4J_AUTH=neo4j/foo\
    --link instance1:instance1 \
    --env=NEO4J_DATABASE_MODE=HA --env=NEO4J_SERVER_ID=2 \
    --env=NEO4J_HA_ADDRESS=instance2 \
    --env=NEO4J_INITIAL_HOSTS=instance1:5001,instance2:5001 $$(cat $<)
> docker run --name=instance3 --detach --publish 7476:7474 --env=NEO4J_AUTH=neo4j/foo\
    --link instance1:instance1 --link instance2:instance2 \
    --env=NEO4J_DATABASE_MODE=HA --env=NEO4J_SERVER_ID=3 \
    --env=NEO4J_HA_ADDRESS=instance3 \
    --env=NEO4J_INITIAL_HOSTS=instance1:5001,instance2:5001,instance3:5001 $$(cat $<)
.PHONY: start-cluster

stop-cluster:
> docker rm instance1 instance2 instance3 || true
> docker stop instance1 instance2 instance3
> docker rm instance1 instance2 instance3
.PHONY: stop-cluster

shell: dev/image-id
> docker run --publish 7474:7474 --rm  --entrypoint sh --interactive --tty \
    $$(cat dev/image-id)
.PHONY: shell

dev/image-id: dev/Dockerfile dev/docker-entrypoint.sh dev/neo4j-package.tar.gz
> @mkdir -p dev
> image=test/$$RANDOM; docker build --tag=$$image dev; echo -n $$image >$@

clean::
> rm -rf dev
.PHONY: clean

%/Dockerfile: Dockerfile.template Makefile lookup
> @mkdir -p $*
> export TAG=$*; \
    version=$$(./lookup version); \
    edition=$$(./lookup edition); \
    sha=$$(./lookup sha); \
    root=$$(./lookup root); \
    inject=$$(./lookup inject); \
    <$< sed "s|%%VERSION%%|$${version}|" \
    | sed "s|%%EDITION%%|$${edition}|" \
    | sed "s|%%DOWNLOAD_SHA%%|$${sha}|" \
    | sed "s|%%DOWNLOAD_ROOT%%|$${root}|" \
    | sed "s|%%INJECT_TARBALL%%|$${inject}|" \
    >$@

%/docker-entrypoint.sh: docker-entrypoint.sh Makefile
> @mkdir -p $*
> cp $< $@

dev/neo4j-package.tar.gz: $(DEV_PACKAGE)
> @mkdir -p dev
> cp $< $@
