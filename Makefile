SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SECONDEXPANSION:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

tests_in = $(shell find $1 -name 'test-*')

all: uriless complete
.PHONY: all

complete: out/image-with-uri/.sentinel
.PHONY: complete

uriless: out/image-with-sha/.sentinel
.PHONY: uriless

run: tmp/.image-id
> trapping-sigint \
    docker run --publish 7474:7474 --publish 7687:7687 \
        --env=NEO4J_AUTH=neo4j/foo --rm $$(cat $<)
.PHONY: run

out/image-with-%/.sentinel: tmp/image-with-%/.sentinel tmp/.tests-pass | out
> rm -rf $(@D)
> cp --recursive $(<D) $(@D)
> @touch $@

tmp/.tests-pass: tmp/.image-id $(call tests_in,test)
> @for test in $(filter test/test-%,$^); do echo "Running $${test}"; "$${test}" $$(cat $<); done
> @touch $@

tmp/.image-id: tmp/image-with-uri/.sentinel | tmp
> image=test/$$RANDOM; docker build --tag=$$image $(<D); echo -n $$image >$@

tmp/image-with-uri/.sentinel: \
    $$(@D)/Dockerfile $$(@D)/docker-entrypoint.sh $$(@D)/neo4j-package.tar.gz
> @touch $@

tmp/image-with-uri/Dockerfile: tmp/image-with-sha/$$(@F) | $$(@D)
> <$< sed "s|%%NEO4J_URI%%|$$(prepare-injection uri $$(neo4j-uri))|" \
    | sed "s|%%INJECT_TARBALL%%|$$(prepare-injection command $$(neo4j-uri))|" \
    >$@

tmp/image-with-uri/docker-entrypoint.sh: tmp/image-with-sha/$$(@F) | $$(@D)
> cp $< $@

tmp/image-with-uri/neo4j-package.tar.gz: | $$(@D)
> prepare-injection copy $$(neo4j-uri) $(@D)

tmp/image-with-uri:
> @mkdir -p $@

tmp/image-with-sha/.sentinel: $$(@D)/Dockerfile $$(@D)/docker-entrypoint.sh
> @touch $@

tmp/image-with-sha/Dockerfile: src/$$(@F) | $$(@D)
> <$< sed "s|%%NEO4J_SHA%%|$$(prepare-injection sha $$(neo4j-uri))|" >$@

tmp/image-with-sha/docker-entrypoint.sh: src/$$(@F) | $$(@D)
> cp $< $@

tmp/image-with-sha:
> @mkdir -p $@

tmp:
> @mkdir -p tmp
clean::
> rm -rf tmp

out:
> @mkdir -p out
clean::
> rm -rf out

.PHONY: clean
