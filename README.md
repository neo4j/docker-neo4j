*NOTE:* Supported images are available in the [official image library](https://hub.docker.com/_/neo4j/) on Docker Hub.
Please use those for production use.

# Using the Neo4j Docker Image

Documentation for the Neo4j image can be found [here](https://neo4j.com/docs/operations-manual/current/deployment/single-instance/docker/).

You can start a Neo4j container like this:

```
docker run \
    --publish=7474:7474 --publish=7687:7687 \
    --volume=$HOME/neo4j/data:/data \
    --volume=$HOME/neo4j/logs:/logs \
    neo4j:latest
```

To start a Neo4j Enterprise Edition container, you can run:

```
docker run \
    --publish=7474:7474 --publish=7687:7687 \
    --env=NEO4J_ACCEPT_LICENSE_AGREEMENT=yes \
    --volume=$HOME/neo4j/data:/data \
    --volume=$HOME/neo4j/logs:/logs \
    neo4j:enterprise
```

Mounting the `/data` and `/logs` folder is optional, 
but it means that data can persist between closing and reopening Neo4j containers.


# Building and Developing the Neo4j Docker Image

See [DEVELOPMENT.md](DEVELOPMENT.md)

# Getting support and contributing

Please create issues and pull requests against this Github repository.
