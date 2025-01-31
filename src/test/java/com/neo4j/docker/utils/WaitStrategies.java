package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.DockerStatus;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class WaitStrategies
{

    private final static Duration STARTUP_TIMEOUT_SECONDS = Duration.ofSeconds(180);

    private WaitStrategies() {}

    public static WaitStrategy waitForNeo4jReady( String username, String password, String database, Duration timeout )
    {
        if (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE &&
            TestSettings.NEO4J_VERSION.isAtLeastVersion(Neo4jVersion.NEO4J_VERSION_500)) {
            return Wait.forHttp("/db/" + database + "/cluster/available")
                       .withBasicCredentials(username, password)
                       .forPort(7474)
                       .forStatusCode(200)
                       .withStartupTimeout(timeout);
        } else
        {
            return waitForBoltReady();
        }
    }

    public static WaitStrategy waitForNeo4jReady( String password ) {
        return waitForNeo4jReady( "neo4j", password, "neo4j", STARTUP_TIMEOUT_SECONDS);
    }

    public static WaitStrategy waitForBoltReady()
    {
        return Wait.forHttp("/")
                   .forPort(7687)
                   .forStatusCode(200)
                   .withStartupTimeout(STARTUP_TIMEOUT_SECONDS);
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
