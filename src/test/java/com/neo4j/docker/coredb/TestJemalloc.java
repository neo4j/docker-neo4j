package com.neo4j.docker.coredb;

import static com.neo4j.docker.utils.WaitStrategies.waitForNeo4jReady;

import com.neo4j.docker.utils.BaseOS;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public class TestJemalloc {
    private static final Logger log = LoggerFactory.getLogger(TestJemalloc.class);

    @Test
    void shouldUseJemalloc() throws Exception {
        Assumptions.assumeTrue(
                TestSettings.NEO4J_VERSION.isAtLeastVersion(Neo4jVersion.NEO4J_VERSION_500),
                "jemalloc is only added to 5.x and calver images");
        Assumptions.assumeTrue(
                TestSettings.BASE_OS == BaseOS.TRIXIE || TestSettings.BASE_OS == BaseOS.UBI10,
                "jemalloc is only added to the trixie and ubi10 images");
        try (GenericContainer<?> container = new GenericContainer<>(TestSettings.IMAGE_ID)) {
            container
                    .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                    .withExposedPorts(7474, 7687)
                    .withLogConsumer(new Slf4jLogConsumer(log))
                    .waitingFor(waitForNeo4jReady("neo4j"));
            container.start();
            Container.ExecResult maps = container.execInContainer(
                    "sh", "-c", "su-exec neo4j cat /proc/$(cat /var/lib/neo4j/run/neo4j.pid)/maps");
            Assertions.assertEquals(0, maps.getExitCode(), "Could not read memory maps of neo4j:\n" + maps);
            Assertions.assertTrue(
                    maps.getStdout().contains("libjemalloc"),
                    "jemalloc was not loaded by the neo4j process. Actual output:\n" + maps);
        }
    }
}
