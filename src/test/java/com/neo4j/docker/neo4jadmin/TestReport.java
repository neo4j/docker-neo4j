package com.neo4j.docker.neo4jadmin;

import com.neo4j.docker.utils.WaitStrategies;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.time.Duration;

public class TestReport
{
    private final Logger log = LoggerFactory.getLogger( TestReport.class );

    private GenericContainer createAdminContainer()
    {
        GenericContainer container = new GenericContainer( TestSettings.ADMIN_IMAGE_ID )
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" ).withEnv( "NEO4J_DEBUG", "yes" )
                .withCommand( "neo4j-admin", "server", "report" )
                .withLogConsumer( new Slf4jLogConsumer( log ) );
        WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 20 ) );
        return container;
    }

    @Test
    void shouldErrorHelpfullyIfAdminReport()
    {
        try(GenericContainer container = createAdminContainer())
        {
            Assertions.assertThrows( ContainerLaunchException.class, container::start );
            Assertions.assertTrue( container.getLogs().contains( "To run the report tool inside a neo4j container, do:" ),
                                   "did not error about needing to run in the same container as the database." +
                                   " Actual logs:"+container.getLogs() );
            Assertions.assertTrue( container.getLogs().contains( "docker exec <CONTAINER NAME> neo4j-admin-report" ),
                                   "did not error about needing to run in the same container as the database." +
                                   " Actual logs:"+container.getLogs() );
        }
    }
}
