package com.neo4j.docker.utils;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.DockerStatus;

public class WaitStrategies
{
    private WaitStrategies() {}

    public static WaitStrategy waitForNeo4jReady( String username, String password, String database, Duration timeout )
    {
        return Neo4jWaitStrategy.waitForNeo4jReady(username, password, database, timeout);
    }

    public static WaitStrategy waitForNeo4jReady( String password ) {
        return waitForNeo4jReady( "neo4j", password, "neo4j", Duration.ofSeconds(60));
    }

    public static WaitStrategy waitForNeo4jReady( String password, Duration timeout ) {
        return waitForNeo4jReady( "neo4j", password, "neo4j", timeout);
    }

    public static WaitStrategy waitForNeo4jReady( String user, String password, Duration timeout ) {
        return waitForNeo4jReady( user, password, "neo4j", timeout);
    }

    public static WaitStrategy waitForBoltReady( Duration timeout )
    {
        return Neo4jWaitStrategy.waitForBoltReady(timeout);
    }

    /**For containers that will just run a command and exit automatically.
     * With this wait strategy, starting a container will block until the container has closed itself.
     * The container could have succeeded or failed, we just wait for it to close.
     * If the container fails to start, it will still throw an exception, this just prevents us from having to wait the full timeout.
     * @param container the container to set the wait strategy on
     * @param timeout how long to wait
     * @return container in the modified state.
     * */
    public static GenericContainer waitUntilContainerFinished( GenericContainer container, Duration timeout)
    {
        container.setStartupCheckStrategy( new OneShotStartupCheckStrategy().withTimeout( timeout ) );
        container.setWaitStrategy( new AbstractWaitStrategy()
        {
            @Override
            protected void waitUntilReady()
            {
                Callable<Boolean> isFinished = () -> {
                    InspectContainerResponse.ContainerState x = waitStrategyTarget.getCurrentContainerInfo().getState();
                    return DockerStatus.isContainerStopped( x );
                };
                Unreliables.retryUntilTrue( (int)timeout.getSeconds(), TimeUnit.SECONDS, isFinished );
            }
        });
        return container;
    }
}
