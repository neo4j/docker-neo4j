# What is this?

This code is purely for generating the [myPlugin.jar test artifact](../src/test/resources/testplugin/myPlugin.jar).
It is a test artifact used for verifying that the `NEO4J_PLUGINS` feature works correctly. *It has nothing to do with Neo4j database or even the Neo4j docker image source code.*

# Do I need to run this code at all?

Short answer: **No.**

A pre-generated `myPlugin.jar` is already included in the test resources. 

The only situation where you would need to even look at this is if you are actively developing the Neo4j docker image and for some reason the plugin tests are not loading `myPlugin.jar` any more.


## How to generate new test plugin

The Makefile will do all the work for you, all you need to do is pick which version of Neo4j to use:

```shell
NEO4JVERSION=4.4.11 make clean plugin
```

The Dockerfile is currently set to use java 11. If that needs to change then just change the base image that the Dockerfile uses.

Don't forget to commit the newly generated `myPlugin.jar` back to git when you've finished.
