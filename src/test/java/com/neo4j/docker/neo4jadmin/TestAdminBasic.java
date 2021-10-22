package com.neo4j.docker.neo4jadmin;

import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;

import java.time.Duration;

public class TestAdminBasic
{
    private static final Logger log = LoggerFactory.getLogger( TestAdminBasic.class );

    private GenericContainer createAdminContainer()
    {
        GenericContainer container = new GenericContainer( TestSettings.ADMIN_IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .withStartupCheckStrategy( new OneShotStartupCheckStrategy().withTimeout( Duration.ofSeconds( 90 ) ) );
        return container;
    }

    @Test
    void testCannotRunNeo4j()
    {
        try(GenericContainer admin = createAdminContainer())
        {
            admin.withCommand( "neo4j", "console" );
            Assertions.assertThrows( ContainerLaunchException.class, () -> admin.start() );
        }
    }
}
