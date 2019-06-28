package com.neo4j.docker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TestBasic
{
    private static Logger log = LoggerFactory.getLogger( TestBasic.class );
    private GenericContainer container;


    private void createBasicContainer()
    {
        container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
    }


    @Test
    void testListensOn7474()
    {
        createBasicContainer();
        container.setWaitStrategy( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) );
        container.start();
        Assertions.assertTrue( container.isRunning() );
        container.stop();
    }

    @Test
    void testNoUnexpectedErrors() throws Exception
    {
        // version 4.0 still has some annoying warnings that haven't been cleaned up, skip this test for now
        Assumptions.assumeFalse( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4,0,0 ) ),
                                 "skipping unexpected error test for version 4.0");

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

        Assertions.assertEquals( "", toStringConsumer.toUtf8String(), "Unexpected errors in stderr from container!\n"+toStringConsumer.toUtf8String() );
        container.stop();
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
        container.setWaitStrategy( Wait.forLogMessage(  "must accept the license", 1 ).withStartupTimeout( Duration.ofSeconds( 10 ) ) );

        Assertions.assertThrows( org.testcontainers.containers.ContainerLaunchException.class,
                                 ()-> container.start(),
                                 "Neo4j did not notify about accepting the license agreement" );
    }

//    @Test
//    void testCypherShellOnPath() throws Exception
//    {
//        String expectedCypherShellPath = "/var/lib/neo4j/bin/cypher-shell";
//
//        createBasicContainer();
//        container.withCommand( "which cypher-shell" );
//        container.setWaitStrategy( null );
//        container.start();
//
//        ToStringConsumer toStringConsumer = new ToStringConsumer();
//        WaitingConsumer waitingConsumer = new WaitingConsumer();
//        container.followOutput( waitingConsumer, OutputFrame.OutputType.STDOUT);
//        container.followOutput( toStringConsumer , OutputFrame.OutputType.STDOUT);
//        waitingConsumer.waitUntil( frame -> frame.getUtf8String().contains( expectedCypherShellPath ),
//                                   10, TimeUnit.SECONDS);
//        Assertions.assertTrue( toStringConsumer.toUtf8String().contains( expectedCypherShellPath ),
//                               "cypher-shell was not on the path. Received:\n"+toStringConsumer.toUtf8String() );
//
//        container.stop();
//    }
//
//    @Test
//    void testCypherShellOnPath2() throws Exception
//    {
//        String expectedCypherShellPath = "/var/lib/neo4j/bin/cypher-shell";
//
//        createBasicContainer();
//        container.withCommand( "which cypher-shell" );
//        container.setWaitStrategy( Wait.forLogMessage( expectedCypherShellPath+"[\\s]*", 1 ).withStartupTimeout( Duration.ofSeconds( 10 ) ) );
//
//        Assertions.assertDoesNotThrow( () -> container.start(),
//                                       "cypher-shell was not on the path. Received:\n"+container.getLogs() );
//        container.stop();
//    }

    @Test
    void testCypherShellOnPath3() throws Exception
    {
        String expectedCypherShellPath = "/var/lib/neo4j/bin/cypher-shell";
        createBasicContainer();
        container.setWaitStrategy( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) );
        container.start();

        Container.ExecResult whichResult = container.execInContainer( "which", "cypher-shell");

        Assertions.assertTrue( whichResult.getStdout().contains( expectedCypherShellPath ),
                               "cypher-shell not on path" );
    }

    @Test
    void testCanChangeWorkDir() throws Exception
    {
        createBasicContainer();
        container.setWorkingDirectory( "/tmp" );
        container.setWaitStrategy( Wait.forHttp( "/" )
                                           .forPort( 7474 )
                                           .forStatusCode( 200 )
                                           .withStartupTimeout( Duration.ofSeconds( 60 ) ));

        Assertions.assertDoesNotThrow( () -> container.start(),
                                  "Could not start neo4j from workdir NEO4J_HOME" );
        container.stop();
    }
}
