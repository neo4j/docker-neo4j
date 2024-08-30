package com.neo4j.docker;

import com.neo4j.docker.utils.TemporaryFolderManager;
import org.junit.ClassRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import static java.lang.System.getenv;

public class TestDockerComposeSecrets
{
    private static final int DEFAULT_BOLT_PORT = 7687;
    private static final int DEFAULT_HTTP_PORT = 7474;

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @Test
    void shouldCreateContainer() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Simple_Container_Compose" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, "simple-container-compose.yml" );
        var serviceName = "simplecontainer";

        try ( var dockerComposeContainer = new DockerComposeContainer( composeFile ) )
        {
            dockerComposeContainer
                    .withExposedService( serviceName, DEFAULT_BOLT_PORT )
                    .withExposedService( serviceName, DEFAULT_HTTP_PORT,
                                         Wait.forHttp( "/" )
                                             .forPort( DEFAULT_HTTP_PORT )
                                             .forStatusCode( 200 )
                                             .withStartupTimeout( Duration.ofSeconds( 300 ) ) )
                    .withEnv( "NEO4J_IMAGE", getenv( "NEO4J_IMAGE" ) )
                    .withEnv( "HOST_ROOT", tmpDir.toAbsolutePath().toString() );

            dockerComposeContainer.start();

            assertSuccessfulAuth( dockerComposeContainer, serviceName, "neo4j", "simplecontainerpassword" );
        }
    }

    @Test
    void shouldCreateContainerWithSecrets() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Container_Compose_With_Secrets" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, "container-compose-with-secrets.yml" );
        var serviceName = "secretscontainer";

        var newSecretPassword = "neo4j/newSecretPassword";
        Files.createFile( tmpDir.resolve( "neo4j_auth.txt" ) );
        Files.writeString( tmpDir.resolve( "neo4j_auth.txt" ), newSecretPassword );

        try ( var dockerComposeContainer = new DockerComposeContainer( composeFile ) )
        {
            dockerComposeContainer
                    .withExposedService( serviceName, DEFAULT_BOLT_PORT )
                    .withExposedService( serviceName, DEFAULT_HTTP_PORT,
                                         Wait.forHttp( "/" )
                                             .forPort( DEFAULT_HTTP_PORT )
                                             .forStatusCode( 200 )
                                             .withStartupTimeout( Duration.ofSeconds( 300 ) ) )
                    .withEnv( "NEO4J_IMAGE", getenv( "NEO4J_IMAGE" ) )
                    .withEnv( "HOST_ROOT", tmpDir.toAbsolutePath().toString() );

            dockerComposeContainer.start();

            assertSuccessfulAuth( dockerComposeContainer, serviceName, "neo4j", "newSecretPassword" );
        }
    }

    private void assertSuccessfulAuth( DockerComposeContainer container, String serviceName, String username, String password )
    {
        String serviceUri = "neo4j://" + container.getServiceHost( serviceName, DEFAULT_BOLT_PORT )
                            + ":" +
                            container.getServicePort( serviceName, DEFAULT_BOLT_PORT );

        try ( Driver coreDriver = GraphDatabase.driver( serviceUri, AuthTokens.basic( username, password ) ) )
        {
            Session session = coreDriver.session();
            Result rs = session.run( "CREATE (god {name:'Zeus'})-[:FATHERS]->(demigod {name:'Hercules'}) RETURN demigod.name" );
            Assertions.assertEquals( "Hercules", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
        }
        catch ( Exception e )
        {
            Assertions.fail( "Failed to connect to neo4j" );
            container.stop();
        }
    }

    private File copyDockerComposeResourceFile( Path targetDirectory, String fileName ) throws IOException
    {
        File compose_file = new File( targetDirectory.toString(), fileName );
        if ( compose_file.exists() )
        {
            Files.delete( compose_file.toPath() );
        }
        Files.copy( Objects.requireNonNull( getClass().getClassLoader().getResourceAsStream( fileName ) ), Paths.get( compose_file.getPath() ) );
        return compose_file;
    }
}
