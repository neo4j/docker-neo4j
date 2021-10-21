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
series := $(shell echo "$(NEO4JVERSION)" | sed -E 's/^([0-9]+\.[0-9]+)\..*/\1/')

out/%/.sentinel: tmp/image-%/.sentinel
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@

## building the image ##

tmp/.image-id-neo4j-admin-%: tmp/local-context-neo4j-admin-%/.sentinel
> mkdir -p $(@D)
> image=test/admin-$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,$*,$(NEO4JVERSION))" \
    $(<D)
> echo -n $$image >$@

# tmp/local-context-{community,enterprise} is a local folder containing the
# Dockerfile/entrypoint/Neo4j/etc required to build a complete image locally.
tmp/local-context-%/.sentinel: tmp/image-%/.sentinel in/$(call tarball,%,$(NEO4JVERSION)) tmp/neo4jlabs-plugins.json
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> cp $(filter %.json,$^) $(@D)/local-package
> touch $@

tmp/local-context-neo4j-admin-%/.sentinel: tmp/image-neo4j-admin-%/.sentinel in/$(call tarball,%,$(NEO4JVERSION))
> rm -rf $(@D)
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> cp $(filter %.tar.gz,$^) $(@D)/local-package
> touch $@

# tmp/image-{community,enterprise} contains the Dockerfile, docker-entrypoint.sh and plugins.json
# with all the variables (eg tini) filled in, but *NO Neo4j tar*. This is what gets released to dockerhub.
# You can successfully do `docker build tmp/image-{community,enterprise}` so long as the Neo4j is a released version.
tmp/image-%/.sentinel: docker-image-src/$(series)/Dockerfile docker-image-src/$(series)/docker-entrypoint.sh \
                       in/$(call tarball,%,$(NEO4JVERSION)) tmp/neo4jlabs-plugins.json
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
> cp $(filter %.json,$^) $(@D)/local-package
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

tmp/neo4jlabs-plugins.json: ./neo4jlabs-plugins.json
> mkdir -p $(@D)
> cp $< $@

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
