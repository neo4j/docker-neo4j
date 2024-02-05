package com.neo4j.docker.coredb;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

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
    void testNoUnexpectedErrors() throws Exception
    {
        Assumptions.assumeFalse( TestSettings.BASE_OS == TestSettings.BaseOS.UBI8,
                                 "UBI8 based images are expected to have a warning in stderr" );
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
            Assertions.assertThrows( ContainerLaunchException.class, () -> container.start(),
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
    void testLicenseAcceptanceAvoidsWarning_evaluation() throws Exception
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
    void testCanChangeWorkDir() throws Exception
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.waitingFor( waitForNeo4jReady( "neo4j" ) );
            container.setWorkingDirectory( "/tmp" );
            Assertions.assertDoesNotThrow( container::start,
                                           "Could not start neo4j from workdir other than NEO4J_HOME" );
        }
    }

    @ParameterizedTest( name = "ShutsDownCorrectly_{0}" )
    @ValueSource( strings = {"SIGTERM", "SIGINT"} )
    void testShutsDownCleanly( String signal ) throws Exception
    {
        try ( GenericContainer container = createBasicContainer() )
        {
            container.withEnv( "NEO4J_AUTH", "none" )
                     .waitingFor( waitForNeo4jReady( "none" ) );
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
    void testContainerCanBeRestarted() throws InterruptedException, ExecutionException, TimeoutException
    {
        try ( GenericContainer container = createBasicContainer() )
        {

            container.waitingFor( waitForNeo4jReady( "none" ) );
            container.withEnv( "NEO4J_AUTH", "none" );

            container.start();
            Assertions.assertTrue( neo4jBoltAvailable( container ) );

            log.info( "Terminating container with SIGKILL signal" );
            terminateContainerWithSignal( container, "SIGKILL" );
            log.info( "Starting container" );
            startContainer( container );
            Assertions.assertTrue( neo4jBoltAvailable( container ) );
        }
    }

    private void terminateContainerWithSignal( GenericContainer container, String signal ) throws TimeoutException, ExecutionException, InterruptedException
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> task = executor.submit( () ->
                                          {
                                              while ( Boolean.TRUE.equals(
                                                      container.getDockerClient().inspectContainerCmd( container.getContainerId() ).exec().getState()
                                                               .getRunning() ) )
                                              {
                                                  container.getDockerClient().killContainerCmd( container.getContainerId() ).withSignal( signal ).exec();
                                              }
                                          } );

        try
        {
            task.get( 30, TimeUnit.SECONDS );
        }
        catch ( TimeoutException e )
        {
            throw new TimeoutException( "Coule not terminate container within timeout duration" );
        }
    }

    private void startContainer( GenericContainer container ) throws TimeoutException, ExecutionException, InterruptedException
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> task = executor.submit( () ->
                                          {
                                              while ( Boolean.FALSE.equals(
                                                      container.getDockerClient().inspectContainerCmd( container.getContainerId() ).exec().getState()
                                                               .getRunning() ) )
                                              {
                                                  container.getDockerClient().startContainerCmd( container.getContainerId() ).exec();
                                              }
                                          } );

        try
        {
            task.get( 60, TimeUnit.SECONDS );
        }
        catch ( TimeoutException e )
        {
            throw new TimeoutException( "Coule not start container within timeout duration" );
        }
    }

    private boolean neo4jBoltAvailable( GenericContainer container )
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> task = executor.submit( () ->
                                          {
                                              boolean browserAvailable = false;
                                              while ( !browserAvailable )
                                              {
                                                  int boltPort = Integer.parseInt( Arrays.stream(
                                                          container.getDockerClient().inspectContainerCmd( container.getContainerId() ).exec()
                                                                   .getNetworkSettings()
                                                                   .getPorts().getBindings()
                                                                   .get( new ExposedPort( 7687 ) ) ).toList().get( 0 ).getHostPortSpec() );
                                                  try
                                                  {
                                                      var url = new URL( "http://localhost:" + boltPort );
                                                      var urlConnection = (HttpURLConnection) url.openConnection();
                                                      urlConnection.connect();
                                                      var response = urlConnection.getResponseCode();
                                                      browserAvailable = response == HttpURLConnection.HTTP_OK;
                                                  }
                                                  catch ( IOException ex )
                                                  {
                                                      log.warn( "Contacting bolt exception {}", ex.getMessage() );
                                                  }
                                              }
                                          } );

        try
        {
            task.get( 120, TimeUnit.SECONDS );
            return true;
        }
        catch ( TimeoutException | ExecutionException | InterruptedException e )
        {
            log.error( "Bolt was not available within the timeout period {}", e.getMessage() );
            return false;
        }
    }
}
