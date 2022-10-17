package com.neo4j.docker.neo4jserver;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.StartupDetector;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.function.Consumer;

import org.neo4j.driver.Record;

public class TestBasic
{
    private static Logger log = LoggerFactory.getLogger( TestBasic.class );

    private GenericContainer createBasicContainer()
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        return container;
    }

    @Test
    void testListensOn7474()
    {
        try(GenericContainer container = createBasicContainer())
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "none" );
            container.start();
            Assertions.assertTrue( container.isRunning() );
        }
    }

    @Test
    void testEditionActuallyCorrect()
    {
        try(GenericContainer container = createBasicContainer())
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "none" );
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            Record dbms = dbio.runCypherQuery( "neo4j", "none", "CALL dbms.components();" ).get( 0 );
            String actualEdition = dbms.get( "edition" ).asString();
            switch(TestSettings.EDITION)
            {
                case ENTERPRISE -> Assertions.assertEquals( "enterprise", actualEdition );
                case COMMUNITY -> Assertions.assertEquals( "community", actualEdition );
            }
        }
    }

    @Test
    void testNoUnexpectedErrors() throws Exception
    {
        try(GenericContainer container = createBasicContainer())
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "none" );
            container.start();
            Assertions.assertTrue( container.isRunning() );

			String stderr = container.getLogs(OutputFrame.OutputType.STDERR);
            Assertions.assertEquals( "", stderr,
                                     "Unexpected errors in stderr from container!\n" +
                                     stderr );
        }
    }

    @Test
    void testLicenseAcceptanceRequired_Neo4jServer()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,3,0 ) ),
                                "No license checks before version 3.3.0");
        testLicenseAcceptance( TestSettings.IMAGE_ID );
    }

    @Test
    void testLicenseAcceptanceRequired_Neo4jAdmin()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4,4,0 ) ),
                                "No Neo4j admin image before version 4.4.0");
        testLicenseAcceptance( TestSettings.ADMIN_IMAGE_ID );
    }

    private void testLicenseAcceptance(String image)
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition");

        String logsOut;
        try(GenericContainer container = new GenericContainer( image )
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
    void testLicenseAcceptanceAvoidsWarning() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition");
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,0,0 ) ),
                                "No unified license acceptance method before 5.0.0");
        try(GenericContainer container = createBasicContainer())
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "none" );
            container.start();
            Assertions.assertTrue( container.isRunning() );

            String stdout = container.getLogs(OutputFrame.OutputType.STDOUT);
            Assertions.assertTrue( stdout.contains( "The license agreement was accepted with environment variable " +
                                                    "NEO4J_ACCEPT_LICENSE_AGREEMENT=yes when the Software was started." ),
            "Neo4j did not register that the license was agreed to.");
        }
    }

    @Test
    void testCypherShellOnPath() throws Exception
    {
        String expectedCypherShellPath = "/var/lib/neo4j/bin/cypher-shell";
        try(GenericContainer container = createBasicContainer())
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "none" );
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
            StartupDetector.makeContainerWaitForNeo4jReady( container, "none" );
            container.setWorkingDirectory( "/tmp" );
            Assertions.assertDoesNotThrow( () -> container.start(),
                                           "Could not start neo4j from workdir other than NEO4J_HOME" );
        }
    }

    @ParameterizedTest(name = "ShutsDownCorrectly_{0}")
    @ValueSource(strings = {"SIGTERM", "SIGINT"})
    void verifyShutsDownCleanly(String signal) throws Exception
    {
        try(GenericContainer container = createBasicContainer())
        {
            StartupDetector.makeContainerWaitForNeo4jReady(container, "none");
            // sets sigterm as the stop container signal
            container.withCreateContainerCmdModifier((Consumer<CreateContainerCmd>) cmd ->
                    cmd.withStopSignal( signal )
                       .withStopTimeout( 60 ));
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", "none" );
            log.info( "issuing container stop command " + signal );
            container.getDockerClient().stopContainerCmd( container.getContainerId() ).exec();
            String stdout = container.getLogs();
            Assertions.assertTrue( stdout.contains( "Neo4j Server shutdown initiated by request" ),
                                   "clean shutdown not initiated by " + signal );
            Assertions.assertTrue( stdout.contains( "Stopped." ),
                                   "clean shutdown not initiated by " + signal );
        }
    }
}
