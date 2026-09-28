package com.neo4j.docker.coredb.mounting;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.SetUserHelper;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

public class TestMounting {
    private static Logger log = LoggerFactory.getLogger(TestMounting.class);

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @AfterEach
    void archiveTestArtifacts() throws Exception {
        temporaryFolderManager.triggerCleanup();
    }

    private GenericContainer setupBasicContainer(boolean asDefaultUser) {
        log.info("Running as user {}, {}", asDefaultUser ? "root" : "non-root");
        GenericContainer container = new GenericContainer(TestSettings.IMAGE_ID);
        container
                .withExposedPorts(7474, 7687)
                .withLogConsumer(new Slf4jLogConsumer(log))
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withEnv("NEO4J_AUTH", "none")
                .withEnv("NEO4J_DEBUG", "yes")
                .waitingFor(WaitStrategies.waitForNeo4jReady("none"));
        if (!asDefaultUser) {
            SetUserHelper.containerAsNonRootUser(container);
        }
        return container;
    }

    private MountingTestHelpers.KnownFileOwners getExpectedOwner(boolean isDefaultUser) {
        return isDefaultUser ? MountingTestHelpers.KnownFileOwners.NEO4J : MountingTestHelpers.KnownFileOwners.CURRENT;
    }

    private Path createAndMountFolderWrapper(GenericContainer container, String mountpoint, boolean asDefaultUser)
            throws Exception {
        Path p = temporaryFolderManager.createFolderAndMountAsVolume(container, mountpoint);
        // In rootless images running without `--user` flag, the internal user will be neo4j.
        // so mounted folders must be owned by neo4j otherwise they will not be read/writable
        if (TestSettings.BASE_OS.isRootless() && asDefaultUser) {
            SetUserHelper.setFolderOwnerToNeo4j(p);
        }
        return p;
    }

    @ParameterizedTest(name = "as_default_user_{0}")
    @ValueSource(booleans = {true, false})
    void canDumpConfig(boolean asDefaultUser) throws Exception {
        File confFile;
        Path confMount;
        String assertMsg = "Conf file was not successfully dumped when running container as "
                + (asDefaultUser ? "root" : "current user");

        try (GenericContainer container = setupBasicContainer(asDefaultUser)) {
            // Mount /conf
            confMount = createAndMountFolderWrapper(container, "/conf", asDefaultUser);
            confFile = confMount.resolve("neo4j.conf").toFile();

            // Start the container
            container.setWaitStrategy(
                    Wait.forLogMessage(".*Config Dumped.*", 1).withStartupTimeout(Duration.ofSeconds(30)));
            container.setStartupCheckStrategy(new OneShotStartupCheckStrategy());
            container.setCommand("dump-config");
            container.start();
        }
        Assertions.assertTrue(confFile.exists(), "Conf file did not get written to expected location");
    }

    @ParameterizedTest(name = "as_default_user_{0}")
    @ValueSource(booleans = {true, false})
    void testCanMountJustDataFolder(boolean asDefaultUser) throws Exception {
        try (GenericContainer container = setupBasicContainer(asDefaultUser)) {
            Path dataMount = createAndMountFolderWrapper(container, "/data", asDefaultUser);
            container.start();

            // neo4j should now have started, so there'll be stuff in the data folder
            // we need to check that stuff is readable and owned by the correct user
            MountingTestHelpers.verifyDataFolderContentsArePresentOnHost(dataMount, getExpectedOwner(asDefaultUser));
        }
    }

    @ParameterizedTest(name = "as_default_user_{0}")
    @ValueSource(booleans = {true, false})
    void testCanMountJustLogsFolder(boolean asDefaultUser) throws Exception {
        try (GenericContainer container = setupBasicContainer(asDefaultUser)) {
            Path logsMount = createAndMountFolderWrapper(container, "/logs", asDefaultUser);
            container.start();

            MountingTestHelpers.verifyLogsFolderContentsArePresentOnHost(logsMount, getExpectedOwner(asDefaultUser));
        }
    }

    @ParameterizedTest(name = "as_default_user_{0}")
    @ValueSource(booleans = {true, false})
    void testCanMountDataAndLogsFolder(boolean asDefaultUser) throws Exception {
        try (GenericContainer container = setupBasicContainer(asDefaultUser)) {
            Path dataMount = createAndMountFolderWrapper(container, "/data", asDefaultUser);
            Path logsMount = createAndMountFolderWrapper(container, "/logs", asDefaultUser);
            container.start();

            MountingTestHelpers.verifyDataFolderContentsArePresentOnHost(dataMount, getExpectedOwner(asDefaultUser));
            MountingTestHelpers.verifyLogsFolderContentsArePresentOnHost(logsMount, getExpectedOwner(asDefaultUser));
        }
    }

    @ParameterizedTest(name = "as_default_user_{0}")
    @ValueSource(booleans = {true, false})
    void canMountAllTheThings_fileMounts(boolean asDefaultUser) throws Exception {
        try (GenericContainer container = setupBasicContainer(asDefaultUser)) {
            createAndMountFolderWrapper(container, "/conf", asDefaultUser);
            createAndMountFolderWrapper(container, "/data", asDefaultUser);
            createAndMountFolderWrapper(container, "/import", asDefaultUser);
            createAndMountFolderWrapper(container, "/logs", asDefaultUser);
            createAndMountFolderWrapper(container, "/metrics", asDefaultUser);
            createAndMountFolderWrapper(container, "/plugins", asDefaultUser);
            container.start();
            DatabaseIO databaseIO = new DatabaseIO(container);
            // do some database writes so that we try writing to writable folders.
            databaseIO.putInitialDataIntoContainer("neo4j", "none");
            databaseIO.verifyInitialDataInContainer("neo4j", "none");
        }
    }

    @ParameterizedTest(name = "as_default_user_{0}")
    @ValueSource(booleans = {true, false})
    void canMountAllTheThings_namedVolumes(boolean asDefaultUser) throws Exception {
        String id = String.format("%04d", new Random().nextInt(10000));
        try (GenericContainer container = setupBasicContainer(asDefaultUser)) {
            container.withCreateContainerCmdModifier((Consumer<CreateContainerCmd>) cmd -> cmd.getHostConfig()
                    .withBinds(
                            Bind.parse("conf-" + id + ":/conf"),
                            Bind.parse("data-" + id + ":/data"),
                            Bind.parse("import-" + id + ":/import"),
                            Bind.parse("logs-" + id + ":/logs"),
                            // Bind.parse("metrics-"+id+":/metrics"), 	//todo metrics needs to be writable but we aren't
                            // chowning in the dockerfile, so a named volume for metrics will fail
                            Bind.parse("plugins-" + id + ":/plugins")));
            container.start();
            DatabaseIO databaseIO = new DatabaseIO(container);
            // do some database writes so that we try writing to writable folders.
            databaseIO.putInitialDataIntoContainer("neo4j", "none");
            databaseIO.verifyInitialDataInContainer("neo4j", "none");
        } finally {
            cleanupVolumes(id);
        }
    }

    private static void cleanupVolumes(String id) {
        DockerClient client = DockerClientFactory.instance().client();
        client.removeVolumeCmd("conf-" + id).exec();
        client.removeVolumeCmd("data-" + id).exec();
        client.removeVolumeCmd("import-" + id).exec();
        client.removeVolumeCmd("logs-" + id).exec();
        client.removeVolumeCmd("plugins-" + id).exec();
    }

    @Test
    void shouldReownSubfilesToNeo4j() throws Exception {
        Path logMount = temporaryFolderManager.createFolder("subfileownership");
        Path debugLog = logMount.resolve("debug.log");
        // put file in logMount
        Files.write(debugLog, "some log words".getBytes());
        // make neo4j own the conf folder but NOT the neo4j.conf
        SetUserHelper.setFolderOwnerToNeo4j(logMount);
        SetUserHelper.setFolderOwnerToCurrentUser(debugLog);

        try (GenericContainer container = setupBasicContainer(true)) {
            temporaryFolderManager.mountHostFolderAsVolume(container, logMount, "/logs");
            container.start();
            // if debug.log doesn't get re-owned, neo4j will not start and this test will fail here
        }
    }
}
