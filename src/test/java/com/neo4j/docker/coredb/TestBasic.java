package com.neo4j.docker.coredb;

import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.neo4j.docker.utils.Network.getUniqueHostPort;
import static com.neo4j.docker.utils.WaitStrategies.waitForBoltReady;
import static com.neo4j.docker.utils.WaitStrategies.waitForNeo4jReady;

public class TestBasic
{
    private static Logger log = LoggerFactory.getLogger( TestBasic.class );
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    private GenericContainer createBasicContainer()
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        return container;
    }

    @Test
    void testListensOn7687()
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.waitingFor( waitForNeo4jReady( "neo4j" ) );
            container.start();
            Assertions.assertTrue( container.isRunning() );
            String stdout = container.getLogs();
            Assertions.assertFalse( stdout.contains( "DEBUGGING ENABLED" ),
                                    "Debugging was enabled even though we did not set debugging" );
        }
    }

    @Test
    void testNoUnexpectedErrors()
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.waitingFor( waitForNeo4jReady( "neo4j" ) );
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
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition" );

        String logsOut;
        try ( GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID )
                .withLogConsumer( new Slf4jLogConsumer( log ) ) )
        {
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 30 ) );
            // container start should fail due to licensing.
            Assertions.assertThrows( ContainerLaunchException.class, container::start,
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
    void testLicenseAcceptanceAvoidsWarning()
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition" );
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5, 0, 0 ) ),
                                "No unified license acceptance method before 5.0.0" );
        try ( GenericContainer container = createBasicContainer() )
        {
            container.waitingFor( waitForNeo4jReady( "neo4j" ) );
            container.start();
            Assertions.assertTrue( container.isRunning() );

            String stdout = container.getLogs( OutputFrame.OutputType.STDOUT );
            Assertions.assertTrue( stdout.contains( "The license agreement was accepted with environment variable " +
                                                    "NEO4J_ACCEPT_LICENSE_AGREEMENT=yes when the Software was started." ),
                                   "Neo4j did not register that the license was agreed to." );
        }
    }

    @Test
    void testLicenseAcceptanceAvoidsWarning_evaluation()
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "No license checks for community edition" );
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5, 0, 0 ) ),
                                "No unified license acceptance method before 5.0.0" );
        try ( GenericContainer container = createBasicContainer() )
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "eval" )
                     .waitingFor( waitForNeo4jReady( "neo4j" ) );
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
            container.waitingFor( waitForNeo4jReady( "neo4j" ) );
            container.start();

            Container.ExecResult whichResult = container.execInContainer( "which", "cypher-shell" );
            Assertions.assertTrue( whichResult.getStdout().contains( expectedCypherShellPath ),
                                   "cypher-shell not on path" );
        }
    }

    @Test
    void testCanChangeWorkDir()
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.waitingFor( waitForNeo4jReady( "neo4j" ) );
            container.setWorkingDirectory( "/tmp" );
            Assertions.assertDoesNotThrow( container::start,
                                           "Could not start neo4j from workdir other than NEO4J_HOME" );
        }
    }

    @Test
    void testPackagingInfoContainsDocker() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5, 0, 0 ) ),
                "No packaging_info file before 5.0.0" );
        try ( GenericContainer container = createBasicContainer() )
        {
            container.waitingFor( waitForNeo4jReady( "neo4j" ) );
            container.start();
            String packagingInfo = container.execInContainer("cat", "/var/lib/neo4j/packaging_info").getStdout();
            List<String> actualPackageType = Stream.of(packagingInfo.split( "\n" ))
                    .filter(line -> line.startsWith("Package Type:"))
                    .toList();
            Assertions.assertEquals(1, actualPackageType.size(),
                    "There should only be 1 Package Type declarations in the packaging_info:\n"+actualPackageType);
            Assertions.assertEquals("Package Type: docker " + TestSettings.BASE_OS.name().toLowerCase(),
                    actualPackageType.get(0), "Docker packaging type is missing from packaging info file");
        }
    }

    @ParameterizedTest( name = "ShutsDownCorrectly_{0}" )
    @ValueSource( strings = {"SIGTERM", "SIGINT"} )
    void testShutsDownCleanly( String signal )
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.withEnv( "NEO4J_AUTH", "none" )
                     .waitingFor( waitForNeo4jReady( "none" ) );
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", "none" );
            try(KillContainerCmd kill = container.getDockerClient().killContainerCmd(container.getContainerId());
                StopContainerCmd stop = container.getDockerClient().stopContainerCmd(container.getContainerId()))
            {
                log.info( "issuing container stop command " + signal );
                kill.withSignal(signal).exec();
                log.info("waiting for container to shut down.");
                stop.withTimeout(30).exec();
            }
            String stdout = container.getLogs();
            Assertions.assertTrue( stdout.contains( "Neo4j Server shutdown initiated by request" ),
                                   "clean shutdown not initiated by " + signal + "\n" + stdout);
            Assertions.assertTrue( stdout.contains( "Stopped." ),
                                   "clean shutdown not initiated by " + signal + "\n" + stdout);
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

    /*
        This test emulates a termination of the Docker Desktop or Docker Engine by the user. In these
        cases the container receives a SIGKILL signal and neo4j doesn't have time to clean up the PID
        file. In turn this causes the container to not be re-startable.
     */
    @Test
    void testContainerCanBeRestartedAfterUnexpectedTermination() throws IOException
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            int boltHostPort = getUniqueHostPort();
            int browserHostPort = getUniqueHostPort();

            container.waitingFor( waitForBoltReady() );
            container.withEnv( "NEO4J_AUTH", "none" );

            // Ensuring host ports are constant with container restarts
            container.setPortBindings( List.of( browserHostPort + ":7474", boltHostPort + ":7687" ) );

            container.start();

            // Terminating container with a SIGKILL signal to emulate docker engine (docker desktop) being terminated by user.
            // This also keeps around the container unlike GenericContainer::stop(), which cleans up everything
            log.info( "Terminating container with SIGKILL signal" );
            container.getDockerClient().killContainerCmd( container.getContainerId() ).withSignal( "SIGKILL" ).exec();

            // Restarting the container with DockerClient because the GenericContainer was not terminates and GenericContainer::start()
            // does not work
            log.info( "Starting container" );
            container.getDockerClient().startContainerCmd( container.getContainerId() ).exec();

            // Applying the Waiting strategy to ensure container is correctly running, because DockerClient does not check
            waitForBoltReady().waitUntilReady( container );
        }
    }

    @Test
    void testExtensionScriptIsExecuted() throws IOException
    {
        Path scriptFolder = temporaryFolderManager.createFolder("extension_script");
        Path script = scriptFolder.resolve("startscript.sh");
        Files.writeString(script, "#!/bin/bash\n\necho \"SCRIPT EXECUTED!\"");

        try ( GenericContainer container = createBasicContainer() )
        {
            temporaryFolderManager.mountHostFolderAsVolume(container, scriptFolder, "/extension");
            container.waitingFor(waitForBoltReady())
                    .withEnv("EXTENSION_SCRIPT", "/extension/startscript.sh");
            container.start();
            String logs = container.getLogs(OutputFrame.OutputType.STDOUT);
            Assertions.assertTrue(logs.contains("SCRIPT EXECUTED!"), "The extension script did not get executed");
        }
    }
}
