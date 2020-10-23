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
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TestBasic
{
    private static Logger log = LoggerFactory.getLogger( TestBasic.class );

    private GenericContainer createBasicContainer()
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        return container;
    }


    @Test
    void testListensOn7474()
    {
        try(GenericContainer container = createBasicContainer())
        {
            container.setWaitStrategy( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) );
            container.start();
            Assertions.assertTrue( container.isRunning() );
        }
    }

    @Test
    void testNoUnexpectedErrors() throws Exception
    {
        // version 4.0 still has some annoying warnings that haven't been cleaned up, skip this test for now

        try(GenericContainer container = createBasicContainer())
        {
			container.setWaitStrategy( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) );
            container.start();
            Assertions.assertTrue( container.isRunning() );

			String stderr = container.getLogs(OutputFrame.OutputType.STDERR);
            Assertions.assertEquals( "", stderr,
                                     "Unexpected errors in stderr from container!\n" +
                                     stderr );
        }
    }

    @Test
    void testLicenseAcceptanceRequired()
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition");
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,3,0 ) ),
                                "No license checks before version 3.3.0");

        String logsOut;
        try(GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID )
                .withLogConsumer( new Slf4jLogConsumer( log ) ) )
        {
            container.waitingFor( Wait.forLogMessage( ".*must accept the license.*", 1 )
                                      .withStartupTimeout( Duration.ofSeconds( 30 ) ) );
			container.setStartupCheckStrategy( new OneShotStartupCheckStrategy() );
			// container start should fail due to licensing.
            Assertions.assertThrows( Exception.class, () -> container.start(),
									 "Neo4j did not notify about accepting the license agreement" );
            logsOut = container.getLogs();
        }
        // double check the container didn't warn and start neo4j anyway
        Assertions.assertTrue( logsOut.contains( "must accept the license" ),
                               "Neo4j did not notify about accepting the license agreement" );
        Assertions.assertFalse( logsOut.contains( "Remote interface available" ),
                                "Neo4j was started even though the license was not accepted" );
    }

    @Test
    void testCypherShellOnPath() throws Exception
    {
        String expectedCypherShellPath = "/var/lib/neo4j/bin/cypher-shell";
        try(GenericContainer container = createBasicContainer())
        {
            container.setWaitStrategy( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) );
            container.start();

            Container.ExecResult whichResult = container.execInContainer( "which", "cypher-shell" );
            Assertions.assertTrue( whichResult.getStdout().contains( expectedCypherShellPath ),
                                   "cypher-shell not on path" );
        }
    }

    @Test
    void testCanChangeWorkDir() throws Exception
    {
        try(GenericContainer container = createBasicContainer())
        {
            container.setWorkingDirectory( "/tmp" );
            container.setWaitStrategy( Wait.forHttp( "/" )
                                           .forPort( 7474 )
                                           .forStatusCode( 200 )
                                           .withStartupTimeout( Duration.ofSeconds( 60 ) ) );
            Assertions.assertDoesNotThrow( () -> container.start(),
                                           "Could not start neo4j from workdir NEO4J_HOME" );
        }
    }
}
