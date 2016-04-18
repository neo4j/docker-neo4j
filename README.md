*NOTE:* Supported images are available in the [official image library](https://hub.docker.com/_/neo4j/) on Docker Hub.
Please use those for production use rather than building your own images from this repository.

# Using the Neo4j Docker image

## Neo4j 3.0

Documentation for the Neo4j 3.0 image can be found [here](http://neo4j.com/developer/docker-3.x/).

You can start a Neo4j 3.0 container like this:

```
docker run \
    --publish=7474:7474 --publish=7687:7687 \
    --volume=$HOME/neo4j/data:/data \
    neo4j/neo4j:3.0.0-RC1
```

# Getting support and contributing

Please create issues and pull requests in this repository.
