package com.neo4j.docker.utils;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static com.neo4j.docker.utils.WaitStrategies.waitForNeo4jReady;

public class HelperContainers
{
    private static Logger log = LoggerFactory.getLogger( HelperContainers.class );

    public static GenericContainer nginx()
    {
        return new GenericContainer(DockerImageName.parse("nginx:latest"))
                .withExposedPorts(80)
                    .waitingFor(Wait.forHttp("/")
                            .withStartupTimeout(Duration.ofSeconds(20)));
    }

    public static GenericContainer createNeo4jContainer( boolean asCurrentUser )
    {
        GenericContainer container = new GenericContainer( TestSettings.NEO4J_IMAGE_ID);
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withExposedPorts( 7474, 7687 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .waitingFor( waitForNeo4jReady( "neo4j" ) );
        if(asCurrentUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }
}
