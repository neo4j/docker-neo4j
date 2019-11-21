# Supported platforms

Development is supported on Ubuntu and OSX. It will probably work on
other Linuxes. Pull requests welcomed for other platforms.

# Prerequisites

## OSX only

1. install GNU Make (>=4.0)
1. install the Docker Toolbox. See: https://docs.docker.com/install/

## Linux

1. install the Docker Toolbox. See https://docs.docker.com/install/

# Building the Image

The build will create two images (one for Enterprise and one for Community) for a single version of Neo4j. 

The make script will automatically download the source files needed to build the images. 
You just need to specify the **full** Neo4j version including major, minor and patch numbers For example:

```bash
NEO4JVERSION=3.5.11 make clean build
```

If you want to build an alpha/beta release, this will still work:

```$bash
NEO4JVERSION=3.5.0-alpha01 make clean build
```

When the make script is complete, the image name will be written to file in `tmp/.image-id-community` and `tmp/.image-id-enterprise`:

```bash
$ cat tmp/.image-id-community
test/19564

$ cat tmp/.image-id-enterprise
test/13909
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


# Running the Tests

The tests are written in java, and require Maven plus jdk 11 for Neo4j version 4.0 onwards or jdk 8 for earlier Neo4j versions.

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
1. Make sure `java --version` is java 11 or java 8 as necessary.
2. `NEO4JVERSION=<VERSION> make test` This is a make target that will run these commands:
```bash
mvn test -Dimage=$(cat tmp/.image-id-community) -Dedition=community -Dversion=${NEO4JVERSION}
mvn test -Dimage=$(cat tmp/.image-id-enterprise) -Dedition=enterprise -Dversion=${NEO4JVERSION}
```

## In Intellij

1. Make sure the project SDK is java 11 or java 8 as necessary.
1. Edit the [pom.xml file](../master/pom.xml) to replace  `${env.NEO4JVERSION}` with the `NEO4JVERSION` you used to build the image.
*(Yes this is terrible, and we need to think of an alternative to this)*. 

    For example:
    ```xml
    <neo4j.version>${env.NEO4JVERSION}</neo4j.version>
    ```
    becomes
    ```xml
    <neo4j.version>4.0.0-alpha05</neo4j.version>
    ```
1. Install the [EnvFile](https://plugins.jetbrains.com/plugin/7861-envfile) Intellij plugin.
2. Under Run Configurations edit the Template JUnit configuration:
   1. Select the "EnvFile" tab
   2. Make sure "Enable EnvFile" is checked.
   3. Click the `+` then click to add a `.env` file.
   4. In the file selection box select `./tmp/devenv-enterprise.env` or `./tmp/devenv-community.env` depending on which one you want to test.
   5. Rebuilding the Neo4j image will regenerate the `.env` files, so you don't need to worry about keeping the environment up to date.


### If the Neo4j Version is not Publicly Available

1. Clone the Neo4j github repository and checkout the branch you want. 
2. Make sure `java --version` returns java 11 if you're building Neo4j 4.0+, or java 8 if building an earlier branch.
1. Run `mvn install` plus whatever maven build flags you like. This should install the latest neo4j jars into the maven cache.
1. Follow instructions for [running tests in Intellij](#in-intellij), 
use the `NEO4JVERSION` that is in the pom file of your Neo4j repository clone.

### cannot find symbol `com.sun.security.auth.module.UnixSystem`

This can happen if you switch from java 8 to java 11 and then try to rebuild the tests in Intellij.

Check that the `java.version` property in the [pom.xml file](../master/pom.xml) is set to 11 instead of 1.8.
DO NOT commit this set to 11 (yes this is a terrible solution).

