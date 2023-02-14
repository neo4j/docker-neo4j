# Supported platforms

Development is tested on Ubuntu and OSX. It will probably work on other Linuxes.

# Prerequisites

## OSX only

1. install GNU Make (>=4.0)
1. install the Docker Toolbox. See: https://docs.docker.com/install/

## Linux

1. install the Docker Toolbox. See https://docs.docker.com/install/

# Building the Image

The build will create two images (one for Enterprise and one for Community) for a single version of Neo4j. 

The make script will automatically download the source files needed to build the images. 
You just need to specify the **full** Neo4j version including major, minor and patch numbers. For example:

```bash
NEO4JVERSION=3.5.11 make clean build
```

When the make script is complete, the image name will be written to file in `tmp/.image-id-community` and `tmp/.image-id-enterprise`:

```bash
$ cat tmp/.image-id-community
test/19564

$ cat tmp/.image-id-enterprise
test/13909
```

## Building ARM64 based images

From Neo4j 4.3.0 onwards, the Neo4j image should be buildable on any architecture using the same build commands as [Building the Image](#building-the-image).

For earlier versions of Neo4j, you may need to set the variable `NEO4J_BASE_IMAGE` to your architecture specific version of `openjdk:11-jdk-slim` (or `openjdk:8-jdk-slim` for versions before 4.0.0).

Like with `amd64` images, you must still specify the **full** Neo4j version including major, minor and patch numbers. For example:

```bash
NEO4J_BASE_IMAGE=arm64v8/openjdk:11-jdk-slim
NEO4JVERSION=4.3.7 make clean build
```


## If the Neo4j Version is not Publicly Available

The make script cannot automatically download unreleased source files, so you need to manually download them before building the images.

1. Assuming you cloned this repository to `$NEO4J_DOCKER_ROOT`, 
download the community and enterprise unix tar.gz files and copy them to `$NEO4J_DOCKER_ROOT/in`.
1. Run the make script setting `NEO4JVERSION` to the version number in the files downloaded into the `in/` folder.

For example: 

```bash
$ cd $NEO4J_DOCKER_ROOT
$ ls $NEO4J_DOCKER_ROOT/in
  neo4j-community-4.0.0-alpha05-unix.tar.gz  neo4j-enterprise-4.0.0-alpha05-unix.tar.gz

$ NEO4JVERSION=4.0.0-alpha05 make clean build
``` 

### If building an image from your local Neo4j repository

1. Clone the Neo4j github repository and checkout the branch you want.
3. Run `mvn install` plus whatever maven build flags you like. This should install the latest neo4j jars into the maven cache.
4. Copy the community and enterprise tar.gz files to `$NEO4J_DOCKER_ROOT/in`.
5. Use the `NEO4JVERSION` that is in the pom file of your Neo4j repository clone to build the docker image, e.g.:
```shell
$ NEO4JVERSION=5.5.0-SNAPSHOT make clean build
```

# Running the Tests

The tests are written in java, and require Maven plus JDK 17 (any JDK distributions should work, we use OpenJDK).

The tests require some information about the image before they can test it. 
These can be passed as an environment variable or a command line parameter when invoking maven:


| Env Variable    | Maven parameter | Description                                                |
|-----------------|-----------------|------------------------------------------------------------|
| `NEO4JVERSION`  | `-Dversion`     | the Neo4j version of the image                             |
| `NEO4J_IMAGE`   | `-Dimage`       | the tag of the image to test                               |
| `NEO4J_EDITION` | `-Dedition`     | Either `community` or `enterprise` depending on the image. |

<!-- prettified with http://www.tablesgenerator.com/markdown_tables -->

## Using Maven
The Makefile can run the entire test suite.
1. Make sure `java --version` is java 17.
2. `NEO4JVERSION=<VERSION> make test` This is a make target that will run these commands:
```bash
mvn test -Dimage=$(cat tmp/.image-id-community) -Dedition=community -Dversion=${NEO4JVERSION}
mvn test -Dimage=$(cat tmp/.image-id-enterprise) -Dedition=enterprise -Dversion=${NEO4JVERSION}
```

## In Intellij

1. Make sure the project SDK is java 17.
3. Install the [EnvFile](https://plugins.jetbrains.com/plugin/7861-envfile) Intellij plugin.
5. Under Run Configurations edit the Template JUnit configuration:
   1. Select the "EnvFile" tab
   2. Make sure "Enable EnvFile" is checked.
   3. Click the `+` then click to add a `.env` file.
   4. In the file selection box select `./tmp/devenv-enterprise.env` or `./tmp/devenv-community.env` depending on which one you want to test. If you do not have the `./tmp` directory, build the docker image and it will be created.
   5. Rebuilding the Neo4j image will regenerate the `.env` files, so you don't need to worry about keeping the environment up to date.

You should now be able to run unit tests straight from the IDE.


## Running with podman

Tests in this module are using testcontainers. The framework expects you to have docker available on your system.
And there are some issues like described here: https://github.com/testcontainers/testcontainers-java/issues/2088

TLDR on what you need to do to be able to use podman:

1. Make sure you have podman service running. For example: ```podman system service --time=0 unix:///tmp/podman.sock```

2. Add those environment variables:
```
DOCKER_HOST=unix:///tmp/podman.sock;
TESTCONTAINERS_RYUK_DISABLED=true;
TESTCONTAINERS_CHECKS_DISABLE=true 
```

# Troubleshooting
## cannot find symbol `com.sun.security.auth.module.UnixSystem`

This can happen if you switch from java 17 to java 11 (or the other way) and then try to rebuild the tests in Intellij.

Check that the `java.version` property in the [pom.xml file](../master/pom.xml) is set to 17.

