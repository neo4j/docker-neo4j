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

clean: ./Dockerfile
> rm $(<D)/../src/test/resources/testplugin/*
.PHONY: clean

make-plugin: ./Dockerfile ./ExampleNeo4jPlugin.java ./build.gradle.kts ./settings.gradle.kts
> workdir=$(realpath $(<D))
> docker build $${workdir} -t testplugin:latest
> docker run -it --rm \
	-e NEO4JVERSION \
	-v $${workdir}/../src/test/resources/testplugin:/output \
	--user="$$(id -u):$$(id -g)" \
	testplugin:latest
> mv $${workdir}/../src/test/resources/testplugin/myPlugin-$${NEO4JVERSION}.jar $${workdir}/../src/test/resources/testplugin/myPlugin.jar
.PHONY: make-plugin