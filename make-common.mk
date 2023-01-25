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

ifndef NEO4JVERSION
  $(error NEO4JVERSION is not set)
endif

tarball = neo4j-$(1)-$(2)-unix.tar.gz
dist_site := https://dist.neo4j.org
major := $(shell echo "$(NEO4JVERSION)" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\1/')
minor := $(shell echo "$(NEO4JVERSION)" | sed -E 's/^([0-9]+)\.([0-9]+)\..*/\2/')
ifeq ($(shell test $(major) -ge "5"; echo $$?), 0)
	series := $(major)
else
	series := $(major).$(minor)
endif

out/%/.sentinel: tmp/image-%/.sentinel
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@


## testing images ##

test: test-enterprise test-community
.PHONY: test

test-enterprise: build-enterprise
> mvn test -Dimage=$$(cat tmp/.image-id-enterprise) -Dadminimage=$$(cat tmp/.image-id-neo4j-admin-enterprise) -Dedition=enterprise -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-enterprise

test-community: build-community
> mvn test -Dimage=$$(cat tmp/.image-id-community) -Dadminimage=$$(cat tmp/.image-id-neo4j-admin-community) -Dedition=community -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-community

## building the image ##

build: build-community build-enterprise build-alpine
.PHONY: build

build-alpine: build-community-alpine build-enterprise-alpine
.PHONY: build-alpine

build-community-alpine: tmp/.image-id-alpine-community  tmp/devenv-alpine-community.env
.PHONY: build-community-alpine

build-enterprise-alpine: tmp/.image-id-alpine-enterprise tmp/devenv-alpine-enterprise.env
.PHONY: build-enterprise-alpine

build-community: tmp/.image-id-community tmp/.image-id-neo4j-admin-community tmp/devenv-community.env
.PHONY: build-community

build-enterprise: tmp/.image-id-enterprise tmp/.image-id-neo4j-admin-enterprise tmp/devenv-enterprise.env
.PHONY: build-enterprise

tmp/devenv-%.env:  tmp/.image-id-% tmp/.image-id-neo4j-admin-%
> echo "NEO4JVERSION=$(NEO4JVERSION)" > ${@}
> echo "NEO4J_IMAGE=$$(cat tmp/.image-id-${*})" >> ${@}
> echo "NEO4JADMIN_IMAGE=$$(cat tmp/.image-id-neo4j-admin-${*})" >> ${@}
> echo "NEO4J_EDITION=${*}" >> ${@}

tmp/devenv-alpine-%.env:  tmp/.image-id-alpine-% 
> echo "NEO4JVERSION=$(NEO4JVERSION)" > ${@}
> echo "NEO4J_IMAGE=$$(cat tmp/.image-id-alpine-${*})" >> ${@}
> echo "NEO4J_EDITION=${*}" >> ${@}

# copy the releaseable version of the image to the output folder.
out/%/.sentinel: tmp/image-%/.sentinel
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

# create image from local build context
tmp/.image-id-%: tmp/local-context-%/.sentinel
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///startup/$(call tarball,$*,$(NEO4JVERSION))" \
    $(<D)
> echo -n $$image >$@

tmp/.image-id-alpine-%: tmp/local-context-alpine%/.sentinel
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///startup/$(call tarball,$*,$(NEO4JVERSION))" \
    $(<D)
> echo -n $$image >$@

tmp/.image-id-neo4j-admin-%: tmp/local-context-neo4j-admin-%/.sentinel
> mkdir -p $(@D)
> image=test/admin-$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///startup/$(call tarball,$*,$(NEO4JVERSION))" \
    $(<D)
> echo -n $$image >$@

## local build context ##

# tmp/local-context-{community,enterprise} is a local folder containing the
# Dockerfile/entrypoint/Neo4j/etc required to build a complete image locally.

tmp/local-context-%/.sentinel: tmp/image-%/.sentinel in/$(call tarball,%,$(NEO4JVERSION))
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> touch $@

tmp/local-context-alpine%/.sentinel: tmp/image-alpine%/.sentinel in/$(call tarball,%,$(NEO4JVERSION))
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> touch $@

tmp/local-context-neo4j-admin-%/.sentinel: tmp/image-neo4j-admin-%/.sentinel in/$(call tarball,%,$(NEO4JVERSION))
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> touch $@

## create Dockerfiles ##

# tmp/image-{community,enterprise} contains the Dockerfile, docker-entrypoint.sh and plugins.json
# with all the variables (eg tini) filled in, but *NO Neo4j tar*. This is what gets released to dockerhub.
# You can successfully do `docker build tmp/image-{community,enterprise}` so long as the Neo4j is a released version.
tmp/image-%/.sentinel: docker-image-src/$(series)/Dockerfile docker-image-src/$(series)/docker-entrypoint.sh \
                       in/$(call tarball,%,$(NEO4JVERSION))
> mkdir -p $(@D)/local-package
> cp docker-image-src/common/* $(@D)/local-package
> cp $(filter %.sh,$^) $(@D)/local-package
> NEO4JPLUGINS_OVERRIDE=docker-image-src/$(series)/neo4jlabs-plugins.json
> if [ -e $${NEO4JPLUGINS_OVERRIDE} ]; then cp $${NEO4JPLUGINS_OVERRIDE} $(@D)/local-package; fi
> sha=$$(shasum --algorithm=256 $(filter %.tar.gz,$^) | cut -d' ' -f1)
> <$(filter %/Dockerfile,$^) sed \
    -e "s|%%NEO4J_BASE_IMAGE%%|${NEO4J_BASE_IMAGE}|" \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(call tarball,$*,$(NEO4JVERSION))|" \
    -e "s|%%NEO4J_EDITION%%|$*|" \
    -e "s|%%NEO4J_DIST_SITE%%|$(dist_site)|" \
    >$(@D)/Dockerfile
> touch $(@D)/local-package/.sentinel
> touch $@

tmp/image-alpine%/.sentinel: docker-image-src/$(series)/alpine/Dockerfile docker-image-src/$(series)/alpine/docker-entrypoint.sh \
                       in/$(call tarball,%,$(NEO4JVERSION))
> mkdir -p $(@D)/local-package
> cp docker-image-src/common/* $(@D)/local-package
> cp $(filter %.sh,$^) $(@D)/local-package
> NEO4JPLUGINS_OVERRIDE=docker-image-src/$(series)/neo4jlabs-plugins.json
> if [ -e $${NEO4JPLUGINS_OVERRIDE} ]; then cp $${NEO4JPLUGINS_OVERRIDE} $(@D)/local-package; fi
> sha=$$(shasum --algorithm=256 $(filter %.tar.gz,$^) | cut -d' ' -f1)
> <$(filter %/Dockerfile,$^) sed \
    -e "s|%%NEO4J_BASE_IMAGE%%|${NEO4J_BASE_IMAGE}|" \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(call tarball,$*,$(NEO4JVERSION))|" \
    -e "s|%%NEO4J_EDITION%%|$*|" \
    -e "s|%%NEO4J_DIST_SITE%%|$(dist_site)|" \
    >$(@D)/Dockerfile
> touch $(@D)/local-package/.sentinel
> touch $@

tmp/image-neo4j-admin-%/.sentinel: docker-image-src/$(series)/neo4j-admin/Dockerfile \
                                    docker-image-src/$(series)/neo4j-admin/docker-entrypoint.sh \
                                    in/$(call tarball,%,$(NEO4JVERSION))
> mkdir -p $(@D)
> cp $(filter %/docker-entrypoint.sh,$^) $(@D)/docker-entrypoint.sh
> sha=$$(shasum --algorithm=256 $(filter %.tar.gz,$^) | cut -d' ' -f1)
> <$(filter %/Dockerfile,$^) sed \
    -e "s|%%NEO4J_BASE_IMAGE%%|${NEO4J_BASE_IMAGE}|" \
    -e "s|%%NEO4J_SHA%%|$${sha}|" \
    -e "s|%%NEO4J_TARBALL%%|$(call tarball,$*,$(NEO4JVERSION))|" \
    -e "s|%%NEO4J_EDITION%%|$*|" \
    -e "s|%%NEO4J_DIST_SITE%%|$(dist_site)|" \
    >$(@D)/Dockerfile
> mkdir -p $(@D)/local-package
> touch $(@D)/local-package/.sentinel
> touch $@

fetch_tarball = curl --fail --silent --show-error --location --remote-name \
    $(dist_site)/$(call tarball,$(1),$(NEO4JVERSION))

cache: in/neo4j-%-$(NEO4JVERSION)-unix.tar.gz
.PHONY: cache

in/neo4j-community-$(NEO4JVERSION)-unix.tar.gz:
> mkdir -p in
> cd in
> $(call fetch_tarball,community)

in/neo4j-enterprise-$(NEO4JVERSION)-unix.tar.gz:
> mkdir -p in
> cd in
> $(call fetch_tarball,enterprise)

clean:
> rm -rf tmp
> rm -rf out
.PHONY: clean
