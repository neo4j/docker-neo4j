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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
        return container;
    }

    private GenericContainer createContainerWithEnvVarListenAddress( String listenAddress )
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( confNames.get( Setting.HTTP_LISTEN_ADDRESS ).envName, listenAddress )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        return container;
    }

    private GenericContainer createContainerWithConfigListenAddress( String listenAddress ) throws IOException
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );

        var tempConfigDir = temporaryFolderManager.createTempFolder( "temp_neo4j_config" );

        var neo4jConfig = new File( tempConfigDir.toAbsolutePath() + File.separator + "neo4j.conf" );
        var writer = new BufferedWriter( new FileWriter( neo4jConfig ) );
        Map<Setting,Configuration> confNames = Configuration.getConfigurationNameMap();
        writer.write( format( "%s=%s", confNames.get( Setting.HTTP_LISTEN_ADDRESS ).name, listenAddress ) );
        writer.close();
        temporaryFolderManager.mountHostFolderAsVolume( container, tempConfigDir, "/conf" );

        return container;
    }

    @Test
    void testContainerIsHealthyWhenNeo4jIsListeningAtPort7474()
    {
        try ( var container = createContainerWithDefaultListenAddress() )
        {
            container.setWaitStrategy( Wait.forHealthcheck() );
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
            container.setWaitStrategy( Wait.forHealthcheck() );
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
            container.setWaitStrategy( Wait.forHealthcheck() );
            container.start();

            Assertions.assertTrue( container.isRunning() );
            Assertions.assertEquals( "healthy", container.getCurrentContainerInfo().getState().getHealth().getStatus() );
        }
    }
}
