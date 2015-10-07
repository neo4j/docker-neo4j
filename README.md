# What is Neo4j?

**Note: this image is a _work in progress_ beta, and is not suited for production use**

Neo4j is a highly scalable, robust, native graph database. It is used in mission-critical apps by thousands of leading startups, enterprises, and governments around the world. You can learn more [here](http://neo4j.com/developer).

# How to use this image

The image exposes two ports (`7474` and `7473`) for HTTP and HTTPS access to the Neo4j API and a volume (`/data`) to allow the database to be persisted outside its container.

	docker run \
	    --detach \
	    --publish=7474:7474 \
	    --volume=$HOME/neo4j-data:/data \
	    neo4j/neo4j

Point your browser at `http://localhost:7474`.

Please note that by default Neo4j requires authentication. You have to login with `neo4j/neo4j` at the first connection and set a new password.

## Docker configuration

### File descriptor limit

Neo4j may use a large number of file descriptors if many indexes are in use or there is a large number of simultaneous database connections.

Docker controls the number of open file descriptors in a container; the limit depends on the configuration of your system. We recommend a limit of at least 40000 for running Neo4j.

To check the limit on your system, run this command:

	docker run neo4j/neo4j \
	    bash -c 'echo Soft limit: $(ulimit -Sn); echo Hard limit: $(ulimit -Hn)'

To override the default configuration for a single container, use the `--ulimit` option like this:

	docker run \
	    --detach \
	    --publish=7474:7474 \
	    --volume=$HOME/neo4j-data:/data \
	    --ulimit=nofile=40000:40000
	    neo4j/neo4j

## Neo4j configuration

The image provides a useable default configuration for learning about Neo4j, but it is not suitable for production use. You can read more about configuring Neo4j in the [manual](http://neo4j.com/docs/stable/configuration.html).

There are three ways to modify the configuration depending on how much you need to customize the image.

## Environment variables

Pass environment variables to the container when you run it.

	docker run \
	    --detach \
	    --publish=7474:7474 \
	    --volume=$HOME/neo4j-data:/data \
	    --env=NEO4J_CACHE_MEMORY=4G \
	    neo4j/neo4j

The following environment variables are available:

-	`NEO4J_CACHE_MEMORY`: the size of Neo4j's native-memory cache, defaults to 512M
-	`NEO4J_HEAP_MEMORY`: the size of Neo4j's heap in MB, defaults to the JVM default for your system
-	`NEO4J_KEEP_LOGICAL_LOGS`: the retention policy for logical logs, defaults to `100M size`
-	`NEO4J_AUTH`: controls authentication, set to `none` to disable authentication or `neo4j/<password>` to override the default password (see documentation [here](http://neo4j.com/docs/stable/rest-api-security.html))

### Enterprise Edition

The following settings control features that are only available in the Enterprise Edition of Neo4j.

-	`NEO4J_DATABASE_MODE`: the database mode, defaults to `SINGLE`, set to `HA` to create a cluster
-	`NEO4J_SERVER_ID`: the id of the server, must be unique within a cluster
-	`NEO4J_INITIAL_HOSTS`: comma-separated list of other members of the cluster

## `/conf` volume

To make arbitrary modifications to the Neo4j configuration, provide the container with a `/conf` volume.

	docker run \
	    --detach \
	    --publish=7474:7474 \
	    --volume=$HOME/neo4j-data:/data \
	    --volume=$HOME/neo4j-conf:/conf \
	    neo4j/neo4j

The `/conf` volume will override all configuration provided by the image and must therefore contain a complete, valid set of Neo4j configuration files.

## Build a new image

For more complex customization of the image you can create a new image based on this one.

	FROM neo4j/neoj4
