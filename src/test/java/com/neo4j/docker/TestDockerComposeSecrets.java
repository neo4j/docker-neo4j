package com.neo4j.docker;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
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

public class TestDockerComposeSecrets
{
    private static final int DEFAULT_BOLT_PORT = 7687;
    private static final int DEFAULT_HTTP_PORT = 7474;

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    private DockerComposeContainer createContainer( File composeFile, Path containerRootDir, String serviceName )
    {
        var container = new DockerComposeContainer( composeFile );

        container.withExposedService( serviceName, DEFAULT_BOLT_PORT ).withExposedService( serviceName, DEFAULT_HTTP_PORT,
                                                                                           Wait.forHttp( "/" ).forPort( DEFAULT_HTTP_PORT )
                                                                                               .forStatusCode( 200 ).withStartupTimeout(
                                                                                                       Duration.ofSeconds( 300 ) ) )
                 .withEnv( "NEO4J_IMAGE", TestSettings.IMAGE_ID.asCanonicalNameString() ).withEnv( "HOST_ROOT", containerRootDir.toAbsolutePath().toString() );

        return container;
    }

    @Test
    void shouldCreateContainerAndConnect() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Simple_Container_Compose" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, "simple-container-compose.yml" );
        var serviceName = "simplecontainer";

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            dockerComposeContainer.start();

            var dbio = new DatabaseIO( dockerComposeContainer.getServiceHost( serviceName, DEFAULT_BOLT_PORT ),
                                       dockerComposeContainer.getServicePort( serviceName, DEFAULT_BOLT_PORT ) );
            dbio.verifyConnectivity( "neo4j", "simplecontainerpassword" );
        }
    }

    @Test
    void shouldCreateContainerWithSecretPasswordAndConnect() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Container_Compose_With_Secrets" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, "container-compose-with-secrets.yml" );
        var serviceName = "secretscontainer";

        var newSecretPassword = "neo4j/newSecretPassword";
        Files.createFile( tmpDir.resolve( "neo4j_auth.txt" ) );
        Files.writeString( tmpDir.resolve( "neo4j_auth.txt" ), newSecretPassword );

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            dockerComposeContainer.start();
            var dbio = new DatabaseIO( dockerComposeContainer.getServiceHost( serviceName, DEFAULT_BOLT_PORT ),
                                       dockerComposeContainer.getServicePort( serviceName, DEFAULT_BOLT_PORT ) );
            dbio.verifyConnectivity( "neo4j", "newSecretPassword" );
        }
    }

    @Test
    void shouldOverrideVariableWithSecretValue() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Container_Compose_With_Secrets_Override" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, "container-compose-with-secrets-override.yml" );
        var serviceName = "secretsoverridecontainer";

        var newSecretPageCache = "50M";
        Files.createFile( tmpDir.resolve( "neo4j_pagecache.txt" ) );
        Files.writeString( tmpDir.resolve( "neo4j_pagecache.txt" ), newSecretPageCache );

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            dockerComposeContainer.start();

            var configFile = tmpDir.resolve( "neo4j" ).resolve( "config" ).resolve( "neo4j.conf" ).toFile();
            Assertions.assertTrue( configFile.exists(), "neo4j.conf file does not exist" );
            Assertions.assertTrue( configFile.canRead(), "cannot read neo4j.conf file" );

            Assertions.assertFalse( Files.readAllLines( configFile.toPath() ).contains( "dbms.memory.pagecache.size=10M" ) );
            Assertions.assertTrue( Files.readAllLines( configFile.toPath() ).contains( "dbms.memory.pagecache.size=50M" ) );
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
