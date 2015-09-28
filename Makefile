SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif

.RECIPEPREFIX = >

all: 2.2.5/Dockerfile 2.2.5/neo4j.sh
.PHONY: all

2.2.5/Dockerfile: Dockerfile
> cp $^ $@

2.2.5/neo4j.sh: neo4j.sh
> cp $^ $@
