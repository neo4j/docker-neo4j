package com.neo4j.docker;

import com.neo4j.docker.utils.TemporaryFolderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;

import static java.lang.System.getenv;

public class TestDockerComposeSecrets
{
    private static final int DEFAULT_BOLT_PORT = 7687;
    private static final int DEFAULT_HTTP_PORT = 7474;

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    String dockerComposeFileName = "simple-container-compose.yml";

    @Test
    void shouldCreateContainer() throws Exception
    {
        Path tmpDir = temporaryFolderManager.createFolder( "Simple_Container_Compose_" );

        File compose_file = new File( tmpDir.toString(), dockerComposeFileName );
        Files.copy( Objects.requireNonNull( getClass().getClassLoader().getResourceAsStream( dockerComposeFileName ) ), Paths.get( compose_file.getPath() ) );

        var dockerComposeContainer = new DockerComposeContainer( compose_file )
                .withExposedService( "simplecontainer", DEFAULT_BOLT_PORT )
                .withExposedService( "simplecontainer", DEFAULT_HTTP_PORT,
                                     Wait.forHttp( "/" )
                                         .forPort( DEFAULT_HTTP_PORT )
                                         .forStatusCode( 200 )
                                         .withStartupTimeout( Duration.ofSeconds( 300 ) ) )
                .withEnv( "NEO4J_IMAGE", getenv( "NEO4J_IMAGE" ) )
                .withEnv( "HOST_ROOT", tmpDir.toAbsolutePath().toString() )
                .withEnv( "BOLT_PORT", Integer.toString( DEFAULT_BOLT_PORT) )
                .withEnv( "HTTP_PORT", Integer.toString( DEFAULT_HTTP_PORT) );

        dockerComposeContainer.start();
    }
}
