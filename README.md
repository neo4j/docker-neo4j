*NOTE:* Supported images are available in the [official image library](https://hub.docker.com/_/neo4j/) on Docker Hub.
Please use those for production use rather than building your own images from this repository.

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

# Getting support and contributing

Please create issues and pull requests in this repository.
