package com.neo4j.docker;

import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.LinkedList;
import java.util.List;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBundledPluginInstallation
{
    private static final int DEFAULT_BROWSER_PORT = 7474;
    private static final int DEFAULT_BOLT_PORT = 7687;

    private static final Logger log = LoggerFactory.getLogger( TestBundledPluginInstallation.class );

    private GenericContainer<?> container;

    @BeforeAll
    public static void checkVersionIsCompatibleWithTest()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 4, 0 ) ) );
    }

    private void createContainerWithBundledPlugin()
    {
        container = new GenericContainer( TestSettings.IMAGE_ID );

        container.withEnv( "NEO4J_AUTH", "neo4j/neo" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( "NEO4JLABS_PLUGINS", "[\"apoc-core\"]" )
                 .withExposedPorts( DEFAULT_BROWSER_PORT, DEFAULT_BOLT_PORT )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
    }

    @BeforeEach
    public void setUp()
    {
        createContainerWithBundledPlugin();
        container.setWaitStrategy( Wait.forHttp( "/" ).forPort( DEFAULT_BROWSER_PORT ).forStatusCode( 200 ) );
    }

    @Test
    public void testBundledPlugin() throws Exception
    {
        // When we start the neo4j docker container
        container.start();

        // Then the plugin is copied to the plugins directory
        List<String> pluginJars = new LinkedList<>();
        for ( String filename : container.execInContainer( "ls", "-1", "/var/lib/neo4j/plugins" ).getStdout().split( "\n" ) )
        {
            if ( filename.endsWith( "jar" ) )
            {
                pluginJars.add( filename );
            }
        }

        assertTrue( pluginJars.size() == 1 );
        assertTrue( pluginJars.get( 0 ).contains( "apoc" ) );
        assertTrue( pluginJars.get( 0 ).contains( "-core" ) );

        // When we connect to the database with the plugin
        String boltAddress = "bolt://" + container.getContainerIpAddress() + ":" + container.getMappedPort( DEFAULT_BOLT_PORT );
        try ( Driver coreDriver = GraphDatabase.driver( boltAddress, AuthTokens.basic( "neo4j", "neo" ) ) )
        {
            Session session = coreDriver.session();
            Result res = session.run( "CALL apoc.help(\"apoc.version\")" );

            // Then something is returned
            assertTrue( res.stream().anyMatch( x -> x.get( "name" ).asString().equals( "apoc.version" ) ) );

            // When we call the procedure from the plugin
            res = session.run( "RETURN apoc.version()" );

            // Then we get the response we expect
            Record record = res.single();
            String version = record.get( 0 ).asString();
            assertTrue( version.startsWith( String.format( "%d.%d", TestSettings.NEO4J_VERSION.major, TestSettings.NEO4J_VERSION.minor ) ),
                        "unexpected version: " + version );
            assertFalse( res.hasNext(), "Our procedure should only return a single result" );

            // Check that the config has been set
            res = session.run( "CALL dbms.listConfig() YIELD name, value WHERE name='dbms.security.procedures.unrestricted' RETURN value" );
            record = res.single();
            assertEquals( record.get( "value" ).asString(), "apoc.*", "neo4j config not updated for plugin" );
            assertFalse( res.hasNext(), "Config lookup should only return a single result" );
        }
        finally
        {
            container.stop();
        }
    }
}
