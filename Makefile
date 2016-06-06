SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

.PHONY: all

.PHONY: clean

.SECONDARY:

include tmp/3.1.0-M02.mk tmp/3.1.0-M02-enterprise.mk tmp/3.0.2.mk tmp/3.0.2-enterprise.mk tmp/dev.mk

tmp/%.mk: version.mk.template | tmp
> sed "s/%%VERSION%%/$*/g" $< >$@

tmp:
> mkdir -p tmp
clean::
> rm -rf tmp

tmp/%.runs-okay: tmp/%.image-id trapping-sigint | tmp
> ./trapping-sigint \
    docker run --publish 7474:7474 --publish 7687:7687 \
        --env=NEO4J_AUTH=neo4j/foo --rm $$(cat $<)
> touch $@

tmp/%.image-id: tmp/%.files | tmp
> image=test/$$RANDOM; docker build --tag=$$image $*; echo -n $$image >$@

tmp/%.files: %/Dockerfile %/docker-entrypoint.sh | tmp
> touch $@

start-cluster: tmp/dev.image-id
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

shell: tmp/dev.image-id
> docker run --publish 7474:7474 --rm  --entrypoint sh --interactive --tty \
    $$(cat $<)
.PHONY: shell

tmp/dev.image-id: dev/neo4j-package.tar.gz

%/Dockerfile: Dockerfile.template lookup
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

%/docker-entrypoint.sh: docker-entrypoint.sh
> @mkdir -p $*
> cp $< $@

dev/neo4j-package.tar.gz: $(DEV_PACKAGE)
> cp $< $@

%.digest:
> curl --silent http://dist.neo4j.org/neo4j-community-$*-unix.tar.gz | sha256sum
> curl --silent http://dist.neo4j.org/neo4j-enterprise-$*-unix.tar.gz | sha256sum
