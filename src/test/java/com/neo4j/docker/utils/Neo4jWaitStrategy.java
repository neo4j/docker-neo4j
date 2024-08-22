package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.io.IOException;
import java.time.Duration;

public class Neo4jWaitStrategy extends HttpWaitStrategy
{
    private static Logger log = LoggerFactory.getLogger(Neo4jWaitStrategy.class);

    public static WaitStrategy waitForNeo4jReady(String username, String password, String database, Duration timeout )
    {
        if (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE &&
                TestSettings.NEO4J_VERSION.isAtLeastVersion(Neo4jVersion.NEO4J_VERSION_500)) {
            return new Neo4jWaitStrategy()
                    .forPath("/db/" + database + "/cluster/available")
                    .withBasicCredentials(username, password)
                    .forPort(7474)
                    .forStatusCode(200)
                    .withStartupTimeout(timeout);
        } else
        {
            return waitForBoltReady( timeout );
        }
    }

    public static WaitStrategy waitForBoltReady( Duration timeout )
    {
        return new Neo4jWaitStrategy()
                .forPath("/")
                .forPort(7687)
                .forStatusCode(200)
                .withStartupTimeout(timeout);
    }

    @Override
    protected void waitUntilReady()
    {
        try {
            super.waitUntilReady();
        }
        catch (ContainerLaunchException ex)
        {
            InspectContainerResponse containerInfo = waitStrategyTarget.getCurrentContainerInfo();
            log.error("Failed to start container. State:\n"+containerInfo.toString());
            String doThreadDumpCmd = "jcmd $(cat /var/lib/neo4j/run/neo4j.pid) Thread.print > /var/lib/neo4j/logs/threaddump";
            // if running as default user then we need to be `neo4j` to query the neo4j process.
            if(containerInfo.getConfig().getUser().isEmpty())
            {
                doThreadDumpCmd = "su-exec neo4j " + doThreadDumpCmd;
            }
            try
            {
                String fullCommand =
                        "if [ -f /var/lib/neo4j/run/neo4j.pid ]; then\n" +
                                doThreadDumpCmd + "\n" +
                            "else\n" +
                                "echo >&2 \"could not dump threads, Neo4j is not running.\"\n" +
                            "fi";
                Container.ExecResult threadDumpResponse = waitStrategyTarget.execInContainer("sh", "-c", fullCommand);
                log.warn(threadDumpResponse.getStderr());
            }
            catch (IOException | InterruptedException e)
            {
                throw new RuntimeException(e);
            }
            throw ex;
        }
    }
}
