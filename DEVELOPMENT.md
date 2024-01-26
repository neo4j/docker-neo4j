# Supported platforms

Development is tested on Ubuntu and OSX. It will probably work on other Linuxes.

# Prerequisites

## OSX only

1. install GNU Make (>=4.0)
1. install the Docker Toolbox. See: https://docs.docker.com/install/

## Linux

1. install the Docker Toolbox. See https://docs.docker.com/install/

# Building the Image

There are two supported base operating systems that the docker image can be build upon:
 * debian, based off `debian:bullseye-slim`.
 * RedHat ubi9, based off `redhat/ubi9-minimal`. Only available for 4.4 onwards.

On top of that there is also the choice Neo4j version, and whether to build `community` or `enterprise` edition Neo4j.

## Just give me the build TLDR

You probably want to create the neo4j image with whatever base image and community/enterprise variant you need, then tag it like a released neo4j version.

Here are some examples:
```bash
# 5.9.0 community edition and debian 
NEO4JVERSION=5.9.0 make tag-debian-community
# creates tag neo4j:5.9.0-debian

# 4.4.20 community edition and redhat ubi9
NEO4JVERSION=4.4.20 make tag-ubi9-community
# creates tag neo4j:4.4.20-ubi9

# 5.2.0 enterprise edition and debian 
NEO4JVERSION=5.2.0 make tag-debian-enterprise
# creates tag neo4j:5.2.0-enterprise-debian

# 4.4.0 enterprise edition and redhat ubi9
NEO4JVERSION=4.4.0 make tag-ubi9-enterprise
# creates tag neo4j:4.4.0-enterprise-ubi9
```

## The build script

The build script [build-docker-image.sh](./build-docker-image.sh) will take these options and produce a Neo4j image and a neo4j-admin image, for the combination you request.
For example:
```bash
#  debian based 4.4.22 community edition:
./build-docker-image.sh 4.4.22 community debian
#  redhat-ubi9 based 5.9.0 enterprise edition:
./build-docker-image.sh 5.9.0 enterprise ubi9
```
The make script will automatically download the source files needed to build the images.
You just need to specify the **full** Neo4j version including major, minor and patch numbers.

The source code (entrypoint, Dockerfile and so on) is outputted into the `build/<base OS>/coredb/<edition>` and `build/<base OS>/neo4j-admin/<edition>` folders.

The resulting images will have a randomly generated tag, which is written into the files `build/<base OS>/coredb/.image-id-<edition>` and `build/<base OS>/neo4j-admin/.image-id-<edition>`.

## Using the Convenience Makefile

The [Makefile](./Makefile) is a wrapper around the [build-docker-image.sh](./build-docker-image.sh).
It mostly just adds extra functionality to help you build lots of images at once, or does extra steps like tagging, testing and generating release files.

The four actions it can do are:
* `build`
* `test`
* `tag`
* `package`

For each action, it can be broken down by base image and community/enterprise type.
For example `build`, has the following make targets:
* `build`. Builds *every* variant.
* `build-debian`. Builds debian community and enterprise.
* `build-ubi9`. Builds redhat-ubi9 community and enterprise.
* `build-debian-community`
* `build-debian-enterprise`
* `build-ubi9-community`
* `build-ubi9-enterprise`

The other actions have the same targets.

This is an example of calling one of the build targets:
```bash
NEO4JVERSION=4.4.4 make clean build-debian
```
This will build community and enterprise, coredb and neo4j-admin, all based on debian.

To build and then tag all debian neo4j images, use `tag`. For example:
```bash
NEO4JVERSION=4.4.4 make clean tag-debian
```

## Building ARM64 based images

From Neo4j 4.4.0 onwards, the Neo4j image should be buildable on any architecture using the same build commands as [Building the Image](#building-the-image).

### Building ARM versions before 4.4
Earlier versions of Neo4j are no longer under active development and have not been tested on ARM architectures, even when those versions were under development.

It is strongly advised that you use 4.4.0 or later on an ARM system.

If you really must use an unsupported Neo4j version then in your clone of this repository, `git checkout` tag `neo4j-4.3.23` and follow development instructions there.
https://github.com/neo4j/docker-neo4j/blob/neo4j-4.3.23/DEVELOPMENT.md#building-arm64-based-images


## If the Neo4j Version is not Publicly Available

The make script cannot automatically download unreleased source files, so you need to manually download them before building the images.

1. Assuming you cloned this repository to `$NEO4J_DOCKER_ROOT`, 
download the community and enterprise unix tar.gz files from the `packaging` build in our pipeline, and copy them to `$NEO4J_DOCKER_ROOT/in`.
1. Run the make script setting `NEO4JVERSION` to the version number in the files downloaded into the `in/` folder.

For example: 

```bash
$ cd $NEO4J_DOCKER_ROOT
$ ls $NEO4J_DOCKER_ROOT/in
  neo4j-community-4.0.0-alpha05-unix.tar.gz  neo4j-enterprise-4.0.0-alpha05-unix.tar.gz

$ NEO4JVERSION=4.0.0-alpha05 make clean build
``` 

### If building an image from your local Neo4j repository

This isn't recommended since you will need to package your Neo4j tar with the browser so that neo4j will be responsive on 7474 and 7687.

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
| `NEO4JADMIN_IMAGE` | `-Dadminimage` | the tag of the neo4j-admin image to test           |
| `NEO4J_EDITION` | `-Dedition`     | Either `community` or `enterprise` depending on the image. |

<!-- prettified with http://www.tablesgenerator.com/markdown_tables -->

## Using Maven
The Makefile can run the entire test suite.
1. Make sure `java --version` is java 17.
2. `NEO4JVERSION=<VERSION> make test-<BASE OS>` This is a make target that will run these commands:
```bash
mvn test -Dimage=$(cat build/<BASE OS>/coredb/.image-id-enterprise) -Dadminimage=$(cat build/<BASE OS>/neo4j-admin/.image-id-enterprise) -Dedition=enterprise -Dversion=${NEO4JVERSION}
mvn test -Dimage=$(cat build/<BASE OS>/coredb/.image-id-community) -Dadminimage=$(cat build/<BASE OS>/neo4j-admin/.image-id-community) -Dedition=community -Dversion=${NEO4JVERSION}
```

## In Intellij

1. Make sure the project SDK is java 17.
3. Install the [EnvFile](https://plugins.jetbrains.com/plugin/7861-envfile) Intellij plugin.
5. Under Run Configurations edit the Template JUnit configuration:
   1. Select the "EnvFile" tab
   2. Make sure "Enable EnvFile" is checked.
   3. Click the `+` then click to add a `.env` file.
   4. In the file selection box select `./build/<BASE OS>/devenv-enterprise.env` or `./build/<BASE OS>/devenv-community.env` depending on which one you want to test. If you do not have the `./build` directory, build the docker image and it will be created.
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

