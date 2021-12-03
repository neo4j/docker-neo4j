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
> mvn test -Dimage=$$(cat tmp/.image-id-enterprise) -Dadminimage=$$(cat tmp/.image-id-neo4j-admin-enterprise) -Dedition=enterprise -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-enterprise

test-community: build-community
> mvn test -Dimage=$$(cat tmp/.image-id-community) -Dadminimage=$$(cat tmp/.image-id-neo4j-admin-community) -Dedition=community -Dversion=$(NEO4JVERSION) -Dtest=$(TESTS)
.PHONY: test-community



# create release images and loadable images
package: package-community package-enterprise
.PHONY: package

package-community: tmp/.image-id-community tmp/.image-id-neo4j-admin-community out/community/.sentinel out/neo4j-admin-community/.sentinel
> mkdir -p out
> docker tag $$(cat $<) neo4j:$(NEO4JVERSION)
> docker save neo4j:$(NEO4JVERSION) > out/neo4j-community-$(NEO4JVERSION)-docker-loadable.tar

package-enterprise: tmp/.image-id-enterprise tmp/.image-id-neo4j-admin-enterprise out/enterprise/.sentinel out/neo4j-admin-enterprise/.sentinel
> mkdir -p out
> docker tag $$(cat $<) neo4j:$(NEO4JVERSION)-enterprise
> docker save neo4j:$(NEO4JVERSION)-enterprise > out/neo4j-enterprise-$(NEO4JVERSION)-docker-loadable.tar
