package com.neo4j.docker.utils;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class HelperContainers {
    public static GenericContainer nginx() {
        return new GenericContainer(DockerImageName.parse("nginx:latest"))
                .withExposedPorts(80)
                .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofSeconds(20)));
    }
}
