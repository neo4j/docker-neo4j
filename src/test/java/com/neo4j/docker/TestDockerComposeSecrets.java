package com.neo4j.docker;

import com.github.dockerjava.api.DockerClient;
import com.neo4j.docker.coredb.configurations.Configuration;
import com.neo4j.docker.coredb.configurations.Setting;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.ToStringConsumer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import static com.neo4j.docker.utils.WaitStrategies.waitForBoltReady;

public class TestDockerComposeSecrets
{
    private final Logger log = LoggerFactory.getLogger( TestDockerComposeSecrets.class );

    private static final int DEFAULT_BOLT_PORT = 7687;
    private static final int DEFAULT_HTTP_PORT = 7474;
    private static final Path TEST_RESOURCES_PATH = Paths.get( "src", "test", "resources", "dockersecrets" );

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @BeforeAll
    public static void skipTestsForARM()
    {
        Assumptions.assumeFalse( System.getProperty( "os.arch" ).equals( "aarch64" ),
                                 "This test is ignored on ARM architecture, because Docker Compose Container doesn't support it." );
    }

    private ComposeContainer createContainer( File composeFile, Path containerRootDir, String serviceName )
    {
        var container = new ComposeContainer( composeFile );

        container.withExposedService( serviceName, DEFAULT_BOLT_PORT )
                 .withExposedService( serviceName, DEFAULT_HTTP_PORT )
                 .withEnv( "NEO4J_IMAGE", TestSettings.IMAGE_ID.asCanonicalNameString() )
                 .withEnv( "HOST_ROOT", containerRootDir.toAbsolutePath().toString() )
                 .waitingFor( serviceName, waitForBoltReady() )
                 .withLogConsumer( serviceName, new Slf4jLogConsumer( log ) );

        return container;
    }

    /* We need to stop the neo4j service before we stop the docker compose container otherwise there is a race condition for
       files that are written in mounted folders. This should not be needed when https://github.com/testcontainers/testcontainers-java/issues/9870 is fixed
    */
    private void stopContainerSafely( ComposeContainer container, String serviceName ) throws IOException
    {
        var containerId = container.getContainerByServiceName( serviceName ).get().getContainerId();

        DockerClient dockerClient = DockerClientFactory.lazyClient();
        dockerClient.stopContainerCmd( containerId ).exec();

        container.stop();
    }

    @Test
    void shouldCreateContainerAndConnect() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Simple_Container_Compose" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, TEST_RESOURCES_PATH.resolve( "simple-container-compose.yml" ).toFile() );
        var serviceName = "simplecontainer";

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            dockerComposeContainer.start();

            var dbio = new DatabaseIO( dockerComposeContainer.getServiceHost( serviceName, DEFAULT_BOLT_PORT ),
                                       dockerComposeContainer.getServicePort( serviceName, DEFAULT_BOLT_PORT ) );
            dbio.verifyConnectivity( "neo4j", "simplecontainerpassword" );
            stopContainerSafely( dockerComposeContainer, serviceName );
        }
    }

    @Test
    void shouldCreateContainerWithSecretPasswordAndConnect() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Container_Compose_With_Secrets" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, TEST_RESOURCES_PATH.resolve( "container-compose-with-secrets.yml" ).toFile() );
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
            stopContainerSafely( dockerComposeContainer, serviceName );
        }
    }

    @Test
    void shouldOverrideVariableWithSecretValue() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Container_Compose_With_Secrets_Override" );
        Files.createDirectories( tmpDir.resolve( "neo4j" ).resolve( "config" ) );

        var composeFile = copyDockerComposeResourceFile( tmpDir, TEST_RESOURCES_PATH.resolve( "container-compose-with-secrets-override.yml" ).toFile() );
        var serviceName = "secretsoverridecontainer";

        var newSecretPageCache = "50M";
        Files.createFile( tmpDir.resolve( "neo4j_pagecache.txt" ) );
        Files.writeString( tmpDir.resolve( "neo4j_pagecache.txt" ), newSecretPageCache );

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            dockerComposeContainer.start();

            var dbio = new DatabaseIO( dockerComposeContainer.getServiceHost( serviceName, DEFAULT_BOLT_PORT ),
                                       dockerComposeContainer.getServicePort( serviceName, DEFAULT_BOLT_PORT ) );

            var secretSetting = dbio.getConfigurationSettingAsString( "neo4j",
                                                                      "secretsoverridecontainerpassword",
                                                                      Configuration.getConfigurationNameMap().get( Setting.MEMORY_PAGECACHE_SIZE ) );

            Assertions.assertTrue( secretSetting.contains( "50" ) );

            stopContainerSafely( dockerComposeContainer, serviceName );
        }
    }

    @Test
    void shouldFailIfSecretFileDoesNotExist() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Container_Compose_With_Secrets_Override" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, TEST_RESOURCES_PATH.resolve( "container-compose-with-secrets-override.yml" ).toFile() );
        var serviceName = "secretsoverridecontainer";

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            Assertions.assertThrows( Exception.class, dockerComposeContainer::start );
        }
    }

    @Test
    void shouldFailAndPrintMessageIfFileIsNotReadable() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Container_Compose_With_Secrets_Override" );
        var composeFile = copyDockerComposeResourceFile( tmpDir, TEST_RESOURCES_PATH.resolve( "container-compose-with-secrets-override.yml" ).toFile() );
        var serviceName = "secretsoverridecontainer";

        Files.createFile( tmpDir.resolve( "neo4j_pagecache.txt" ) );
        Files.writeString( tmpDir.resolve( "neo4j_pagecache.txt" ), "50M" );

        var newPermissions = PosixFilePermissions.fromString( "rw-------" );
        Files.setPosixFilePermissions( tmpDir.resolve( "neo4j_pagecache.txt" ), newPermissions );

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            var containerLogConsumer = new ToStringConsumer();
            dockerComposeContainer.withLogConsumer( serviceName, containerLogConsumer );
            var expectedLogLine = "The secret file '/run/secrets/neo4j_dbms_memory_pagecache_size_file' does not exist or is not readable. " +
                                  "Make sure you have correctly configured docker secrets.";
            Assertions.assertThrows( Exception.class, dockerComposeContainer::start );
            Assertions.assertTrue( containerLogConsumer.toUtf8String().contains( expectedLogLine ) );
        }
    }

    @Test
    void shouldIgnoreNonNeo4jFileEnvVars() throws Exception
    {
        var tmpDir = temporaryFolderManager.createFolder( "Simple_Container_Compose_With_File_Var" );
        var composeFile =
                copyDockerComposeResourceFile( tmpDir, TEST_RESOURCES_PATH.resolve( "simple-container-compose-with-external-file-var.yml" ).toFile() );
        var serviceName = "simplecontainer";

        try ( var dockerComposeContainer = createContainer( composeFile, tmpDir, serviceName ) )
        {
            dockerComposeContainer.start();

            var dbio = new DatabaseIO( dockerComposeContainer.getServiceHost( serviceName, DEFAULT_BOLT_PORT ),
                                       dockerComposeContainer.getServicePort( serviceName, DEFAULT_BOLT_PORT ) );
            dbio.verifyConnectivity( "neo4j", "simplecontainerpassword" );

            stopContainerSafely( dockerComposeContainer, serviceName );
        }
    }

    private File copyDockerComposeResourceFile( Path targetDirectory, File resourceFile ) throws IOException
    {
        File compose_file = new File( targetDirectory.toString(), resourceFile.getName() );
        if ( compose_file.exists() )
        {
            Files.delete( compose_file.toPath() );
        }
        Files.copy( resourceFile.toPath(), Paths.get( compose_file.getPath() ) );
        return compose_file;
    }
}
