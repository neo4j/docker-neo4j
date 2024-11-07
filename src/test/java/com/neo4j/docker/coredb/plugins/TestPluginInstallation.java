package com.neo4j.docker.coredb.plugins;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.neo4j.docker.coredb.configurations.Configuration;
import com.neo4j.docker.coredb.configurations.Setting;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HttpServerTestExtension;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static com.neo4j.docker.utils.TestSettings.NEO4J_VERSION;


public class TestPluginInstallation
{
    private static final String DB_USER = "neo4j";
    private static final String DB_PASSWORD = "qualityPassword";

    private final Logger log = LoggerFactory.getLogger( TestPluginInstallation.class );
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
    @RegisterExtension
    public HttpServerTestExtension httpServer = new HttpServerTestExtension();
    StubPluginHelper stubPluginHelper = new StubPluginHelper(httpServer);


    private GenericContainer createContainerWithTestingPlugin(boolean asCurrentUser)
    {
        Testcontainers.exposeHostPorts( httpServer.PORT );
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );

        container.withEnv( "NEO4J_AUTH", DB_USER + "/" + DB_PASSWORD )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( "NEO4J_DEBUG", "yes" )
                 .withEnv( Neo4jPluginEnv.get(), "[\"" + stubPluginHelper.PLUGIN_ENV_NAME + "\"]" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( WaitStrategies.waitForNeo4jReady( DB_PASSWORD));
        if(asCurrentUser) SetContainerUser.nonRootUser( container );
        return container;
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource( booleans = {true, false} )
    public void testPluginLoads(boolean asCurrentUser) throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginForVersion(pluginsDir, NEO4J_VERSION);
        try ( GenericContainer container = createContainerWithTestingPlugin(asCurrentUser) )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
        }
    }

    @Test
    public void test_NEO4JLABS_PLUGIN_envWorksIn5() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ),
                                "NEO4JLABS_PLUGIN backwards compatibility does not need checking pre 5.x" );

        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginForVersion(pluginsDir, NEO4J_VERSION);
        try ( GenericContainer container = createContainerWithTestingPlugin(false) )
        {
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_5X, "" );
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_4X, "[\"_testing\"]" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
        }
    }

    @Test
    public void test_NEO4J_PLUGIN_envWorksIn44() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 4, 18 ) ),
                                "NEO4JLABS_PLUGIN did not work in 4.4 before 4.4.18" );
        Assumptions.assumeTrue( NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ),
                                "Only checking forwards compatibility in 4.4" );

        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginForVersion(pluginsDir, NEO4J_VERSION);
        try ( GenericContainer container = createContainerWithTestingPlugin(false) )
        {
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_5X, "[\"_testing\"]" );
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_4X, "" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
        }
    }

    @Test
    public void testPluginConfigurationDoesNotOverrideUserSetValues() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        Configuration securityProcedures = Configuration.getConfigurationNameMap()
                .get( Setting.SECURITY_PROCEDURES_UNRESTRICTED );
        stubPluginHelper.createStubPluginForVersion(pluginsDir, NEO4J_VERSION);
        try ( GenericContainer container = createContainerWithTestingPlugin(false) )
        {
            container.withEnv( securityProcedures.envName, "foo" );
            container.start();
            // When we connect to the database with the plugin
            // Check that the config remains as set by our env var and is not overridden by the plugin defaults
            DatabaseIO db = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
            db.verifyConfigurationSetting( DB_USER, DB_PASSWORD,
                                           securityProcedures,
                                           "foo",
                                           "neo4j config should not be overridden by plugin" );
        }
    }

    @Test
    void invalidPluginNameShouldGiveOptionsAndError()
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_440 ) );
        try ( GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID ) )
        {
            // if we try to set a plugin that doesn't exist
            container.withEnv( Neo4jPluginEnv.get(), "[\"notarealplugin\"]" )
                     .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withLogConsumer( new Slf4jLogConsumer( log ) );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 30 ) );
            Assertions.assertThrows( ContainerLaunchException.class, container::start );
            // the container should output a helpful message and quit
            String stdout = container.getLogs();
            Assertions.assertTrue( stdout.contains( "\"notarealplugin\" is not a known Neo4j plugin. Options are:" ) );
            Assertions.assertFalse( stdout.contains( "_testing" ), "Fake _testing plugin is exposed." );
        }
    }

    @Test
    void invalidPluginNameShouldGiveOptionsAndError_mulitpleplugins()
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_440 ) );
        try ( GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID ) )
        {
            // if we try to set a plugin that doesn't exist
            container.withEnv( Neo4jPluginEnv.get(), "[\"apoc\", \"notarealplugin\"]" )
                     .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withLogConsumer( new Slf4jLogConsumer( log ) );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 30 ) );
            Assertions.assertThrows( ContainerLaunchException.class, container::start );
            // the container should output a helpful message and quit
            String stdout = container.getLogs();
            Assertions.assertTrue( stdout.contains( "\"notarealplugin\" is not a known Neo4j plugin. Options are:" ) );
            Assertions.assertFalse( stdout.contains( StubPluginHelper.PLUGIN_ENV_NAME), "Fake _testing plugin is exposed." );
        }
    }

    @Test
    public void testBrokenVersionsJsonGivesWarning() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_440 ) );
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        // create a versions.json that DOES NOT contain the current neo4j version in its mapping
        stubPluginHelper.createStubPluginForVersion(pluginsDir, new Neo4jVersion(50,0,0));
        try ( GenericContainer container = createContainerWithTestingPlugin(true) )
        {
            container.start();
            String startupErrors = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue( startupErrors.contains( "No compatible \"_testing\" plugin found for Neo4j " + NEO4J_VERSION ),
                                   "Did not error about plugin compatibility." );
            DatabaseIO db = new DatabaseIO( container );
            // make sure plugin did not load
            List<Record> procedures = db.runCypherQuery( DB_USER, DB_PASSWORD,
                                                         "SHOW PROCEDURES YIELD name, signature RETURN name, signature" );
            Assertions.assertFalse( procedures.stream()
                                              .anyMatch( x -> x.get( "name" ).asString()
                                                               .equals( "com.neo4j.docker.test.myplugin.defaultValues" ) ),
                                    "Incompatible test plugin was loaded." );
        }
    }

    //@Disabled("Test is flaky for unknown reasons. Needs further investigation.")
    @Test
    void testMissingVersionsJsonGivesWarning()
    {
        Configuration securityProcedures = Configuration.getConfigurationNameMap().get(Setting.SECURITY_PROCEDURES_UNRESTRICTED);
        // make double sure there are no versions.json files being served.
        httpServer.unregisterEndpoint("/versions.json");
        try ( Network net = Network.newNetwork();
              GenericContainer container = createContainerWithTestingPlugin(false)
                      .withNetwork(net) ) {
            container.start();
            String startupErrors = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue(
                    startupErrors.contains( "could not query http://host.testcontainers.internal:3000/versions.json for plugin compatibility information" ),
                    "Did not error about missing versions.json. Actual errors:\n\"" + startupErrors + "\"" );
            Assertions.assertFalse( startupErrors.contains( "No compatible \"_testing\" plugin found for Neo4j " + NEO4J_VERSION ),
                                    "Should not have errored about incompatible versions in versions.json" );
            // make sure plugin did not load
            DatabaseIO db = new DatabaseIO( container );
            List<Record> procedures = db.runCypherQuery( DB_USER, DB_PASSWORD,
                                                         "SHOW PROCEDURES YIELD name, signature RETURN name, signature" );
            Assertions.assertFalse( procedures.stream()
                                              .anyMatch( x -> x.get( "name" ).asString()
                                                               .equals( "com.neo4j.docker.test.myplugin.defaultValues" ) ),
                                    "Incompatible test plugin was loaded." );
            // make sure configuration did not set
            String securityConf = db.getConfigurationSettingAsString(DB_USER, DB_PASSWORD, securityProcedures);
            Assertions.assertFalse(securityConf.contains("com.neo4j.docker.neo4jserver.plugins.*"),
                    "Test plugin configuration setting was set, even though the plugin did not load.");
        }
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource( booleans = {true, false} )
    public void testPlugin_originalEntrypointLocation(boolean asCurrentUser) throws Exception
    {
        // Older versions of Neo4j had docker-entrypoint.sh in / rather than /startup and sometimes
        // users use the old entrypoint location. This apparently caused problems loading plugins.
        Assumptions.assumeTrue( NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ),
                                "/docker-entrypoint.sh is permanently moved from 5.0 onwards" );
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginForVersion(pluginsDir, NEO4J_VERSION);
        try ( GenericContainer container = createContainerWithTestingPlugin(asCurrentUser) )
        {
            container.withCreateContainerCmdModifier(
                    (Consumer<CreateContainerCmd>) cmd -> cmd.withEntrypoint( "/docker-entrypoint.sh", "neo4j" ) );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
        }
    }

    @ParameterizedTest( name = "as_current_user_{0}" )
    @ValueSource( booleans = {true, false} )
    void testPluginIsMovedToMountedFolderAndIsLoadedCorrectly( boolean asCurrentUser ) throws Exception
    {
        try ( GenericContainer container = createContainerWithTestingPlugin(asCurrentUser))
        {
            Path pluginsFolder = temporaryFolderManager.createFolderAndMountAsVolume(container, "/plugins");
            stubPluginHelper.createStubPluginForVersion(pluginsFolder, NEO4J_VERSION);
            container.start();
            Assertions.assertTrue( pluginsFolder.resolve( StubPluginHelper.PLUGIN_ENV_NAME +".jar" ).toFile().exists(),
                    "Did not find _testing.jar in plugins folder");

            DatabaseIO databaseIO = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded(databaseIO, DB_USER, DB_PASSWORD);
        }
    }
}
