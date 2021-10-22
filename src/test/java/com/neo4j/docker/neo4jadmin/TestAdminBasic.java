package com.neo4j.docker.neo4jadmin;

import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.time.Duration;

public class TestAdminBasic
{
    private static final Logger log = LoggerFactory.getLogger( TestAdminBasic.class );

    @Test
    void testCannotRunNeo4j()
    {
        GenericContainer admin = new GenericContainer( TestSettings.ADMIN_IMAGE_ID );
        admin.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
             .withExposedPorts( 7474 )
             .withLogConsumer( new Slf4jLogConsumer( log ) )
             .withStartupCheckStrategy( new OneShotStartupCheckStrategy().withTimeout( Duration.ofSeconds( 15 ) ) )
             .waitingFor( new HttpWaitStrategy().forPort( 7474 ).forStatusCode( 200 ) )
             .withCommand( "neo4j", "console" );

        Assertions.assertThrows( ContainerLaunchException.class, () -> admin.start() );
        admin.stop();
    }
}
