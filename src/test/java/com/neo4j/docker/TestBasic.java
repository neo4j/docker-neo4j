package com.neo4j.docker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;

import java.util.concurrent.TimeUnit;

public class TestBasic
{
    private static Logger log = LoggerFactory.getLogger( TestBasic.class );
    private Neo4jContainer container;


    private void createBasicContainer()
    {
        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer( log );

        container = new Neo4jContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474 )
                 .withLogConsumer( logConsumer );
    }


    @Test
    void testListensOn7474()
    {
        createBasicContainer();
        container.start();
        Assertions.assertTrue( container.isRunning() );
    }

    @Test
    void testNoUnexpectedErrors() throws Exception
    {
        createBasicContainer();
        container.start();
        Assertions.assertTrue( container.isRunning() );

        ToStringConsumer toStringConsumer = new ToStringConsumer();
        WaitingConsumer waitingConsumer = new WaitingConsumer();
        container.followOutput( waitingConsumer, OutputFrame.OutputType.STDOUT);
        container.followOutput( toStringConsumer , OutputFrame.OutputType.STDERR);

        // wait for neo4j to start
        waitingConsumer.waitUntil( frame -> frame.getUtf8String().contains( "Remote interface available at http://localhost:7474/" ),
                                   10, TimeUnit.SECONDS);

        Assertions.assertEquals( "Unexpected errors in stderr from container!\n"+toStringConsumer.toUtf8String(),
                      "", toStringConsumer.toUtf8String() );
    }

    @Test
    void testIgnoreNumericVars()
    {
        createBasicContainer();
        container.withEnv( "NEO4J_1a", "1" );
        container.start();
        Assertions.assertTrue( container.isRunning() );

        WaitingConsumer waitingConsumer = new WaitingConsumer();
        container.followOutput(waitingConsumer);

        try
        {
            waitingConsumer.waitUntil( frame -> frame.getUtf8String()
                                                     .contains( "WARNING: 1a not written to conf file because settings that " +
                                                                "start with a number are not permitted" ), 30, TimeUnit.SECONDS );
        }
        catch(Exception e)
        {
            Assertions.fail("Neo4j did not warn about invalid numeric config variable `Neo4j_1a`");
        }
    }

    @Test
    void testLicenseAcceptanceRequired()
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition");
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,3,0 ) ),
                                "No license checks before version 3.3.0");

        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        container.start();

        WaitingConsumer waitingConsumer = new WaitingConsumer();
        container.followOutput(waitingConsumer);

        try
        {
            // wait 30 seconds for error about no license
            waitingConsumer.waitUntil( frame -> frame.getUtf8String()
                                                     .contains( "must accept the license" ),
                                       30, TimeUnit.SECONDS );
        }
        catch(Exception e)
        {
            Assertions.fail("Neo4j did not notify about accepting the license agreement");
        }
    }

    @Test
    void testCypherShellOnPath() throws Exception
    {
        createBasicContainer();
        container.withCommand( "echo hail satan!" ); // don't start Neo4j in the container because we don't need it
        container.setWaitStrategy( null );
        container.start();

        Container.ExecResult whichResult = container.execInContainer("which", "cypher-shell");

        Assertions.assertTrue( whichResult.getStdout().contains( "/var/lib/neo4j/bin/cypher-shell" ),
                               "cypher-shell not on path" );
    }

    @Test
    void testCanChangeWorkDir() throws Exception
    {
        createBasicContainer();
//        container.withCommand( "echo hail satan!" ); // don't start Neo4j in the container because we don't need it
//        container.setWaitStrategy( null );
        container.setWorkingDirectory( "/tmp" );
        container.start();

        Container.ExecResult whichResult = container.execInContainer("pwd");

        Assertions.assertTrue( whichResult.getStdout().contains( "/tmp" ),
                               "Could not start neo4j from outside NEO4J_HOME" );
    }
}
