package com.neo4j.docker.coredb.plugins;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.google.gson.Gson;
import com.neo4j.docker.coredb.configurations.Configuration;
import com.neo4j.docker.coredb.configurations.Setting;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileHttpHandler;
import com.neo4j.docker.utils.HttpServerTestExtension;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.WaitStrategies;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.neo4j.driver.Record;

import static com.neo4j.docker.utils.TestSettings.NEO4J_VERSION;


public class TestPluginInstallation
{
    private static final String DB_USER = "neo4j";
    private static final String DB_PASSWORD = "qualityPassword";
    private static final String PLUGIN_JAR = "myPlugin.jar";

    private static final Logger log = LoggerFactory.getLogger( TestPluginInstallation.class );
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
    @RegisterExtension
    public HttpServerTestExtension httpServer = new HttpServerTestExtension();

    private class VersionsJsonEntry
    {
        String neo4j;
        String jar;
        String _testing;

        VersionsJsonEntry( String neo4j )
        {
            this.neo4j = neo4j;
            this._testing = "SNAPSHOT";
            this.jar = "http://host.testcontainers.internal:3000/" + PLUGIN_JAR;
        }

        VersionsJsonEntry( String neo4j, String jar )
        {
            this.neo4j = neo4j;
            this._testing = "SNAPSHOT";
            this.jar = "http://host.testcontainers.internal:3000/" + jar;
        }
    }

    private GenericContainer createContainerWithTestingPlugin()
    {
        Testcontainers.exposeHostPorts( httpServer.PORT );
        GenericContainer container = new GenericContainer( TestSettings.NEO4J_IMAGE_ID);

        container.withEnv( "NEO4J_AUTH", DB_USER + "/" + DB_PASSWORD )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( "NEO4J_DEBUG", "yes" )
                 .withEnv( Neo4jPluginEnv.get(), "[\"_testing\"]" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( WaitStrategies.waitForNeo4jReady( DB_PASSWORD));
        SetContainerUser.nonRootUser( container );
        return container;
    }

    private GenericContainer setupContainerWithUser( boolean asCurrentUser )
    {
        log.info( "Running as user {}, {}",
                  asCurrentUser ? "non-root" : "root" );

        GenericContainer container = new GenericContainer( TestSettings.NEO4J_IMAGE_ID);
        container.withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( "NEO4J_AUTH", "none" )
                 .waitingFor( WaitStrategies.waitForNeo4jReady( "none" ) );
        if ( asCurrentUser )
        {
            SetContainerUser.nonRootUser( container );
        }

        return container;
    }

    private File createTestVersionsJson( Path destinationFolder, String version ) throws Exception
    {
        List<VersionsJsonEntry> jsonEntry = Collections.singletonList( new VersionsJsonEntry( version ) );
        Gson jsonBuilder = new Gson();
        String jsonStr = jsonBuilder.toJson( jsonEntry );

        File outputJsonFile = destinationFolder.resolve( "versions.json" ).toFile();
        java.nio.file.Files.writeString( outputJsonFile.toPath(), jsonStr );
        return outputJsonFile;
    }

    private File createTestVersionsJson( Path destinationFolder, Map<String,String> versionAndJar ) throws Exception
    {
        List<VersionsJsonEntry> jsonEntries = versionAndJar.keySet()
                                                           .stream()
                                                           .map( key -> new VersionsJsonEntry( key, versionAndJar.get( key ) ) )
                                                           .collect( Collectors.toList() );
        Gson jsonBuilder = new Gson();
        String jsonStr = jsonBuilder.toJson( jsonEntries );

        File outputJsonFile = destinationFolder.resolve( "versions.json" ).toFile();
        java.nio.file.Files.writeString( outputJsonFile.toPath(), jsonStr );
        return outputJsonFile;
    }

    private void setupTestPlugin( File versionsJson ) throws Exception
    {
        File myPluginJar = new File( getClass().getClassLoader().getResource( "testplugin/" + PLUGIN_JAR ).toURI() );

        httpServer.registerHandler( versionsJson.getName(), new HostFileHttpHandler( versionsJson, "application/json" ) );
        httpServer.registerHandler( PLUGIN_JAR, new HostFileHttpHandler( myPluginJar, "application/java-archive" ) );
    }

    private void verifyTestPluginLoaded( DatabaseIO db )
    {
        // when we check the list of installed procedures...
        String listProceduresCypherQuery = NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 3, 0 ) ) ?
                                           "SHOW PROCEDURES YIELD name, signature RETURN name, signature" :
                                           "CALL dbms.procedures() YIELD name, signature RETURN name, signature";
        List<Record> procedures = db.runCypherQuery( DB_USER, DB_PASSWORD, listProceduresCypherQuery );
        // Then the procedure from the test plugin should be listed
        Assertions.assertTrue( procedures.stream()
                                         .anyMatch( x -> x.get( "name" ).asString()
                                                          .equals( "com.neo4j.docker.test.myplugin.defaultValues" ) ),
                               "Missing procedure provided by our plugin" );

        // When we call the procedure from the plugin
        List<Record> pluginResponse = db.runCypherQuery( DB_USER, DB_PASSWORD,
                                                         "CALL com.neo4j.docker.test.myplugin.defaultValues" );

        // Then we get the response we expect
        Assertions.assertEquals( 1, pluginResponse.size(), "Our procedure should only return a single result" );
        Record record = pluginResponse.get( 0 );

        String message = "Result from calling our procedure doesnt match our expectations";
        Assertions.assertEquals( "a string", record.get( "string" ).asString(), message );
        Assertions.assertEquals( 42L, record.get( "integer" ).asInt(), message );
        Assertions.assertEquals( 3.14d, record.get( "aFloat" ).asDouble(), 0.000001, message );
        Assertions.assertEquals( true, record.get( "aBoolean" ).asBoolean(), message );
    }

    @Test
    public void testPluginLoads() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder( "plugin" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            verifyTestPluginLoaded( db );
        }
    }

    @Test
    public void test_NEO4JLABS_PLUGIN_envWorksIn5() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ),
                                "NEO4JLABS_PLUGIN backwards compatibility does not need checking pre 5.x" );

        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-backcompat-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_5X, "" );
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_4X, "[\"_testing\"]" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            verifyTestPluginLoaded( db );
        }
    }

    @Test
    public void test_NEO4J_PLUGIN_envWorksIn44() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 4, 18 ) ),
                                "NEO4JLABS_PLUGIN did not work in 4.4 before 4.4.18" );
        Assumptions.assumeTrue( NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ),
                                "Only checking forwards compatibility in 4.4" );

        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-forwardcompat-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_5X, "[\"_testing\"]" );
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_4X, "" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            verifyTestPluginLoaded( db );
        }
    }

    @Test
    public void testPluginConfigurationDoesNotOverrideUserSetValues() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-noOverride-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        Configuration securityProcedures = Configuration.getConfigurationNameMap()
                                                        .get( Setting.SECURITY_PROCEDURES_UNRESTRICTED );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            // When we set a config value explicitly
            container.withEnv( securityProcedures.envName, "foo" );
            // When we start the neo4j docker container
            container.start();

            // When we connect to the database with the plugin
            // Check that the config remains as set by our env var and is not overridden by the plugin defaults
            DatabaseIO db = new DatabaseIO( container );
            verifyTestPluginLoaded( db );
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
        try ( GenericContainer container = new GenericContainer( TestSettings.NEO4J_IMAGE_ID) )
        {
            // if we try to set a plugin that doesn't exist
            container.withEnv( Neo4jPluginEnv.get(), "[\"notarealplugin\"]" )
                     .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withLogConsumer( new Slf4jLogConsumer( log ) );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds(30));
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
        try ( GenericContainer container = new GenericContainer( TestSettings.NEO4J_IMAGE_ID) )
        {
            // if we try to set a plugin that doesn't exist
            container.withEnv( Neo4jPluginEnv.get(), "[\"apoc\", \"notarealplugin\"]" )
                     .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withLogConsumer( new Slf4jLogConsumer( log ) );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds(30));
            Assertions.assertThrows( ContainerLaunchException.class, container::start );
            // the container should output a helpful message and quit
            String stdout = container.getLogs();
            Assertions.assertTrue( stdout.contains( "\"notarealplugin\" is not a known Neo4j plugin. Options are:" ) );
            Assertions.assertFalse( stdout.contains( "_testing" ), "Fake _testing plugin is exposed." );
        }
    }

    @Test
    public void testBrokenVersionsJsonGivesWarning() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_440 ) );
        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-broken-versionsjson" );
        // create a versions.json that DOES NOT contain the current neo4j version in its mapping
        File versionsJson = createTestVersionsJson( pluginsDir, "50.0.0" );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
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

    @Disabled("Test is flaky for unknown reasons. Needs further investigation.")
    @Test
    void testMissingVersionsJsonGivesWarning()
    {
        // make double sure there are no versions.json files being served.
        httpServer.unregisterEndpoint("/versions.json");
        try ( GenericContainer container = createContainerWithTestingPlugin() ) {
            container.start();
            String startupErrors = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue(startupErrors.contains("could not query http://host.testcontainers.internal:3000/versions.json for plugin compatibility information"),
                    "Did not error about missing versions.json. Actual errors:\n\""+startupErrors+"\"");
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
        }
    }

    @Test
    void testSemanticVersioningPlugin_catchesMatchWithX() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-semverMatchesX-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.getBranch() + ".x" );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            verifyTestPluginLoaded( db );
        }
    }

    @Test
    void testSemanticVersioningPlugin_catchesMatchWithStar() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-semverMatchesStar-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.getBranch() + ".*" );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            verifyTestPluginLoaded( db );
        }
    }

    @Test
    void testSemanticVersioningPlugin_prefersExactMatch() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-semverPrefersExact-" );
        File versionsJson = createTestVersionsJson( pluginsDir, new HashMap<String,String>()
        {{
            put( NEO4J_VERSION.toString(), PLUGIN_JAR );
            put( NEO4J_VERSION.getBranch() + ".x", "notareal.jar" );
            put( NEO4J_VERSION.major + ".x.x", "notareal.jar" );
        }} );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            // if semver did not pick exact version match then it will load a non-existent plugin instead and fail.
            verifyTestPluginLoaded( db );
        }
    }

    @Test
    public void testPlugin_originalEntrypointLocation() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ),
                                "/docker-entrypoint.sh is permanently moved from 5.0 onwards" );
        Path pluginsDir = temporaryFolderManager.createFolder( "plugin-oldEntrypoint-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.getBranch() + ".x" );
        setupTestPlugin( versionsJson );
        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.withCreateContainerCmdModifier(
                    (Consumer<CreateContainerCmd>) cmd -> cmd.withEntrypoint( "/docker-entrypoint.sh", "neo4j" ) );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            verifyTestPluginLoaded( db );
        }
    }

    @Test
    void testSemanticVersioningLogic() throws Exception
    {
        String major = Integer.toString( NEO4J_VERSION.major );
        String minor = Integer.toString( NEO4J_VERSION.minor );

        // testing common neo4j name variants
        List<String> neo4jVersions = new ArrayList<String>()
        {{
            add( NEO4J_VERSION.toString() );
            add( NEO4J_VERSION.toString() + "-drop01.1" );
            add( NEO4J_VERSION.toString() + "-drop01" );
            add( NEO4J_VERSION.toString() + "-beta04" );
        }};

        List<String> matchingCases = new ArrayList<String>()
        {{
            add( NEO4J_VERSION.toString() );
            add( major + '.' + minor + ".x" );
            add( major + '.' + minor + ".*" );
        }};

        List<String> nonMatchingCases = new ArrayList<String>()
        {{
            add( (NEO4J_VERSION.major + 1) + '.' + minor + ".x" );
            add( (NEO4J_VERSION.major - 1) + '.' + minor + ".x" );
            add( major + '.' + (NEO4J_VERSION.minor + 1) + ".x" );
            add( major + '.' + (NEO4J_VERSION.minor - 1) + ".x" );
            add( (NEO4J_VERSION.major + 1) + '.' + minor + ".*" );
            add( (NEO4J_VERSION.major - 1) + '.' + minor + ".*" );
            add( major + '.' + (NEO4J_VERSION.minor + 1) + ".*" );
            add( major + '.' + (NEO4J_VERSION.minor - 1) + ".*" );
        }};

        // Asserting every test case means that if there's a failure, all further tests won't run.
        // Instead we're running all tests and saving any failed cases for reporting at the end of the test.
        List<String> failedTests = new ArrayList<String>();

        try ( GenericContainer container = createContainerWithTestingPlugin() )
        {
            container.withEnv( Neo4jPluginEnv.get(), "" ); // don't need the _testing plugin for this
            container.start();

            String semverQuery = "echo \"{\\\"neo4j\\\":\\\"%s\\\"}\" | " +
                                 "jq -L/startup --raw-output \"import \\\"semver\\\" as lib; " +
                                 ".neo4j | lib::semver(\\\"%s\\\")\"";
            for ( String neoVer : neo4jVersions )
            {
                for ( String ver : matchingCases )
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, ver, neoVer ) );
                    if ( !out.getStdout().trim().equals( "true" ) )
                    {
                        failedTests.add( String.format( "%s should match %s but did not", ver, neoVer ) );
                    }
                }
                for ( String ver : nonMatchingCases )
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, ver, neoVer ) );
                    if ( !out.getStdout().trim().equals( "false" ) )
                    {
                        failedTests.add( String.format( "%s should NOT match %s but did", ver, neoVer ) );
                    }
                }
            }
            if ( failedTests.size() > 0 )
            {
                Assertions.fail( failedTests.stream().collect( Collectors.joining( "\n" ) ) );
            }
        }
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource( booleans = {true, false} )
    void testPluginIsMovedToMountedFolderAndIsLoadedCorrectly( boolean asCurrentUser ) throws Exception
    {
        try ( GenericContainer container = setupContainerWithUser( asCurrentUser ) )
        {
            var pluginsFolder = temporaryFolderManager.createFolderAndMountAsVolume(container, "/plugins");
            container.withEnv( "NEO4J_PLUGINS", "[\"bloom\"]" );
            container.start();
            Assertions.assertTrue( pluginsFolder.resolve( "bloom.jar" ).toFile().exists(), "Did not find bloom.jar in plugins folder" );

            DatabaseIO databaseIO = new DatabaseIO( container );
            var result = databaseIO.runCypherQuery( "neo4j", "none", "SHOW PROCEDURES YIELD name, description, signature WHERE name STARTS WITH 'bloom'" );
            Assertions.assertFalse( result.isEmpty(), "Bloom procedures not found in neo4j installation" );
        }
    }
}
