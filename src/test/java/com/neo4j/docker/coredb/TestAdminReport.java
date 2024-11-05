package com.neo4j.docker.coredb;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.WaitStrategies;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;


public class TestAdminReport
{
    private final Logger log = LoggerFactory.getLogger( TestAdminReport.class );
    private final String PASSWORD = "supersecretpassword";
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
    private static String reportDestinationFlag;

    @BeforeAll
    static void setCorrectPathFlagForVersion()
    {
        if( TestSettings.NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ) )
        {
            reportDestinationFlag = "--to";
        }
        else
        {
            reportDestinationFlag = "--to-path";
        }
    }

    private GenericContainer createNeo4jContainer( boolean asCurrentUser)
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID )
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withEnv( "NEO4J_AUTH", "neo4j/"+PASSWORD )
                .withExposedPorts( 7474, 7687 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .waitingFor(WaitStrategies.waitForNeo4jReady( PASSWORD ));
        if(asCurrentUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

    @ParameterizedTest(name = "ascurrentuser_{0}")
    @ValueSource(booleans = {true, false})
    void testMountToTmpReports(boolean asCurrentUser) throws Exception
    {
        try(GenericContainer container = createNeo4jContainer(asCurrentUser))
        {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            Path reportFolder = temporaryFolderManager.createFolderAndMountAsVolume(container, "/tmp/reports");
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", PASSWORD );

            Container.ExecResult execResult = container.execInContainer( "neo4j-admin-report" );
            verifyCreatesReport( reportFolder, execResult );
        }
    }

    @ParameterizedTest(name = "ascurrentuser_{0}")
    @ValueSource(booleans = {true, false})
    void testCanWriteReportToAnyMountedLocation_toPathWithEquals(boolean asCurrentUser) throws Exception
    {
        String reportFolderName = "reportAnywhere-"+ (asCurrentUser? "currentuser-":"defaultuser-") + "withEqualsArg-";
        verifyCanWriteToMountedLocation( asCurrentUser,
                                         reportFolderName,
                                         new String[]{"neo4j-admin-report", "--verbose", reportDestinationFlag+"=/reports"} );
    }

    @ParameterizedTest(name = "ascurrentuser_{0}")
    @ValueSource(booleans = {true, false})
    void testCanWriteReportToAnyMountedLocation_toPathWithSpace(boolean asCurrentUser) throws Exception
    {
        String reportFolderName = "reportAnywhere-"+ (asCurrentUser? "currentuser-":"defaultuser-") + "withSpaceArg-";
        verifyCanWriteToMountedLocation( asCurrentUser,
                                         reportFolderName,
                                         new String[]{"neo4j-admin-report", "--verbose", reportDestinationFlag, "/reports"} );
    }

    private void verifyCanWriteToMountedLocation(boolean asCurrentUser, String testFolderPrefix, String[] execArgs) throws Exception
    {
        try(GenericContainer container = createNeo4jContainer(asCurrentUser))
        {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            Path reportFolder = temporaryFolderManager.createFolderAndMountAsVolume(container, "/reports");
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", PASSWORD );
            Container.ExecResult execResult = container.execInContainer(execArgs);
            // log exec results, because the results of an exec don't get logged automatically.
            log.info( execResult.getStdout() );
            log.warn( execResult.getStderr() );
            verifyCreatesReport( reportFolder, execResult );
        }
    }

    @Test
    void shouldShowNeo4jAdminHelpText_whenCMD() throws Exception
    {
        try(GenericContainer container = createNeo4jContainer(false))
        {
            container.withCommand( "neo4j-admin-report", "--help" );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 20 ) );
            try
            {
                container.start();
            }
            catch ( ContainerLaunchException e )
            {
                // consume any failed to start exceptions
                log.warn( "Running 'neo4j-admin-report --help' caused the container to fail rather than " +
                          "successfully complete. This is allowable, so the test is not going to fail." );
            }
            verifyHelpText( container.getLogs(OutputFrame.OutputType.STDOUT),
                            container.getLogs(OutputFrame.OutputType.STDERR) );
        }
    }

    @Test
    void shouldShowNeo4jAdminHelpText_whenEXEC() throws Exception
    {
        try(GenericContainer container = createNeo4jContainer(false))
        {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            container.start();
            Container.ExecResult execResult = container.execInContainer( "neo4j-admin-report", "--help" );
            // log exec results, because the results of an exec don't get logged automatically.
            log.info( "STDOUT:\n" + execResult.getStdout() );
            log.warn( "STDERR:\n" + execResult.getStderr() );
            verifyHelpText( execResult.getStdout(), execResult.getStderr() );
        }
    }

    private void verifyCreatesReport( Path reportFolder,Container.ExecResult reportExecOut ) throws Exception
    {
        List<File> reports = Files.list( reportFolder )
                                  .map( Path::toFile )
                                  .filter( file -> ! file.isDirectory() )
                                  .toList();
        if( TestSettings.NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ) )
        {
            // for some reason neo4j-admin report prints jvm details to stderr
            String[] lines = reportExecOut.getStderr().split( "\n" );
            Assertions.assertEquals( 1, lines.length,
                                     "There were errors during report generation" );
            Assertions.assertTrue( lines[0].startsWith( "Selecting JVM" ),
                                   "There were unexpected error messages in the neo4j-admin report:\n"+reportExecOut.getStderr() );
        }
        else
        {
            Assertions.assertEquals( "", reportExecOut.getStderr(),
                                     "There were errors during report generation" );
        }
        Assertions.assertEquals( 1, reports.size(), "Expected exactly 1 report to be produced" );
        Assertions.assertFalse( reportExecOut.toString().contains( "No running instance of neo4j was found" ),
                                "neo4j-admin could not locate running neo4j database" );
    }

    private void verifyHelpText(String stdout, String stderr)
    {
        // in 4.4 the help text goes in stderr
        if( TestSettings.NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ) )
        {
            Assertions.assertTrue( stderr.contains(
                    "Produces a zip/tar of the most common information needed for remote assessments." ) );
            Assertions.assertTrue( stderr.contains( "USAGE" ) );
            Assertions.assertTrue( stderr.contains( "OPTIONS" ) );
        }
        else
        {
            Assertions.assertTrue( stdout.contains(
                    "Produces a zip/tar of the most common information needed for remote assessments." ) );
            Assertions.assertTrue( stdout.contains( "USAGE" ) );
            Assertions.assertTrue( stdout.contains( "OPTIONS" ) );
            Assertions.assertEquals( "", stderr, "There were errors when trying to get neo4j-admin-report help text" );
        }
    }
}
