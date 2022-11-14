package com.neo4j.docker.neo4jserver.plugins;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.google.gson.Gson;
import com.neo4j.docker.utils.*;
import java.time.Duration;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.google.common.io.Files;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.neo4j.driver.Record;

import static com.neo4j.docker.utils.TestSettings.NEO4J_VERSION;

@EnableRuleMigrationSupport
public class TestPluginInstallation
{
    private static final String DB_USER = "neo4j";
    private static final String DB_PASSWORD = "qualityPassword";
    private static final String PLUGIN_JAR = "myPlugin.jar";

    private static final Logger log = LoggerFactory.getLogger( TestPluginInstallation.class );

    @Rule
    public HttpServerRule httpServer = new HttpServerRule();

//    @BeforeAll
//    public static void ensureNotARMArchitecture()
//    {
//        // These tests make use of a TestContainers feature that means that if you serve something locally on the
//        // host machine, it is accessible from a container at the address http://host.testcontainers.internal
//        // This feature does not seem to work on ARM64 machines. I've created a bug report here 27/01/2022:
//        // https://github.com/testcontainers/testcontainers-java/issues/4956
//        //
//        // For now, we skip these tests on ARM until there is a fix or workaround.
//        Assumptions.assumeTrue( System.getProperty("os.arch").equals( "amd64" ),
//                                "Plugin tests can only run on amd64 machines at the moment" );
//    }

    private GenericContainer createContainerWithTestingPlugin()
    {
        Testcontainers.exposeHostPorts( httpServer.PORT );
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );

        container.withEnv( "NEO4J_AUTH", DB_USER+"/"+ DB_PASSWORD)
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withEnv( Neo4jPluginEnv.get(), "[\"_testing\"]" )
                .withExposedPorts( 7474, 7687 )
                .withLogConsumer( new Slf4jLogConsumer( log ) );
        StartupDetector.makeContainerWaitForDatabaseReady(container, DB_USER, DB_PASSWORD, "neo4j",
                Duration.ofSeconds(60));
        SetContainerUser.nonRootUser( container );
        return container;
    }

    private File createTestVersionsJson(Path destinationFolder, String... versions) throws Exception
    {
        List<VersionsJsonEntry> entryList = Arrays.stream( versions )
                                                  .map( VersionsJsonEntry::new )
                                                  .collect( Collectors.toList() );
        Gson jsonBuilder = new Gson();
        String jsonStr = jsonBuilder.toJson( entryList );

        File outputJsonFile = destinationFolder.resolve( "versions.json" ).toFile();
        Files.write( jsonStr, outputJsonFile, StandardCharsets.UTF_8 );
        return outputJsonFile;
    }

    private void setupTestPlugin( Path pluginsDir, File versionsJson ) throws Exception
    {
       File myPluginJar = pluginsDir.resolve( PLUGIN_JAR ).toFile();
        new JarBuilder().createJarFor( myPluginJar, ExampleNeo4jPlugin.class, ExampleNeo4jPlugin.PrimitiveOutput.class );

        httpServer.registerHandler( versionsJson.getName(), new HostFileHttpHandler( versionsJson, "application/json" ) );
        httpServer.registerHandler( PLUGIN_JAR, new HostFileHttpHandler( myPluginJar, "application/java-archive" ) );
    }

    private void verifyTestPluginLoaded(DatabaseIO db)
    {
        // when we check the list of installed procedures...
        String listProceduresCypherQuery = NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 3, 0 ) ) ?
                                           "SHOW PROCEDURES YIELD name, signature RETURN name, signature" :
                                           "CALL dbms.procedures() YIELD name, signature RETURN name, signature";
        List<Record> procedures = db.runCypherQuery( DB_USER, DB_PASSWORD, listProceduresCypherQuery );
        // Then the procedure from the test plugin should be listed
        Assertions.assertTrue( procedures.stream()
                                .anyMatch(x -> x.get( "name" ).asString()
                                    .equals( "com.neo4j.docker.neo4jserver.plugins.defaultValues" ) ),
                "Missing procedure provided by our plugin" );

        // When we call the procedure from the plugin
        List<Record> pluginResponse = db.runCypherQuery(DB_USER, DB_PASSWORD,
                "CALL com.neo4j.docker.neo4jserver.plugins.defaultValues" );

        // Then we get the response we expect
        Assertions.assertEquals(1, pluginResponse.size(), "Our procedure should only return a single result");
        Record record = pluginResponse.get(0);

        String message = "Result from calling our procedure doesnt match our expectations";
        Assertions.assertEquals( "a string", record.get( "string" ).asString(), message );
        Assertions.assertEquals( 42L, record.get( "integer" ).asInt(), message );
        Assertions.assertEquals( 3.14d, record.get( "aFloat" ).asDouble(), 0.000001, message );
        Assertions.assertEquals( true, record.get( "aBoolean" ).asBoolean(), message );
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "NEO4J_DOCKER_TESTS_TestPluginInstallation", matches = "ignore")
    public void testPlugin() throws Exception
    {
        Path pluginsDir = HostFileSystemOperations.createTempFolder( "plugin-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        setupTestPlugin( pluginsDir, versionsJson );
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
        }
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "NEO4J_DOCKER_TESTS_TestPluginInstallation", matches = "ignore")
    public void testPlugin_50BackwardsCompatibility() throws Exception
    {
        Assumptions.assumeTrue( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ),
                                "NEO4JLABS_PLUGIN backwards compatibility does not need checking");
        Path pluginsDir = HostFileSystemOperations.createTempFolder( "plugin-backcompat-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        setupTestPlugin( pluginsDir, versionsJson );
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_5X, "" );
            container.withEnv( Neo4jPluginEnv.PLUGIN_ENV_4X, "[\"_testing\"]" );
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
        }
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "NEO4J_DOCKER_TESTS_TestPluginInstallation", matches = "ignore")
    public void testPluginConfigurationDoesNotOverrideUserSetValues() throws Exception
    {
        Path pluginsDir = HostFileSystemOperations.createTempFolder( "plugin-noOverride-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        setupTestPlugin( pluginsDir, versionsJson );
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            // When we set a config value explicitly
            container.withEnv("NEO4J_dbms_security_procedures_unrestricted", "foo" );
            // When we start the neo4j docker container
            container.start();

            // When we connect to the database with the plugin
            // Check that the config remains as set by our env var and is not overridden by the plugin defaults
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
            db.verifyConfigurationSetting( DB_USER, DB_PASSWORD,
                                           "dbms.security.procedures.unrestricted",
                                           "foo",
                                           "neo4j config should not be overridden by plugin");
        }
    }

    @Test
    void testSemanticVersioningPlugin_catchesMatchWithX() throws Exception
    {
        Path pluginsDir = HostFileSystemOperations.createTempFolder( "plugin-semverMatchesX-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.getBranch()+".x");
        setupTestPlugin( pluginsDir, versionsJson );
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
        }
    }

    @Test
    void testSemanticVersioningPlugin_catchesMatchWithStar() throws Exception
    {
        Path pluginsDir = HostFileSystemOperations.createTempFolder( "plugin-semverMatchesStar-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.getBranch()+".*");
        setupTestPlugin( pluginsDir, versionsJson );
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
        }
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "NEO4J_DOCKER_TESTS_TestPluginInstallation", matches = "ignore")
    public void testPlugin_originalEntrypointLocation() throws Exception
    {
        Assumptions.assumeFalse( NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ),
                                 "/docker-entrypoint.sh is permanently moved from 5.0 onwards");
        Path pluginsDir = HostFileSystemOperations.createTempFolder( "plugin-oldEntrypoint-" );
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.getBranch()+".x" );
        setupTestPlugin( pluginsDir, versionsJson );
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.withCreateContainerCmdModifier(
                    (Consumer<CreateContainerCmd>) cmd -> cmd.withEntrypoint( "/docker-entrypoint.sh", "neo4j" ) );
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
        }
    }



    @Test
    void testSemanticVersioningLogic() throws Exception
    {
        String major = Integer.toString(NEO4J_VERSION.major);
        String minor = Integer.toString(NEO4J_VERSION.minor);

        // testing common neo4j name variants
        List<String> neo4jVersions = new ArrayList<String>() {{
            add(NEO4J_VERSION.toString());
            add(NEO4J_VERSION.toString()+"-drop01.1");
            add(NEO4J_VERSION.toString()+"-drop01");
            add(NEO4J_VERSION.toString()+"-beta04");
        }};

        List<String> matchingCases = new ArrayList<String>() {{
            add( NEO4J_VERSION.toString() );
            add( major+'.'+minor+".x" );
            add( major+'.'+minor+".*" );
        }};

        List<String> nonMatchingCases = new ArrayList<String>() {{
            add( (NEO4J_VERSION.major+1)+'.'+minor+".x" );
            add( (NEO4J_VERSION.major-1)+'.'+minor+".x" );
            add( major+'.'+(NEO4J_VERSION.minor+1)+".x" );
            add( major+'.'+(NEO4J_VERSION.minor-1)+".x" );
            add( (NEO4J_VERSION.major+1)+'.'+minor+".*" );
            add( (NEO4J_VERSION.major-1)+'.'+minor+".*" );
            add( major+'.'+(NEO4J_VERSION.minor+1)+".*" );
            add( major+'.'+(NEO4J_VERSION.minor-1)+".*" );
        }};

        // Asserting every test case means that if there's a failure, all further tests won't run.
        // Instead we're running all tests and saving any failed cases for reporting at the end of the test.
        List<String> failedTests = new ArrayList<String>();


        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.withEnv( Neo4jPluginEnv.get(), "" ); // don't need the _testing plugin for this
            container.start();

            String semverQuery = "echo \"{\\\"neo4j\\\":\\\"%s\\\"}\" | " +
                                 "jq -L/startup --raw-output \"import \\\"semver\\\" as lib; " +
                                 ".neo4j | lib::semver(\\\"%s\\\")\"";
            for(String neoVer : neo4jVersions)
            {
                for(String ver : matchingCases)
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, ver, neoVer) );
                    if(! out.getStdout().trim().equals( "true" ) )
                    {
                        failedTests.add( String.format( "%s should match %s but did not", ver, neoVer) );
                    }
                }
                for(String ver : nonMatchingCases)
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, ver, neoVer) );
                    if(! out.getStdout().trim().equals( "false" ) )
                    {
                        failedTests.add( String.format( "%s should NOT match %s but did", ver, neoVer) );
                    }
                }
            }
            if(failedTests.size() > 0)
            {
                Assertions.fail(failedTests.stream().collect( Collectors.joining("\n")));
            }
        }

    }

    private class VersionsJsonEntry
    {
        String neo4j;
        String jar;
        String _testing;

        VersionsJsonEntry(String neo4j)
        {
            this.neo4j = neo4j;
            this._testing = "SNAPSHOT";
            this.jar = "http://host.testcontainers.internal:3000/"+PLUGIN_JAR;
        }
    }
}
