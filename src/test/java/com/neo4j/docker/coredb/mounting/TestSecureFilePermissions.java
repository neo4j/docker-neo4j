package com.neo4j.docker.coredb.mounting;

import com.neo4j.docker.utils.SetUserHelper;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class TestSecureFilePermissions {
    private static Logger log = LoggerFactory.getLogger(TestSecureFilePermissions.class);

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @BeforeAll
    static void skipRootlessImages() {
        Assumptions.assumeFalse(TestSettings.BASE_OS.isRootless(), "Skipping mount tests on rootless images");
    }

    @AfterEach
    void archiveTestArtifacts() throws Exception {
        temporaryFolderManager.triggerCleanup();
    }

    private GenericContainer setupBasicContainer(boolean asDefaultUser) {
        log.info("Running as user {}, with secure file permissions", asDefaultUser ? "root" : "non-root");

        GenericContainer container = new GenericContainer(TestSettings.IMAGE_ID);
        container
                .withExposedPorts(7474, 7687)
                .withLogConsumer(new Slf4jLogConsumer(log))
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withEnv("NEO4J_AUTH", "none")
                .withEnv("SECURE_FILE_PERMISSIONS", "yes")
                .waitingFor(WaitStrategies.waitForNeo4jReady("none"));
        if (!asDefaultUser) {
            SetUserHelper.containerAsNonRootUser(container);
        }
        return container;
    }

    @Test
    void testCanMountJustDataFolder() throws IOException {
        try (GenericContainer container = setupBasicContainer(false)) {
            Path dataMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/data");
            container.start();

            // neo4j should now have started, so there'll be stuff in the data folder
            // we need to check that stuff is readable and owned by the correct user
            MountingTestHelpers.verifyDataFolderContentsArePresentOnHost(
                    dataMount, MountingTestHelpers.KnownFileOwners.CURRENT);
        }
    }

    @Test
    void testCanMountJustLogsFolder() throws IOException {
        try (GenericContainer container = setupBasicContainer(false)) {
            Path logsMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            container.start();

            MountingTestHelpers.verifyLogsFolderContentsArePresentOnHost(
                    logsMount, MountingTestHelpers.KnownFileOwners.CURRENT);
        }
    }

    @Test
    void testCanMountDataAndLogsFolder() throws IOException {
        try (GenericContainer container = setupBasicContainer(false)) {
            Path dataMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/data");
            Path logsMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            container.start();

            MountingTestHelpers.verifyDataFolderContentsArePresentOnHost(
                    dataMount, MountingTestHelpers.KnownFileOwners.CURRENT);
            MountingTestHelpers.verifyLogsFolderContentsArePresentOnHost(
                    logsMount, MountingTestHelpers.KnownFileOwners.CURRENT);
        }
    }

    @Test
    void testCanNotWriteIfSecureEnabledAndAsRoot_data() throws IOException {
        try (GenericContainer container = setupBasicContainer(true)) {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/data");

            // currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
            container.setWaitStrategy(Wait.forLogMessage("[fF]older /data is not accessible for user", 1)
                    .withStartupTimeout(Duration.ofSeconds(20)));
            Assertions.assertThrows(
                    ContainerLaunchException.class,
                    () -> container.start(),
                    "Neo4j should not start in secure mode if data folder is unwritable");
        }
    }

    @Test
    void testCanNotWriteIfSecureEnabledAndAsRoot_logs() throws IOException {
        try (GenericContainer container = setupBasicContainer(true)) {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");

            // currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
            container.setWaitStrategy(Wait.forLogMessage("[fF]older /logs is not accessible for user", 1)
                    .withStartupTimeout(Duration.ofSeconds(20)));
            Assertions.assertThrows(
                    ContainerLaunchException.class,
                    () -> container.start(),
                    "Neo4j should not start in secure mode if logs folder is unwritable");
        }
    }
}
