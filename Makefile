SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SECONDEXPANSION:
.SECONDARY:

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

ifndef NEO4J_VERSION
  $(error NEO4J_VERSION is not set)
endif

tarball = neo4j-$(1)-$(2)-unix.tar.gz
dist_site := https://dist.neo4j.org
series := $(shell echo "$(NEO4J_VERSION)" | sed -E 's/^([0-9]+\.[0-9]+)\..*/\1/')

all: test
.PHONY: all

test: tmp/.image-id-community tmp/.image-id-enterprise
> mvn test -Dimage=$$(cat tmp/.image-id-community) -Dedition=community -Dversion=$(NEO4J_VERSION)
> mvn test -Dimage=$$(cat tmp/.image-id-enterprise) -Dedition=enterprise -Dversion=$(NEO4J_VERSION)
.PHONY: test

# just build the images, don't test or package
build: tmp/.image-id-community tmp/.image-id-enterprise
.PHONY: build

# create release images and loadable images
package: package-community package-enterprise
.PHONY: package

package-community: tmp/.image-id-community out/community/.sentinel
> mkdir -p out
> docker tag $$(cat $<) neo4j:$(NEO4J_VERSION)
> docker save neo4j:$(NEO4J_VERSION) > out/neo4j-community-$(NEO4J_VERSION)-docker-loadable.tar

package-enterprise: tmp/.image-id-enterprise out/enterprise/.sentinel
> mkdir -p out
> docker tag $$(cat $<) neo4j:$(NEO4J_VERSION)-enterprise
> docker save neo4j:$(NEO4J_VERSION)-enterprise > out/neo4j-enterprise-$(NEO4J_VERSION)-docker-loadable.tar

out/%/.sentinel: tmp/image-%/.sentinel
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

# building the image

tmp/.image-id-%: tmp/local-context-%/.sentinel
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,$*,$(NEO4J_VERSION))" \
    $(<D)
> echo -n $$image >$@

tmp/neo4jlabs-plugins.json: ./neo4jlabs-plugins.json
> mkdir -p $(@D)
> cp $< $@

tmp/local-context-%/.sentinel: tmp/image-%/.sentinel in/$(call tarball,%,$(NEO4J_VERSION)) tmp/neo4jlabs-plugins.json
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> cp $(filter %.json,$^) $(@D)/local-package
> touch $@

tmp/image-%/.sentinel: docker-image-src/$(series)/Dockerfile docker-image-src/$(series)/docker-entrypoint.sh \
                       in/$(call tarball,%,$(NEO4J_VERSION)) tmp/neo4jlabs-plugins.json
> mkdir -p $(@D)
> cp $(filter %/docker-entrypoint.sh,$^) $(@D)/docker-entrypoint.sh
> sha=$$(shasum --algorithm=256 $(filter %.tar.gz,$^) | cut -d' ' -f1)
> <$(filter %/Dockerfile,$^) sed \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(call tarball,$*,$(NEO4J_VERSION))|" \
    -e "s|%%NEO4J_EDITION%%|$*|" \
    -e "s|%%NEO4J_DIST_SITE%%|$(dist_site)|" \
    >$(@D)/Dockerfile
> mkdir -p $(@D)/local-package
> cp $(filter %.json,$^) $(@D)/local-package
> touch $(@D)/local-package/.sentinel
> touch $@

fetch_tarball = curl --fail --silent --show-error --location --remote-name \
    $(dist_site)/$(call tarball,$(1),$(NEO4J_VERSION))

cache: in/neo4j-%-$(NEO4J_VERSION)-unix.tar.gz
.PHONY: cache

in/neo4j-community-$(NEO4J_VERSION)-unix.tar.gz:
> mkdir -p in
> cd in
> $(call fetch_tarball,community)

in/neo4j-enterprise-$(NEO4J_VERSION)-unix.tar.gz:
> mkdir -p in
> cd in
> $(call fetch_tarball,enterprise)

clean:
> rm -rf tmp
> rm -rf out
.PHONY: clean
