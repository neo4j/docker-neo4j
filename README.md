*NOTE:* Supported images are available in the [official image library](https://hub.docker.com/_/neo4j/) on Docker Hub.
Please use those for production use.

# Using the Neo4j Docker image

## Neo4j 2.3

Documentation for the Neo4j 2.3 image can be found [here](http://neo4j.com/developer/docker-2.x/).

You can start a Neo4j 2.3 container like this:

```
docker run \
    --publish=7474:7474 \
    --volume=$HOME/neo4j/data:/data \
    neo4j
```

## Neo4j 3.0

Documentation for the Neo4j 3.0 image can be found [here](http://neo4j.com/developer/docker-3.x/).

You can start a Neo4j 3.0 container like this:

```
docker run \
    --publish=7474:7474 --publish=7687:7687 \
    --volume=$HOME/neo4j/data:/data \
    neo4j/neo4j:milestone
```

# Getting support and contributing

Please create issues and pull requests in the Github repository.
