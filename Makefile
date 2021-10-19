include make-common.mk

NEO4J_BASE_IMAGE?="openjdk:11-jdk-slim"

# Use make test TESTS='<pattern>' to run specific tests
# e.g. `make test TESTS='TestCausalCluster'` or `make test TESTS='*Cluster*'`
# the value of variable is passed to the maven test property. For more info see https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html
# by default this is empty which means all tests will be run
TESTS?=""

all: test
.PHONY: all

test: test-enterprise test-community
.PHONY: test

test-enterprise: build-enterprise
> mvn test -Dimage=$$(cat $<) -Dedition=enterprise -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-enterprise

test-community: build-community
> mvn test -Dimage=$$(cat $<) -Dedition=community -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-community

# just build the images, don't test or package
build: build-community build-enterprise
.PHONY: build

build-community: tmp/.image-id-community tmp/.image-id-neo4j-admin-community tmp/devenv-community.env
.PHONY: build-community

build-enterprise: tmp/.image-id-enterprise tmp/.image-id-neo4j-admin-enterprise tmp/devenv-enterprise.env
.PHONY: build-enterprise

tmp/devenv-%.env:  tmp/.image-id-% tmp/.image-id-neo4j-admin-%
> echo "NEO4JVERSION=$(NEO4JVERSION)" > ${@}
> echo "NEO4J_IMAGE=$$(cat tmp/.image-id-${*})" >> ${@}
> echo "NEO4JADMIN_IMAGE=$$(cat tmp/.image-id-neo4j-admin-${*})" >> ${@}
> echo "NEO4J_EDITION=${*}" >> ${@}

# create release images and loadable images
package: package-community package-enterprise
.PHONY: package

package-community: tmp/.image-id-community tmp/.image-id-neo4j-admin-community out/community/.sentinel
> mkdir -p out
> docker tag $$(cat $<) neo4j:$(NEO4JVERSION)
> docker save neo4j:$(NEO4JVERSION) > out/neo4j-community-$(NEO4JVERSION)-docker-loadable.tar

package-enterprise: tmp/.image-id-enterprise tmp/.image-id-neo4j-admin-enterprise out/enterprise/.sentinel
> mkdir -p out
> docker tag $$(cat $<) neo4j:$(NEO4JVERSION)-enterprise
> docker save neo4j:$(NEO4JVERSION)-enterprise > out/neo4j-enterprise-$(NEO4JVERSION)-docker-loadable.tar

# create image from local build context
tmp/.image-id-%: tmp/local-context-%/.sentinel
> mkdir -p $(@D)
> image=test/$$RANDOM
> docker build --tag=$$image \
    --build-arg="NEO4J_URI=file:///tmp/$(call tarball,$*,$(NEO4JVERSION))" \
    $(<D)
> echo -n $$image >$@

# copy the releaseable version of the image to the output folder.
out/%/.sentinel: tmp/image-%/.sentinel
> mkdir -p $(@D)
> cp -r $(<D)/* $(@D)
> touch $@