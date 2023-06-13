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
> rm -rf ./build/debian/
> rm -rf ./build/rhel8/
> rm -rf ./out
.PHONY: clean

all: tag
.PHONY: all
.DEFAULT:

## building

build: build-debian build-rhel8
.PHONY: build

build-debian: build-debian-community build-debian-enterprise
.PHONY: build-debian

build-debian-%:
> ./build/build-docker-image.sh $(NEO4JVERSION) "${*}" "debian"
.PHONY: build-debian-%

build-rhel8: build-rhel8-community build-rhel8-enterprise
.PHONY: build-debian

build-rhel8-%:
> ./build/build-docker-image.sh $(NEO4JVERSION) "${*}" "rhel8"
.PHONY: build-rhel8-%

## tagging

tag: tag-debian tag-rhel8
.PHONY: tag

tag-debian: tag-debian-community tag-debian-enterprise
.PHONY: tag-debian

tag-rhel8: tag-rhel8-community tag-rhel8-enterprise
.PHONY: tag-rhel8

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
package: package-debian package-rhel8
.PHONY: package

package-debian: package-debian-community package-debian-enterprise package-debian-release-artifacts
.PHONY: package-debian

package-rhel8: package-rhel8-community package-rhel8-enterprise package-rhel8-release-artifacts
.PHONY: package-rhel8

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