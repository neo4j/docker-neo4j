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

# Use make test TESTS='<pattern>' to run specific tests
# e.g. `make test TESTS='TestCausalCluster'` or `make test TESTS='*Cluster*'`
# the value of variable is passed to the maven test property. For more info see https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html
# by default this is empty which means all tests will be run
TESTS?=""

clean:
> rm -rf ./build/
> rm -rf ./out
.PHONY: clean

test-%-enterprise: build-%-enterprise
> mvn test -Dimage=$$(cat build/${*}/coredb/.image-id-enterprise) -Dadminimage=$$(cat build/${*}/neo4j-admin/.image-id-enterprise) -Dedition=enterprise -Dbaseos=${*} -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-%-enterprise

test-%-community: build-%-community
> mvn test -Dimage=$$(cat build/${*}/coredb/.image-id-community) -Dadminimage=$$(cat build/${*}/neo4j-admin/.image-id-community) -Dedition=community -Dbaseos=${*} -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-%-community

## building

build: build-debian build-ubi9
.PHONY: build

build-debian: build-debian-community build-debian-enterprise
.PHONY: build-debian
build-debian-community: build/debian/coredb/community/.sentinel
.PHONY: build-debian-community
build-debian-enterprise: build/debian/coredb/enterprise/.sentinel
.PHONY: build-debian-enterprise

build/debian/coredb/%/.sentinel::
> ./build-docker-image.sh $(NEO4JVERSION) "${*}" "debian"
> touch $@

build-ubi9: build-ubi9-community build-ubi9-enterprise
.PHONY: build-ubi9
build-ubi9-community: build/ubi9/coredb/community/.sentinel
.PHONY: build-ubi9-community
build-ubi9-enterprise: build/ubi9/coredb/enterprise/.sentinel
.PHONY: build-ubi9-enterprise

build/ubi9/coredb/%/.sentinel::
> ./build-docker-image.sh $(NEO4JVERSION) "${*}" "ubi9"
> touch $@

build-ubi8: build-ubi8-community build-ubi8-enterprise
.PHONY: build-ubi8
build-ubi8-community: build/ubi8/coredb/community/.sentinel
.PHONY: build-ubi8-community
build-ubi8-enterprise: build/ubi8/coredb/enterprise/.sentinel
.PHONY: build-ubi8-enterprise

build/ubi8/coredb/%/.sentinel::
> ./build-docker-image.sh $(NEO4JVERSION) "${*}" "ubi8"
> touch $@

## tagging

tag: tag-debian tag-ubi9
.PHONY: tag

tag-debian: tag-debian-community tag-debian-enterprise
.PHONY: tag-debian
tag-ubi9: tag-ubi9-community tag-ubi9-enterprise
.PHONY: tag-ubi9
tag-ubi8: tag-ubi8-community tag-ubi8-enterprise
.PHONY: tag-ubi8

tag-%-community: build-%-community
> docker tag $$(cat ./build/${*}/coredb/.image-id-community) neo4j:$(NEO4JVERSION)-${*}
> docker tag $$(cat ./build/${*}/neo4j-admin/.image-id-community) neo4j/neo4j-admin:$(NEO4JVERSION)-${*}
.PHONY: tag-%-community

tag-%-enterprise: build-%-enterprise
> docker tag $$(cat ./build/${*}/coredb/.image-id-enterprise) neo4j:$(NEO4JVERSION)-enterprise-${*}
> docker tag $$(cat ./build/${*}/neo4j-admin/.image-id-enterprise) neo4j/neo4j-admin:$(NEO4JVERSION)-enterprise-${*}
.PHONY: tag-%-enterprise

## packaging and release

# create release images and loadable images
package: package-debian package-ubi9
.PHONY: package

package-debian: package-debian-community package-debian-enterprise package-debian-release-artifacts
.PHONY: package-debian

package-ubi9: package-ubi9-community package-ubi9-enterprise package-ubi9-release-artifacts
.PHONY: package-ubi9

package-ubi8: package-ubi8-community package-ubi8-enterprise package-ubi8-release-artifacts
.PHONY: package-ubi8

package-%-community:  tag-%-community
> mkdir -p out
> docker save neo4j:$(NEO4JVERSION)-${*} > out/neo4j-community-$(NEO4JVERSION)-${*}-docker-loadable.tar
> docker save neo4j/neo4j-admin:$(NEO4JVERSION)-${*} > out/neo4j-admin-community-$(NEO4JVERSION)-${*}-docker-loadable.tar

package-%-enterprise:  tag-%-enterprise
> mkdir -p out
> docker save neo4j:$(NEO4JVERSION)-enterprise-${*} > out/neo4j-enterprise-$(NEO4JVERSION)-${*}-docker-loadable.tar
> docker save neo4j/neo4j-admin:$(NEO4JVERSION)-enterprise-${*} > out/neo4j-admin-enterprise-$(NEO4JVERSION)-${*}-docker-loadable.tar

package-%-release-artifacts: build-%-community build-%-enterprise
> mkdir -p out
> cp --recursive --force build/${*} out/
> find out/${*} -name "neo4j-*.tar.gz" -delete
> find out/${*} -name ".image-id-*" -delete
> find out/${*} -name ".sentinel" -delete