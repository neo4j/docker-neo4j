package com.neo4j.docker.neo4jserver;

import com.neo4j.docker.neo4jserver.configurations.Configuration;
import com.neo4j.docker.neo4jserver.configurations.Setting;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static java.lang.String.format;

public class TestHealthcheck
{
    private static Logger log = LoggerFactory.getLogger( TestHealthcheck.class );
    private static Map<Setting,Configuration> confNames = Configuration.getConfigurationNameMap();

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    private GenericContainer createContainerWithDefaultListenAddress()
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        container.setWaitStrategy( Wait.forHealthcheck() );
        return container;
    }

    private GenericContainer createContainerWithEnvVarListenAddress( String listenAddress )
    {
        var container = createContainerWithDefaultListenAddress();
        container.withEnv( confNames.get( Setting.HTTP_LISTEN_ADDRESS ).envName, listenAddress );

        return container;
    }

    private GenericContainer createContainerWithConfigListenAddress( String listenAddress ) throws IOException
    {
        var container = createContainerWithDefaultListenAddress();

        var tempConfigDir = temporaryFolderManager.createTempFolder( "healthcheck_listen_address_config" );

        Files.write( tempConfigDir.resolve( "neo4j.conf" ),
                     format( "%s=%s", confNames.get( Setting.HTTP_LISTEN_ADDRESS ).name, listenAddress ).getBytes() );
        temporaryFolderManager.mountHostFolderAsVolume( container, tempConfigDir, "/conf" );

        return container;
    }

    private GenericContainer createContainerWithConfigAndEnvVarListenAddress( String listenAddress ) throws IOException
    {
        var container = createContainerWithDefaultListenAddress();

        container.withEnv( confNames.get( Setting.HTTP_LISTEN_ADDRESS ).envName, listenAddress );

        var tempConfigDir = temporaryFolderManager.createTempFolder( "healthcheck_listen_address_config" );

        Files.write( tempConfigDir.resolve( "neo4j.conf" ),
                     format( "%s=%s", confNames.get( Setting.HTTP_LISTEN_ADDRESS ).name, "incorrecthostname:12345" ).getBytes() );
        temporaryFolderManager.mountHostFolderAsVolume( container, tempConfigDir, "/conf" );

        return container;
    }

    @Test
    void testContainerIsHealthyWhenNeo4jIsListeningAtPort7474()
    {
        try ( var container = createContainerWithDefaultListenAddress() )
        {
            container.start();

            Assertions.assertTrue( container.isRunning() );
            Assertions.assertEquals( "healthy", container.getCurrentContainerInfo().getState().getHealth().getStatus() );
        }
    }

    @ParameterizedTest
    @ValueSource( strings = {":4747", "127.0.0.1:4747", "localhost:4747", "localhost"} )
    void testContainerIsHealthyWhenListenAddressIsModifiedByUser( String listenAddress )
    {
        try ( var container = createContainerWithEnvVarListenAddress( listenAddress ) )
        {
            container.start();

            Assertions.assertTrue( container.isRunning() );
            Assertions.assertEquals( "healthy", container.getCurrentContainerInfo().getState().getHealth().getStatus() );
        }
    }

    @ParameterizedTest
    @ValueSource( strings = {":4747", "127.0.0.1:4747", "localhost:4747", "localhost"} )
    void testContainerIsHealthyWhenConfigIsModifiedByMounting( String listenAddress ) throws IOException
    {
        try ( var container = createContainerWithConfigListenAddress( listenAddress ) )
        {
            container.start();

            Assertions.assertTrue( container.isRunning() );
            Assertions.assertEquals( "healthy", container.getCurrentContainerInfo().getState().getHealth().getStatus() );
        }
    }

    @ParameterizedTest
    @ValueSource( strings = {":4747", "127.0.0.1:4747", "localhost:4747", "localhost"} )
    void testHealthCheckListenAddressUsesEnvVarOver( String listenAddress ) throws IOException
    {
        try ( var container = createContainerWithConfigAndEnvVarListenAddress( listenAddress ) )
        {
            container.start();

            Assertions.assertTrue( container.isRunning() );
            Assertions.assertEquals( "healthy", container.getCurrentContainerInfo().getState().getHealth().getStatus() );
        }
    }
}
