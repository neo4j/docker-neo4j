package com.neo4j.docker;

import com.neo4j.docker.plugins.ExampleNeo4jPlugin;
import com.neo4j.docker.utils.HostFileHttpHandler;
import com.neo4j.docker.utils.HttpServerRule;
import com.neo4j.docker.plugins.JarBuilder;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.Rule;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.StatementResult;

import static com.neo4j.docker.utils.TestSettings.NEO4J_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableRuleMigrationSupport
public class TestPluginInstallation
{
    private static final int DEFAULT_BROWSER_PORT = 7474;
    private static final int DEFAULT_BOLT_PORT = 7687;

    private static final String versions = "versions.json";
    private static final String myPlugin = "myPlugin.jar";

    private static final Logger log = LoggerFactory.getLogger( TestPluginInstallation.class );

    @Rule
    public HttpServerRule httpServer = new HttpServerRule();

    private GenericContainer container;

    @BeforeAll
    public static void checkVersionIsCompatibleWithTest()
    {
        // Should work for all versions
    }

    private void createContainerWithTestingPlugin()
    {
        Testcontainers.exposeHostPorts( httpServer.PORT );
        container = new GenericContainer( TestSettings.IMAGE_ID );

        container.withEnv( "NEO4J_AUTH", "neo4j/neo" )
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withEnv( "NEO4JLABS_PLUGINS", "[\"_testing\"]" )
                .withExposedPorts( DEFAULT_BROWSER_PORT, DEFAULT_BOLT_PORT )
                .withLogConsumer( new Slf4jLogConsumer( log ) );

        SetContainerUser.nonRootUser( container );
    }

    @BeforeEach
    public void setUp( @TempDir Path pluginsDir ) throws Exception
    {
        File versionsJson = pluginsDir.resolve( versions ).toFile();

        Files.write( getResource( "versions.json" ).replace( "$NEO4JVERSION", NEO4J_VERSION.toString() ), versionsJson, StandardCharsets.UTF_8 );

        File myPluginJar = pluginsDir.resolve( myPlugin ).toFile();

        new JarBuilder().createJarFor( myPluginJar, ExampleNeo4jPlugin.class, ExampleNeo4jPlugin.PrimitiveOutput.class );

        httpServer.registerHandler( versions, new HostFileHttpHandler( versionsJson, "application/json" ) );
        httpServer.registerHandler( myPlugin, new HostFileHttpHandler( myPluginJar, "application/java-archive" ) );

        createContainerWithTestingPlugin();
        container.setWaitStrategy( Wait.forHttp( "/" ).forPort( DEFAULT_BROWSER_PORT ).forStatusCode( 200 ) );
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "NEO4J_DOCKER_TESTS_TestPluginInstallation", matches = "ignore")
    public void testPlugin() throws Exception
    {
        // When we start the neo4j docker container
        container.start();

        // Then the plugin is downloaded and placed in the plugins directory
        String lsPluginsDir = container.execInContainer( "ls", "/var/lib/neo4j/plugins" ).getStdout();
        // Two options here because it varies depending on whether the plugins dir _only_ contains our file or if it contains multiple files
        assertTrue( lsPluginsDir.contains( "\n_testing.jar\n" ) || lsPluginsDir.equals( "_testing.jar\n" ), "Plugin jar file not found in plugins directory" );

        // When we connect to the database with the plugin
        String boltAddress = "bolt://" + container.getContainerIpAddress() + ":" + container.getMappedPort( DEFAULT_BOLT_PORT );
        try ( Driver coreDriver = GraphDatabase.driver( boltAddress, AuthTokens.basic( "neo4j", "neo" ) ) )
        {
            Session session = coreDriver.session();
            StatementResult res = session.run( "CALL dbms.procedures() YIELD name, signature RETURN name, signature" );

            // Then the procedure from the plugin is listed
            assertTrue( res.stream().anyMatch( x -> x.get( "name" ).asString().equals( "com.neo4j.docker.plugins.defaultValues" ) ),
                    "Missing procedure provided by our plugin" );

            // When we call the procedure from the plugin
            res = session.run( "CALL com.neo4j.docker.plugins.defaultValues" );

            // Then we get the response we expect
            Record record = res.single();
            String message = "Result from calling our procedure doesnt match our expectations";
            assertEquals( record.get( "string" ).asString(), "a string", message );
            assertEquals( record.get( "integer" ).asInt(), 42L, message );
            assertEquals( record.get( "aFloat" ).asDouble(), 3.14d, 0.000001, message );
            assertEquals( record.get( "aBoolean" ).asBoolean(), true, message );
            assertFalse( res.hasNext(), "Our procedure should only return a single result" );
        }
        finally
        {
            container.stop();
        }
    }

    private String getResource( String path ) throws IOException
    {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream( path );
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ( (length = inputStream.read( buffer )) != -1 )
        {
            result.write( buffer, 0, length );
        }
        return result.toString( "UTF-8" );
    }
}
