SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif

.RECIPEPREFIX = >

all: 2.2.5/Dockerfile 2.2.5/neo4j.sh
.PHONY: all

2.2.5/Dockerfile: Dockerfile.template Makefile
> sed 's/%%NEO4J_VERSION%%/2.2.5/g; s/%%NEO4J_SHA%%/7fadc119f465a3d6adceb610401363fb158a5ed25081f9893d4f56ac4989a998/g' $< >$@

2.2.5/neo4j.sh: neo4j.sh Makefile
> cp $< $@
