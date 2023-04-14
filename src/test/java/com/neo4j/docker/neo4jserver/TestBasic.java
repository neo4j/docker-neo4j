package com.neo4j.docker.neo4jserver;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.neo4j.docker.neo4jserver.configurations.Configuration;
import com.neo4j.docker.neo4jserver.configurations.Setting;
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
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

public class TestBasic
{
    private static Logger log = LoggerFactory.getLogger( TestBasic.class );

    private GenericContainer createBasicContainer()
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        return container;
    }

    @Test
    void testListensOn7474()
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "neo4j" );
            container.start();
            Assertions.assertTrue( container.isRunning() );
            String stdout = container.getLogs();
            Assertions.assertFalse( stdout.contains( "DEBUGGING ENABLED" ),
                                    "Debugging was enabled even though we did not set debugging" );
        }
    }

    @Test
    void testNoUnexpectedErrors() throws Exception
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "neo4j" );
            container.start();
            Assertions.assertTrue( container.isRunning() );

            String stderr = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertEquals( "", stderr,
                                     "Unexpected errors in stderr from container!\n" +
                                     stderr );
        }
    }

    @Test
    void testLicenseAcceptanceRequired_Neo4jServer()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3, 3, 0 ) ),
                                "No license checks before version 3.3.0" );
        testLicenseAcceptanceRequired( TestSettings.IMAGE_ID );
    }

    @Test
    void testLicenseAcceptanceRequired_Neo4jAdmin()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_440 ),
                                "No Neo4j admin image before version 4.4.0" );
        testLicenseAcceptanceRequired( TestSettings.ADMIN_IMAGE_ID );
    }

    private void testLicenseAcceptanceRequired( DockerImageName image )
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition" );

        String logsOut;
        try ( GenericContainer container = new GenericContainer( image )
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
                                "No license checks for community edition" );
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5, 0, 0 ) ),
                                "No unified license acceptance method before 5.0.0" );
        try ( GenericContainer container = createBasicContainer() )
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "neo4j" );
            container.start();
            Assertions.assertTrue( container.isRunning() );

            String stdout = container.getLogs( OutputFrame.OutputType.STDOUT );
            Assertions.assertTrue( stdout.contains( "The license agreement was accepted with environment variable " +
                                                    "NEO4J_ACCEPT_LICENSE_AGREEMENT=yes when the Software was started." ),
                                   "Neo4j did not register that the license was agreed to." );
        }
    }

    @Test
    void testLicenseAcceptanceAvoidsWarning_evaluation() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition" );
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5, 0, 0 ) ),
                                "No unified license acceptance method before 5.0.0" );
        try ( GenericContainer container = createBasicContainer() )
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "eval" );
            StartupDetector.makeContainerWaitForNeo4jReady( container, "neo4j" );
            container.start();
            Assertions.assertTrue( container.isRunning() );

            String stdout = container.getLogs( OutputFrame.OutputType.STDOUT );
            Assertions.assertTrue( stdout.contains( "The license agreement was accepted with environment variable " +
                                                    "NEO4J_ACCEPT_LICENSE_AGREEMENT=eval when the Software was started." ),
                                   "Neo4j did not register that the evaluation license was agreed to." );
        }
    }

    @Test
    void testCypherShellOnPath() throws Exception
    {
        String expectedCypherShellPath = "/var/lib/neo4j/bin/cypher-shell";
        try ( GenericContainer container = createBasicContainer() )
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "neo4j" );
            container.start();

            Container.ExecResult whichResult = container.execInContainer( "which", "cypher-shell" );
            Assertions.assertTrue( whichResult.getStdout().contains( expectedCypherShellPath ),
                                   "cypher-shell not on path" );
        }
    }

    @Test
    void testCanChangeWorkDir() throws Exception
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            StartupDetector.makeContainerWaitForNeo4jReady( container, "neo4j" );
            container.setWorkingDirectory( "/tmp" );
            Assertions.assertDoesNotThrow( () -> container.start(),
                                           "Could not start neo4j from workdir other than NEO4J_HOME" );
        }
    }

    @ParameterizedTest( name = "ShutsDownCorrectly_{0}" )
    @ValueSource( strings = {"SIGTERM", "SIGINT"} )
    void testShutsDownCleanly( String signal ) throws Exception
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.withEnv( "NEO4J_AUTH", "none" );
            StartupDetector.makeContainerWaitForNeo4jReady( container, "none" );
            // sets sigterm as the stop container signal
            container.withCreateContainerCmdModifier( (Consumer<CreateContainerCmd>) cmd ->
                    cmd.withStopSignal( signal )
                       .withStopTimeout( 60 ) );
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

    @Test
    void testStartsWhenDebuggingEnabled()
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.withEnv( "NEO4J_DEBUG", "true" );
            container.start();
            Assertions.assertTrue( container.isRunning() );
        }
    }

    @Test
    void testContainerIsHealthyWhenNeo4jIsListeningAtPort7474()
    {
        try ( var container = createBasicContainer() )
        {
            container.setWaitStrategy( Wait.forHealthcheck() );
            container.start();

            Assertions.assertTrue( container.isRunning() );
            Assertions.assertEquals( "healthy", container.getCurrentContainerInfo().getState().getHealth().getStatus() );
        }
    }

    @Test
    void testContainerIsUnhealthyWhenNeo4jIsNotListeningAtPort7474()
    {
        try ( var container = createBasicContainer() )
        {
            Map<Setting,Configuration> confNames = Configuration.getConfigurationNameMap();
            container.withEnv( confNames.get( Setting.HTTP_LISTEN_ADDRESS ).envName, ":4747" );

            container.setWaitStrategy( Wait.forHealthcheck() );
            Assertions.assertThrows( ContainerLaunchException.class, container::start );
            Assertions.assertTrue( container.isRunning() );

            Assertions.assertEquals( "unhealthy", container.getCurrentContainerInfo().getState().getHealth().getStatus() );
        }
    }
}
