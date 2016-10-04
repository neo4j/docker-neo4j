# Supported platforms

Development is supported on Ubuntu and OSX. It will probably work on
other Linuxes. Pull requests welcomed for other platforms.

# Prerequisites

## OSX only

1. install GNU Make (>=4.0)
1. install the Docker Toolbox

## Debian

1. install `uuid-runtime`

## All platforms

1. download the Neo4j Community unix tarball
1. copy `devenv.local.template` as `devenv.local`; fill in the
   directory to which you have downloaded the tarball and its version

# Build process

## OSX only

1. create a docker-machine VM
1. export the docker-machine environment for docker

## All platforms

1. `. devenv`
1. `make`
