# Supported platforms

Development is supported on Ubuntu and OSX. It will probably work on
other Linuxes. Pull requests welcomed for other platforms.

# Prerequisites

## OSX only

1. install GNU Make (>=4.0)
1. install the Docker Toolbox
1. create a docker-machine VM
1. export the docker-machine environment for docker

## Debian

1. install `uuid-runtime`

# Building

To set up a development environment for `docker-neo4j`, you must
source `devenv` in your shell before you do anything.

    $ . devenv

The build will create two images (one for Enterprise and one for
Community) for a single version of Neo4j. The Enterprise and Community
tarballs must be available in the `in` directory and the version
number must be passed to `make`.

For local development it's expected that `in` will contain symlinks to
the tarballs to make it easy to rebuild Neo4j and test the results.

    ln -s $NEO4J_SRC/packaging/standalone/target/neo4j-*-3.4.0-SNAPSHOT-unix.tar.gz in
    make NEO4JVERSION=3.4.0-SNAPSHOT

For building images of released versions, they can be downloaded into
`in`.

    (
      cd in
      curl --remote-name $DOWNLOAD_ROOT/neo4j-3.3.5-community-unix.tar.gz
      curl --remote-name $DOWNLOAD_ROOT/neo4j-3.3.5-enterprise-unix.tar.gz
    )
    make NEO4JVERSION=3.3.5

To avoid having to pass the version to make every time you can set it
in the environment by copying `devenv.local` from
`devenv.local.template`, filling in the version you are working with
and re-sourcing `devenv`.
