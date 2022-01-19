package com.neo4j.docker.neo4jserver;

import com.google.gson.Gson;
import com.neo4j.docker.neo4jserver.plugins.ExampleNeo4jPlugin;
import com.neo4j.docker.utils.*;
import com.neo4j.docker.neo4jserver.plugins.JarBuilder;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.google.common.io.Files;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.driver.Record;

import static com.neo4j.docker.utils.TestSettings.NEO4J_VERSION;

@EnableRuleMigrationSupport
public class TestPluginInstallation
{
    private static final String DB_USER = "neo4j";
    private static final String DB_PASSWORD = "quality";
    private static final String PLUGIN_JAR = "myPlugin.jar";

    private static final Logger log = LoggerFactory.getLogger( TestPluginInstallation.class );

    @Rule
    public HttpServerRule httpServer = new HttpServerRule();


    private GenericContainer createContainerWithTestingPlugin()
    {
        Testcontainers.exposeHostPorts( httpServer.PORT );
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );

        container.withEnv( "NEO4J_AUTH", DB_USER+"/"+ DB_PASSWORD)
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withEnv( "NEO4JLABS_PLUGINS", "[\"_testing\"]" )
                .withExposedPorts( 7474, 7687 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .waitingFor( Wait.forHttp( "/" )
                                 .forPort( 7474 )
                                 .forStatusCode( 200 ) );

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

    private void setupTestPluginSingleVersionOption(Path pluginsDir ) throws Exception
    {
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.toString() );
        setupTestPlugin( pluginsDir, versionsJson );
    }

    private void setupTestPlugin(Path pluginsDir, File versionsJson ) throws Exception
    {
       File myPluginJar = pluginsDir.resolve( PLUGIN_JAR ).toFile();
        new JarBuilder().createJarFor( myPluginJar, ExampleNeo4jPlugin.class, ExampleNeo4jPlugin.PrimitiveOutput.class );

        httpServer.registerHandler( versionsJson.getName(), new HostFileHttpHandler( versionsJson, "application/json" ) );
        httpServer.registerHandler( PLUGIN_JAR, new HostFileHttpHandler( myPluginJar, "application/java-archive" ) );
    }

    private void verifyTestPluginLoaded(DatabaseIO db)
    {
        // when we check the list of installed procedures...
        List<Record> procedures = db.runCypherQuery(DB_USER, DB_PASSWORD,
                "CALL dbms.procedures() YIELD name, signature RETURN name, signature");
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
    public void testPlugin(@TempDir Path pluginsDir) throws Exception
    {
        setupTestPluginSingleVersionOption(pluginsDir);
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
        }
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "NEO4J_DOCKER_TESTS_TestPluginInstallation", matches = "ignore")
    public void testPluginConfigurationDoesNotOverrideUserSetValues(@TempDir Path pluginsDir) throws Exception
    {
        setupTestPluginSingleVersionOption(pluginsDir);
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
            List<Record> results = db.runCypherQuery(DB_USER, DB_PASSWORD,
                        "CALL dbms.listConfig() YIELD name, value WHERE name='dbms.security.procedures.unrestricted' RETURN value");
            Assertions.assertEquals(1, results.size(), "Config lookup should only return a single result");
            Assertions.assertEquals( "foo", results.get(0).get( "value" ).asString(),
                    "neo4j config should not be overridden by plugin" );
        }
    }

    @Test
    void testSemanticVersioningPluginSelection(@TempDir Path pluginsDir) throws Exception
    {
        File versionsJson = createTestVersionsJson( pluginsDir, NEO4J_VERSION.getBranch()+".x");
        setupTestPlugin( pluginsDir, versionsJson );
        try(GenericContainer container = createContainerWithTestingPlugin())
        {
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            verifyTestPluginLoaded(db);
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
