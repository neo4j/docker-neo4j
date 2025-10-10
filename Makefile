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

build: build-bullseye build-ubi9
.PHONY: build

build-bullseye: build-bullseye-community build-bullseye-enterprise
.PHONY: build-bullseye
build-bullseye-community: build/bullseye/coredb/community/.sentinel
.PHONY: build-bullseye-community
build-bullseye-enterprise: build/bullseye/coredb/enterprise/.sentinel
.PHONY: build-bullseye-enterprise

build/bullseye/coredb/%/.sentinel::
> ./build-docker-image.sh $(NEO4JVERSION) "${*}" "bullseye"
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

## tagging

tag: tag-bullseye tag-ubi9
.PHONY: tag

tag-bullseye: tag-bullseye-community tag-bullseye-enterprise
.PHONY: tag-bullseye
tag-ubi9: tag-ubi9-community tag-ubi9-enterprise
.PHONY: tag-ubi9

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
package: package-bullseye package-ubi9
.PHONY: package

package-bullseye: package-bullseye-community package-bullseye-enterprise package-bullseye-release-artifacts
.PHONY: package-bullseye

package-ubi9: package-ubi9-community package-ubi9-enterprise package-ubi9-release-artifacts
.PHONY: package-ubi9

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


# keep "debian" targets as an alias for bullseye until we update the build pipelines
build-debian: build-bullseye-community build-bullseye-enterprise
.PHONY: build-debian
build-debian-community: build-bullseye-community
> mkdir -p build/debian/
> cp --recursive build/bullseye/* build/debian/
.PHONY: build-debian-community
build-debian-enterprise: build-bullseye-enterprise
> mkdir -p build/debian/
> cp --recursive build/bullseye/* build/debian/
.PHONY: build-debian-enterprise
tag-debian: tag-debian-community tag-debian-enterprise
.PHONY: tag-debian
package-debian: package-debian-community package-debian-enterprise package-debian-release-artifacts
.PHONY: package-debian