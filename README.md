## Container for Neo4j 2.2

**Note: this is just _work in progress_ alpha state, not suited for production/serious use**

Neo4j is an highly scalable, robust (fully ACID) native graph database.
It is used in mission-critical apps by thousands of leading, startups, enterprises, and governments around the world.

Learn more on http://neo4j.com and get started with http://neo4j.com/developer

This Dockerfile creates a container for Neo4j 2.2.2 community edition that is ready to run and can link to your external data directories.

### Setup

1. Build & Run:

```
git clone https://github.com/neo4j-contrib/docker-neo4j
cd docker-neo4j
docker build .

# note the resulting image-id
# run the image, -i -t for interactive and terminal so you can stop the server with ctrl-c
# --rm deletes the image instance after it has finished
# pass in the path to an existing neo4j `data` directory or just an empty directory

# docker run -i -t --rm --name neo4j -v </path/to/neo4j/data-dir>:/data -p <external port>:7474 <image-id>
# for example:

docker run -i -t --rm --name neo4j -v $HOME/neo4j-data:/data -p 8476:7474 <image-id>
```

2. Open in browser

     `http://localhost:8474`

On OSX use http://boot2docker.io/[boot2docker] and replace localhost with the IP from `$DOCKERHOST` instead.

### Authentication


Please note that Neo4j 2.2.2 requires authentication.

Providing your own password :

You can provide your own password when starting the container with the `NEO4J_AUTH` environment variable :

docker run -i -t --rm --name neo4j -v $HOME/neo4j-data:/data -p 8476:7474 -e NEO4J_AUTH=myPassword <image-id>

If you don't provided a password as environment variable when starting the container, you'll have to login with `neo4j/neo4j` at the first connection and set a new password.
The auth credentials are stored in the `/data/dbms/auth` file, which will reside in your external directory.

You can also access the Neo4j log-files in `data/log` and `data/graph.db/messages.log`

If you set an environment variable `NEO4J_NO_AUTH` to a non-empty value, Neo4j's authentication will be disabled.

### Configuration

You can also mount a `conf` (`-v $HOME/my-neo-conf:/conf`) directory whose content will be copied over Neo4j's configuration.

### TODO

* Provide initial password externally
* Memory Tuning Options (esp. page-cache)
