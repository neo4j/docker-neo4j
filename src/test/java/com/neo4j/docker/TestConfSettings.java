package com.neo4j.docker;

import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class TestConfSettings {
    private static Logger log = LoggerFactory.getLogger(TestConfSettings.class);

    private GenericContainer createContainer() {
        return new GenericContainer(TestSettings.IMAGE_ID)
                .withEnv("NEO4J_AUTH", "none")
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withExposedPorts(7474, 7687)
                .withLogConsumer(new Slf4jLogConsumer(log));
    }

    @Test
    void testIgnoreNumericVars() {
        GenericContainer container = createContainer();
        container.withEnv("NEO4J_1a", "1");
        container.start();
        Assertions.assertTrue(container.isRunning());

        WaitingConsumer waitingConsumer = new WaitingConsumer();
        container.followOutput(waitingConsumer);

        Assertions.assertDoesNotThrow(() -> waitingConsumer.waitUntil(frame -> frame.getUtf8String()
                        .contains("WARNING: 1a not written to conf file because settings that " +
                                "start with a number are not permitted"), 15, TimeUnit.SECONDS),
                "Neo4j did not warn about invalid numeric config variable `Neo4j_1a`");
        container.stop();
    }

    private Map<String, String> parseConfFile(File conf) throws FileNotFoundException {
        Map<String, String> configurations = new HashMap<>();
        Scanner scanner = new Scanner(conf);
        while (scanner.hasNextLine()) {
            String[] params = scanner.nextLine().split("=", 2);
            log.debug(params[0] + "\t:\t" + params[1]);
            configurations.put(params[0], params[1]);
        }
        return configurations;
    }

    @Test
    void testAdminConfOverride() throws Exception {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion(new Neo4jVersion(3, 0, 0)),
                "No neo4j-admin in 2.3: skipping neo4j-admin-conf-override test");

        GenericContainer container = createContainer()
                .withEnv("NEO4J_dbms_memory_pagecache_size", "1000m")
                .withEnv("NEO4J_dbms_memory_heap_initial__size", "2000m")
                .withEnv("NEO4J_dbms_memory_heap_max__size", "3000m")
                .withCommand("echo running");
        container.setWaitStrategy(null);
        SetContainerUser.nonRootUser(container);
        Path confMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(container, "conf-", "/var/lib/neo4j/conf");

        container.start();
        container.execInContainer("neo4j-admin", "help", ">/dev/null");
        container.stop();

        // now check the settings we set via env are in the new conf file
        File conf = confMount.resolve("neo4j.conf").toFile();
        Assertions.assertTrue(conf.exists(), "configuration file not written");
        Assertions.assertTrue(conf.canRead(), "configuration file not readable for some reason?");

        Map<String, String> configurations = parseConfFile(conf);
        Assertions.assertTrue(configurations.containsKey("dbms.memory.pagecache.size"), "pagecache size not overridden");
        Assertions.assertEquals("1000m",
                configurations.get("dbms.memory.pagecache.size"),
                "pagecache size not overridden");

        Assertions.assertTrue(configurations.containsKey("dbms.memory.heap.initial_size"), "initial heap size not overridden");
        Assertions.assertEquals("2000m",
                configurations.get("dbms.memory.heap.initial_size"),
                "initial heap size not overridden");

        Assertions.assertTrue(configurations.containsKey("dbms.memory.heap.max_size"), "maximum heap size not overridden");
        Assertions.assertEquals("3000m",
                configurations.get("dbms.memory.heap.max_size"),
                "maximum heap size not overridden");
    }

    @Test
    void testReadTheConfFile() throws Exception {
        //Create container
        GenericContainer container = createContainer();
        //Mount /conf
        Path confMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(container, "conf-", "/conf");
        Path logMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(container, "logs-", "/logs");
        SetContainerUser.nonRootUser(container);
        //Create ReadConf.conf file with the custom env variables
        Path confFile = Paths.get("src", "test", "resources", "confs", "ReadConf.conf");
        Files.copy(confFile, confMount.resolve("neo4j.conf"));
        //Start the container
        container.setWaitStrategy(Wait.forHttp("/").forPort(7474).forStatusCode(200));
        container.start();
        //Check if the container reads the conf file
        Stream<String> lines = Files.lines(logMount.resolve("debug.log"));
        Optional<String> heapSizeMatch = lines.filter(s -> s.contains("dbms.memory.heap.max_size=512m")).findFirst();
        lines.close();
        Assertions.assertTrue(heapSizeMatch.isPresent(), "dbms.memory.heap.max_size was not set correctly");
        //Kill the container
        container.stop();
    }

    @Test
    void testCommentedConfigsAreReplacedByDefaultOnes() throws Exception {
        //Create container
        GenericContainer container = createContainer();
        //Mount /conf
        Path confMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(container, "conf-", "/conf");
        SetContainerUser.nonRootUser(container);
        //Create ConfsReplaced.conf file
        Path confFile = Paths.get("src", "test", "resources", "confs", "ConfsReplaced.conf");
        Files.copy(confFile, confMount.resolve("ConfsReplaced.conf"));
        //Start the container
        container.setWaitStrategy(Wait.forLogMessage(".*Config Dumped.*", 1).withStartupTimeout(Duration.ofSeconds(10)));
        container.setCommand("dump-config");
        container.start();
        //Read the config file to check if the config is appended correctly
        Stream<String> lines = Files.lines(confMount.resolve("neo4j.conf"));
        Optional<String> pagecacheSizeMatch = lines.filter(s -> s.contains("dbms.memory.pagecache.size=512M")).findFirst();
        lines.close();
        Assertions.assertTrue(pagecacheSizeMatch.isPresent(), "conf settings not appended correctly by docker-entrypoint");
        //Kill the container
        container.stop();
    }


    @Test
    void testConfigsAreNotOverridenByDockerentrypoint() throws Exception {
        //Create container
        GenericContainer container = createContainer();
        //Mount /conf
        Path confMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(container, "conf-", "/conf");
        SetContainerUser.nonRootUser(container);
        //Create ConfsNotOverriden.conf file
        Path confFile = Paths.get("src", "test", "resources", "confs", "ConfsNotOverriden.conf");
        Files.copy(confFile, confMount.resolve("neo4j.conf"));
        //Start the container
        container.setWaitStrategy(Wait.forLogMessage(".*Config Dumped.*", 1).withStartupTimeout(Duration.ofSeconds(10)));
        container.setCommand("dump-config");
        container.start();
        //Read the config file to check if the config is not overriden
        Stream<String> lines = Files.lines(confMount.resolve("neo4j.conf"));
        Optional<String> httpsListenAdressMatch = lines.filter(s -> s.contains("dbms.connector.https.listen_address=:8483")).findFirst();
        lines.close();
        Assertions.assertTrue(httpsListenAdressMatch.isPresent(), "docker-entrypoint has overriden custom setting set by user");
        //Kill the container
        container.stop();
    }


    @Test
    void testEnvVarsOverride() throws Exception {
        //Create container
        GenericContainer container = createContainer()
                .withEnv("NEO4J_dbms_memory_pagecache_size", "512m");
        //Mount /conf
        Path confMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(container, "conf-", "/conf");
        Path logMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(container, "logs-", "/logs");
        SetContainerUser.nonRootUser(container);
        //Create EnvVarsOverride.conf file
        Path confFile = Paths.get("src", "test", "resources", "confs", "EnvVarsOverride.conf");
        Files.copy(confFile, confMount.resolve("EnvVarsOverride.conf"));
        //Start the container
        container.setWaitStrategy(Wait.forHttp("/").forPort(7474).forStatusCode(200));
        container.start();
        //Read the config file to check if the config is not overriden
        Stream<String> lines = Files.lines(logMount.resolve("debug.log"));
        Optional<String> heapSizeMatch = lines.filter(s -> s.contains("dbms.memory.pagecache.size=512m")).findFirst();
        lines.close();
        Assertions.assertTrue(heapSizeMatch.isPresent(), "dbms.memory.pagecache.size was not set correctly");
        //Kill the container
        container.stop();
    }