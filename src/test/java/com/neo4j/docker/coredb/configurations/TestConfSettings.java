package com.neo4j.docker.coredb.configurations;

import com.neo4j.docker.coredb.plugins.Neo4jPluginEnv;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.driver.exceptions.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;

public class TestConfSettings
{
    private static final String PASSWORD = "none";
    private static final String AUTH = "none"; // or "neo4j/"+PASSWORD if we want authentication
    private final Logger log = LoggerFactory.getLogger(TestConfSettings.class);
    private static Path confFolder;
    private static Map<Setting,Configuration> confNames;
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @BeforeAll
    static void getVersionSpecificConfigurationSettings()
    {
        confFolder = Configuration.getConfigurationResourcesFolder();
        confNames = Configuration.getConfigurationNameMap();
    }

    private GenericContainer createContainer()
    {
        return new GenericContainer(TestSettings.IMAGE_ID)
                .withEnv("NEO4J_AUTH", AUTH)
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withExposedPorts(7474, 7687)
                .withLogConsumer(new Slf4jLogConsumer(log));
    }

    private GenericContainer makeContainerDumpConfig(GenericContainer container)
    {
        SetContainerUser.nonRootUser( container );
        container.setCommand("dump-config");
        WaitStrategies.waitUntilContainerFinished(container, Duration.ofSeconds(30));
        return container;
    }

    private Map<String, String> parseConfFile(File conf) throws FileNotFoundException
    {
        Map<String, String> configurations = new HashMap<>();
        Scanner scanner = new Scanner(conf);
        while ( scanner.hasNextLine() )
        {
            String[] params = scanner.nextLine().split( "=", 2 );
            if(params.length < 2)
            {
                continue;
            }
            log.debug( params[0] + "\t:\t" + params[1] );
            configurations.put( params[0], params[1] );
        }
        return configurations;
    }

    private void assertConfigurationPresentInDebugLog( Path debugLog, Configuration setting, String value, boolean shouldBeFound ) throws IOException
    {
        // searches the debug log for the given string, returns true if present
        Stream<String> lines = Files.lines(debugLog);
        String actualSetting = lines.filter(s -> s.contains( setting.name ))
                                    .findFirst()
                                    .orElse( "" );
        lines.close();
        if(shouldBeFound)
        {
            Assertions.assertTrue( !actualSetting.isEmpty(), setting.name+" was never set" );
            Assertions.assertTrue( actualSetting.contains( value ),
                                   setting.name +" is set to the wrong value. Expected: "+
                                   value +" Actual: " + actualSetting );
        }
        else
        {
            Assertions.assertTrue( actualSetting.isEmpty(),setting.name+" was set when it should not have been. " +
                                                           "Actual value: "+actualSetting );
        }
    }

    @Test
    void testIgnoreNumericVars()
    {
        try(GenericContainer container = createContainer())
        {
            container.withEnv( "NEO4J_1a", "1" )
                     .waitingFor( WaitStrategies.waitForBoltReady() );
            container.start();
            Assertions.assertTrue( container.isRunning() );
            String errorLogs = container.getLogs( OutputFrame.OutputType.STDERR);
            Assertions.assertTrue( errorLogs.contains( "WARNING: 1a not written to conf file. Settings that start with a number are not permitted" ),
                                   "Neo4j did not warn about invalid numeric config variable `Neo4j_1a`.\n" +
                                   "Actual warnings were:\n"+errorLogs);
		}
	}

    @Test
    void testEnvVarsOverrideDefaultConfigurations() throws Exception
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion(new Neo4jVersion(3, 0, 0)),
                               "No neo4j-admin in 2.3: skipping neo4j-admin-conf-override test");
        File conf;
        Map<Setting,String> expectedValues = new HashMap<Setting,String>() {{
                put( Setting.MEMORY_PAGECACHE_SIZE, "1000m");
                put( Setting.MEMORY_HEAP_INITIALSIZE, "2000m");
                put( Setting.MEMORY_HEAP_MAXSIZE, "3000m");
                put( Setting.DIRECTORIES_LOGS, "/notdefaultlogs" );
                put( Setting.DIRECTORIES_DATA, "/notdefaultdata" );
        }};

        try(GenericContainer container = createContainer())
        {
            for(Setting s : expectedValues.keySet())
            {
                container.withEnv( confNames.get( s ).envName, expectedValues.get( s ) );
            }
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            conf = confMount.resolve( "neo4j.conf" ).toFile();
            makeContainerDumpConfig( container );
            container.start();
        }

        // now check the settings we set via env are in the new conf file
        Assertions.assertTrue( conf.exists(), "configuration file not written" );
        Assertions.assertTrue( conf.canRead(), "configuration file not readable for some reason?" );

        Map<String,String> configurations = parseConfFile( conf );
        for(Setting s : expectedValues.keySet())
        {
            Assertions.assertTrue( configurations.containsKey( confNames.get( s ).name ),
                               confNames.get( s ).name + " not set at all" );
            Assertions.assertEquals( expectedValues.get( s ),
                                 configurations.get( confNames.get( s ).name ),
                                 confNames.get( s ).name + " not overridden" );
        }
    }

    @Test
    void testReadsTheConfFile() throws Exception
    {
        Path debugLog;

        try(GenericContainer container = createContainer().waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD)))
        {
            //Mount /conf
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            Path logMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            debugLog = logMount.resolve("debug.log");
            SetContainerUser.nonRootUser( container );
            //Create ReadConf.conf file with the custom env variables
            Path confFile = confFolder.resolve( "ReadConf.conf" );
            Files.copy( confFile, confMount.resolve( "neo4j.conf" ) );
            //Start the container
            container.start();
        }

        //Check if the container reads the conf file
        assertConfigurationPresentInDebugLog( debugLog, confNames.get( Setting.MEMORY_HEAP_MAXSIZE ),
                                              "512", true );
    }

    @Test
    void testDefaultsConfigsAreSet() throws Exception
    {
        try(GenericContainer container = createContainer().waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD)))
        {
            //Mount /logs
            Path logMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            SetContainerUser.nonRootUser( container );
            //Start the container
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            Path debugLog = logMount.resolve( "debug.log" );

            String expectedDefaultListenAddress = "0.0.0.0";
            dbio.verifyConfigurationSetting("neo4j", PASSWORD, confNames.get( Setting.DEFAULT_LISTEN_ADDRESS), expectedDefaultListenAddress);
            assertConfigurationPresentInDebugLog(debugLog, confNames.get( Setting.DEFAULT_LISTEN_ADDRESS), expectedDefaultListenAddress, true);
            // test enterprise only default configurations are set
            if (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE) {
                String expectedTxAddress = container.getContainerId().substring(0, 12) + ":6000";
                String expectedRaftAddress = container.getContainerId().substring(0, 12) + ":7000";
                String expectedRoutingAddress = container.getContainerId().substring(0, 12) + ":7688";
                dbio.verifyConfigurationSetting("neo4j", PASSWORD, confNames.get( Setting.CLUSTER_TRANSACTION_ADDRESS), expectedTxAddress);
                assertConfigurationPresentInDebugLog(debugLog, confNames.get( Setting.CLUSTER_TRANSACTION_ADDRESS), expectedTxAddress,true);
                dbio.verifyConfigurationSetting("neo4j", PASSWORD, confNames.get( Setting.CLUSTER_RAFT_ADDRESS), expectedRaftAddress);
                assertConfigurationPresentInDebugLog(debugLog, confNames.get( Setting.CLUSTER_RAFT_ADDRESS), expectedRaftAddress,true);
                dbio.verifyConfigurationSetting("neo4j", PASSWORD, confNames.get( Setting.CLUSTER_ROUTING_ADDRESS), expectedRoutingAddress);
                assertConfigurationPresentInDebugLog(debugLog, confNames.get( Setting.CLUSTER_ROUTING_ADDRESS), expectedRoutingAddress,true);
            }
        }
    }

    @Test
    void testCommentedConfigsAreReplacedByDefaultOnes() throws Exception
    {
        File conf;
        try(GenericContainer container = createContainer())
        {
            //Mount /conf
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            conf = confMount.resolve( "neo4j.conf" ).toFile();
            SetContainerUser.nonRootUser( container );
            //Create ConfsReplaced.conf file in mounted folder
            Files.copy( confFolder.resolve( "ConfsReplaced.conf" ), conf.toPath() );
            makeContainerDumpConfig( container );
            //Start the container
            container.start();
        }
        //Read the config file to check if the config is set correctly
        Map<String,String> configurations = parseConfFile( conf );
        Assertions.assertTrue( configurations.containsKey( confNames.get( Setting.MEMORY_PAGECACHE_SIZE ).name ),
                               "conf settings not set correctly by docker-entrypoint" );
        Assertions.assertEquals( "512M",
                                 configurations.get(confNames.get( Setting.MEMORY_PAGECACHE_SIZE ).name),
                                 "conf settings not appended correctly by docker-entrypoint" );
    }

    @Test
    void testConfFileNotOverridenByDockerEntrypoint() throws Exception
    {
        File conf;
        try(GenericContainer container = createContainer())
        {
            //Mount /conf
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            conf = confMount.resolve( "neo4j.conf" ).toFile();
            SetContainerUser.nonRootUser( container );
            //Create ConfsNotOverridden.conf file
            Path confFile = confFolder.resolve( "ConfsNotOverridden.conf" );
            Files.copy( confFile, confMount.resolve( "neo4j.conf" ) );
            makeContainerDumpConfig( container );
            container.start();
        }

        //Read the config file to check if the config is not overriden
        Map<String, String> configurations = parseConfFile(conf);
        Assertions.assertTrue(configurations.containsKey(confNames.get( Setting.MEMORY_PAGECACHE_SIZE).name),
                              "conf settings not set correctly by docker-entrypoint");
        Assertions.assertEquals("1024M",
                                configurations.get(confNames.get( Setting.MEMORY_PAGECACHE_SIZE).name),
                                "docker-entrypoint has overridden custom setting set from user's conf");
    }

    @Test
    void testOldConfigNamesNotOverwrittenByDockerDefaults() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500),
                                "test only applicable after 5.0." );
        // at some point we will fully deprecate old config names, at which point we add an assume-version-less-than here
        Path logMount;
        Map<Setting,Configuration> oldConfMap = Configuration.getConfigurationNameMap( new Neo4jVersion( 4, 4, 0 ) );
        Map<Setting,String> expectedValues = new HashMap<Setting,String>() {{
            put( Setting.TXLOG_RETENTION_POLICY, "5M size" );
            put( Setting.MEMORY_PAGECACHE_SIZE, "100.00KiB" );
            put( Setting.DEFAULT_LISTEN_ADDRESS, "127.0.0.1" );
        }};
        if( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE)
        {
            expectedValues.put( Setting.CLUSTER_TRANSACTION_ADDRESS, "1.2.3.4:8000" );
            expectedValues.put( Setting.CLUSTER_RAFT_ADDRESS, "1.2.3.4:9000" );
        }

        try(GenericContainer container = createContainer())
        {
            logMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            SetContainerUser.nonRootUser( container );
            // set configurations using old config names
            for( Setting s : expectedValues.keySet() )
            {
                container.withEnv( oldConfMap.get( s ).envName, expectedValues.get( s ) );
            }
            // the container probably won't start nicely because the clustering settings are ivalid.
            // However we only care that the configs were read properly, so we can kill as soon as neo4j logs that it started.
            container.waitingFor( new LogMessageWaitStrategy()
                                          .withRegEx( ".*Remote interface available at http://localhost:7474/.*" )
                                          .withStartupTimeout( Duration.ofSeconds( 60 ) ));
            container.start();
        }
        for( Setting s : expectedValues.keySet() )
        {
            // configuration should be present in debug log under new configuration name
            assertConfigurationPresentInDebugLog(logMount.resolve( "debug.log" ),
                                                 confNames.get( s ),
                                                 expectedValues.get( s ),
                                                 true );
        }
    }

    @Test
    void testEnvVarsOverrideConfFile() throws Exception
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion(new Neo4jVersion(4, 2, 0)),
                               "test not applicable in versions before 4.2.");
        Path debugLog;
        try(GenericContainer container = createContainer()
                .withEnv(confNames.get(Setting.MEMORY_PAGECACHE_SIZE).envName, "512.00MiB")
                .waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD)))
        {
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            Path logMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            debugLog = logMount.resolve( "debug.log" );
            SetContainerUser.nonRootUser( container );
            //Create EnvVarsOverride.conf file
            Path confFile = confFolder.resolve("EnvVarsOverride.conf");
            Files.copy( confFile, confMount.resolve( "neo4j.conf" ) );
            //Start the container
            container.start();
        }
        assertConfigurationPresentInDebugLog(debugLog, confNames.get(Setting.MEMORY_PAGECACHE_SIZE), "512.00MiB", true );
    }

    @Test
    void testEnterpriseOnlyDefaultsDontOverrideConfFile() throws Exception
    {
        Assumptions.assumeTrue(TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                "This is testing only ENTERPRISE EDITION configs");

        try(GenericContainer container = createContainer().waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD)))
        {
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            Path logMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            // mount a configuration file with enterprise only settings already set
            Path confFile = confFolder.resolve( "EnterpriseOnlyNotOverwritten.conf" );
            Files.copy( confFile, confMount.resolve( "neo4j.conf" ) );

            //Start the container
            SetContainerUser.nonRootUser( container );
            container.start();
            //Read debug.log to check that cluster confs are set successfully
            assertConfigurationPresentInDebugLog( logMount.resolve( "debug.log" ),
                                                  confNames.get( Setting.CLUSTER_TRANSACTION_ADDRESS ),
                                                  "localhost:6060", true );
        }
    }

    @Test
    void testMountingMetricsFolderShouldNotSetConfInCommunity() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.COMMUNITY,
                                "Test only valid with community edition");

        try ( GenericContainer container = createContainer() )
        {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/metrics");
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            makeContainerDumpConfig( container );
            container.start();

            File conf = confMount.resolve( "neo4j.conf" ).toFile();
            Map<String, String> configurations = parseConfFile(conf);
            Assertions.assertFalse(configurations.containsKey(confNames.get( Setting.DIRECTORIES_METRICS ).name),
                                   "should not be setting any metrics configurations in community edition");
        }
    }

    @Test
    void testCommunityDoesNotHaveEnterpriseConfigs() throws Exception
    {
        Assumptions.assumeTrue(TestSettings.EDITION == TestSettings.Edition.COMMUNITY,
                               "This is testing only COMMUNITY EDITION configs");

        Path debugLog;
        try(GenericContainer container = createContainer()
                .withEnv(confNames.get(Setting.MEMORY_PAGECACHE_SIZE).envName, "512m")
                .waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD)))
        {
            //Mount /logs
			Path logMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            debugLog = logMount.resolve( "debug.log" );
            SetContainerUser.nonRootUser( container );
            //Start the container
            container.start();
        }

        //Read debug.log to check that cluster confs are not present
        assertConfigurationPresentInDebugLog( debugLog, confNames.get(Setting.CLUSTER_TRANSACTION_ADDRESS), "*", false );
    }

    @Test
    @Tag("BundleTest")
    void testSettingAppendsToConfFileWithoutEmptyLine_neo4jPlugins() throws Exception
    {
        String expectedPageCacheSize = "1000.00MiB";
        String pluginStr = "[\"apoc\"]";
        if(TestSettings.NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ))
        {
            pluginStr = "[\"apoc-core\"]";
        }

        try(GenericContainer container = createContainer().waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD)))
        {
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            Files.copy( confFolder.resolve( "NoNewline.conf" ), confMount.resolve( "neo4j.conf" ) );
            container.withEnv( Neo4jPluginEnv.get(), pluginStr );
            //Start the container
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            try
            {
                dbio.runCypherQuery( "neo4j", PASSWORD, "RETURN apoc.version()" );
            }
            catch( ClientException ex )
            {
                Assertions.fail("Did not load apoc plugin.", ex);
            }
            dbio.verifyConfigurationSetting( "neo4j",
                                             PASSWORD,
                                             confNames.get( Setting.MEMORY_PAGECACHE_SIZE ),
                                             expectedPageCacheSize);
        }
    }

    @Test
    void testSettingAppendsToConfFileWithoutEmptyLine_envSetting() throws Exception
    {
        String expectedHeapSize = "128.00MiB";
        String expectedPageCacheSize = "1000.00MiB";

        try(GenericContainer container = createContainer())
        {
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            Files.copy( confFolder.resolve( "NoNewline.conf" ), confMount.resolve( "neo4j.conf" ) );
            // set an env variable
            container.withEnv( confNames.get( Setting.MEMORY_HEAP_MAXSIZE ).envName, expectedHeapSize )
                     .waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD));
            //Start the container
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.verifyConfigurationSetting( "neo4j",
                                             PASSWORD,
                                             confNames.get( Setting.MEMORY_HEAP_MAXSIZE ),
                                             expectedHeapSize);
            dbio.verifyConfigurationSetting( "neo4j",
                                             PASSWORD,
                                             confNames.get( Setting.MEMORY_PAGECACHE_SIZE ),
                                             expectedPageCacheSize);
        }
    }

    @Test
    void testApocEnvVarsAreWrittenToApocConf() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,3, 0 ) ),
                                "APOC conf not present before 5.0 and this bug wasn't fixed before 5.3.");

        Path confMount;
        try(GenericContainer container = createContainer())
        {
            container.withEnv( confNames.get( Setting.APOC_EXPORT_FILE_ENABLED ).envName, "true" );
            container.withEnv( Neo4jPluginEnv.get(), "[\"apoc\"]" );
            confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            makeContainerDumpConfig( container );
            container.start();
        }
        // there's no way to verify that APOC configurations have been set by querying neo4j or the debug log,
        // so the only verification we can do is check that neo4j started ok and that there is an apoc.conf dumped.
        File apocConf = confMount.resolve( "apoc.conf" ).toFile();
        Assertions.assertTrue( apocConf.exists(), "Did not create an apoc.conf to contain the apoc settings." );
        Map<String,String> actualApocSettings = parseConfFile( apocConf );
        Assertions.assertTrue(actualApocSettings.containsKey(confNames.get(Setting.APOC_EXPORT_FILE_ENABLED).name),
                              "APOC setting not added to apoc.conf");
        Assertions.assertEquals("true",
                                actualApocSettings.get(confNames.get( Setting.APOC_EXPORT_FILE_ENABLED).name),
                                "Incorrect value written for APOC setting");
    }

    @Test
    void testShellExpansionAvoided() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400),
                                "test only applicable to 4.0 and beyond." );

        Path confMount;
        try(GenericContainer container = createContainer()
                .withEnv(confNames.get(Setting.SECURITY_PROCEDURES_UNRESTRICTED).envName, "*"))
        {
			confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            makeContainerDumpConfig( container );
            container.start();
        }
        File conf = confMount.resolve( "neo4j.conf" ).toFile();
        Map<String, String> configurations = parseConfFile(conf);
        Assertions.assertTrue(configurations.containsKey(confNames.get( Setting.SECURITY_PROCEDURES_UNRESTRICTED).name),
                              "configuration not set from env var");
        Assertions.assertEquals("*",
                configurations.get(confNames.get( Setting.SECURITY_PROCEDURES_UNRESTRICTED).name),
                "Configuration value should be *. If it's not docker-entrypoint.sh probably evaluated it as a glob expression.");
    }
}
