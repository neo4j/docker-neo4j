## Container for Neo4j 2.2 Community Edition

**Note: this is just _work in progress_ Beta state, not suited for production/serious use**

Neo4j is an highly scalable, robust (fully ACID) native graph database.
It is used in mission-critical apps by thousands of leading startups, enterprises, and governments around the world.

Learn more on http://neo4j.com and get started with http://neo4j.com/developer

This Dockerfile creates a container for Neo4j 2.2.4 community edition that is ready to run and can link to your external data directories.

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

On OSX use http://boot2docker.io/[boot2docker] and replace localhost with the IP from `$DOCKER_HOST` instead. Tip: you can use `boot2docker ip` to get it.

### Authentication

Please note that Neo4j 2.2.4 requires authentication.
You have to login with `neo4j/neo4j` at the first connection and set a new password.
The auth credentials are stored in the `/data/dbms/auth` file, which will reside in your external directory.

You can also access the Neo4j log-files in `data/log` and `data/graph.db/messages.log`

If you set the environment variable `NEO4J_NO_AUTH` to a non-empty value, Neo4j's authentication will be disabled.

### Configuration

You can provide additional environment variables to your `docker run` command to control certain aspects of the running Neo4j instance:

* `-e NEO4J_OPEN_FILES=20000`, default 40000
* `-e NEO4J_HEAP_MEMORY=758M`, default is jvm default
* `-e NEO4J_CACHE_MEMORY=1G`, default is 512M
* `-e NEO4J_NO_AUTH=true`, default is not set, i.e. auth enabled

You can also mount a `conf` (`-v $HOME/my-neo-conf:/conf`) directory whose content will be copied over Neo4j's configuration.
Then you are completely responsible yourself for providing the correctly set-up config files.

### TODO

* Provide initial password externally
* "docker stop" needs to execute graceful shutdown
