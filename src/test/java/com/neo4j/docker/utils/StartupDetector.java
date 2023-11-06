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
import org.testcontainers.utility.DockerStatus;

public class StartupDetector {
    private StartupDetector() {}

    public static GenericContainer makeContainerWaitForDatabaseReady(
            GenericContainer container, String username,
            String password, String database, Duration timeout) {
        if (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE &&
                TestSettings.NEO4J_VERSION.isAtLeastVersion(Neo4jVersion.NEO4J_VERSION_500)) {
            container.setWaitStrategy(Wait.forHttp("/db/" + database + "/cluster/available")
                    .withBasicCredentials(username, password)
                    .forPort(7474)
                    .forStatusCode(200)
                    .withStartupTimeout(timeout));
        } else {
            container.setWaitStrategy(Wait.forHttp("/")
                    .forPort(7687)
                    .forStatusCode(200)
                    .withStartupTimeout(timeout));
        }
        return container;
    }

    public static GenericContainer makeContainerWaitForNeo4jReady(GenericContainer container, String password) {
        return makeContainerWaitForDatabaseReady(container, "neo4j", password, "neo4j", Duration.ofSeconds(60));
    }

    public static GenericContainer makeContainerWaitForNeo4jReady(GenericContainer container, String password, Duration timeout) {
        return makeContainerWaitForDatabaseReady(container, "neo4j", password, "neo4j", timeout);
    }

    /**For containers that will just run a command and exit automatically.
     * With this wait strategy, starting a container will block until the container has closed itself.
     * The container could have succeeded or failed, we just wait for it to close.
     * @param container the container to set the wait strategy on
     * @param timeout how long to wait
     * @return container in the modified state.
     * */
    public static GenericContainer makeContainerWaitUntilFinished(GenericContainer container, Duration timeout)
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
