package com.neo4j.docker;

import com.neo4j.docker.utils.HostFileSystemOperations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;


public class TestMounting
{
    private static Logger log = LoggerFactory.getLogger( TestMounting.class );
    private Neo4jContainer container;


    static Stream<Arguments> defaultUseFlagrSecurePermissionsFlag()
    {
        // it would be nice if JUnit5 had some way of providing all combinations of some collections as test parameters
        return Stream.of(
                Arguments.arguments( false, false ),
                Arguments.arguments( false, true  ),
                Arguments.arguments(  true, false ),
                Arguments.arguments(  true, true  ));
    }

    private void setupBasicContainer( boolean asCurrentUser, boolean setSecurityPermissionsFlag )
    {
        container = new Neo4jContainer( TestSettings.IMAGE_ID );
        container.withExposedPorts( 7474 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withEnv( "NEO4J_AUTH", "none" );

        if(asCurrentUser)
        {
            SetContainerUser.currentlyRunningUser( container );
        }
        if(setSecurityPermissionsFlag)
        {
            container.withEnv( "SECURE_FILE_PERMISSIONS", "yes" );
        }
    }

    private void verifySingleFolder(Path folderToCheck, boolean shouldBeWritable)
    {
        String folderForDiagnostics = TestSettings.TEST_TMP_FOLDER.relativize( folderToCheck ).toString();

        Assertions.assertTrue( folderToCheck.toFile().exists(), "did not create " + folderForDiagnostics + " folder on host" );
        if(shouldBeWritable)
        {
            Assertions.assertTrue( folderToCheck.toFile().canRead(), "cannot read host "+folderForDiagnostics+" folder" );
            Assertions.assertTrue(folderToCheck.toFile().canWrite(),  "cannot write to host "+folderForDiagnostics+" folder" );
        }
    }

    private void verifyDataFolderContentsArePresentOnHost( Path dataMount, boolean shouldBeWritable )
    {
        verifySingleFolder( dataMount.resolve( "dbms" ), shouldBeWritable );
        verifySingleFolder( dataMount.resolve( "databases" ), shouldBeWritable );

        if(TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ))
        {
            verifySingleFolder( dataMount.resolve( "tx-logs" ), shouldBeWritable );
        }
    }

    private void verifyLogsFolderContentsArePresentOnHost( Path logsMount, boolean shouldBeWritable )
    {
        verifySingleFolder( logsMount, shouldBeWritable );
        Assertions.assertTrue( logsMount.resolve( "debug.log" ).toFile().exists(),
                               "Neo4j did not write a debug.log file to "+logsMount.toString() );
        Assertions.assertEquals( shouldBeWritable,
                                 logsMount.resolve( "debug.log" ).toFile().canWrite(),
                                 String.format( "The debug.log file should %sbe writable", shouldBeWritable?"":"not ") );
    }


    @Test
    void testDumpConfig( ) throws Exception
    {
        setupBasicContainer( true, false );
        Path confMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "conf-", "/conf" );
        container.setWaitStrategy( null );
        container.withCommand( "dump-config" );
        container.start();
        container.stop();

        Path expectedConfDumpFile = confMount.resolve( "neo4j.conf" );
        Assertions.assertTrue( expectedConfDumpFile.toFile().exists(), "dump-config did not dump the config file to "+confMount.toString() );
    }


    @ParameterizedTest(name = "asUser={0}, secureFlag={1}")
    @MethodSource("defaultUseFlagrSecurePermissionsFlag")
    void testCanMountJustDataFolder(boolean asCurrentUser, boolean setSecurityPermissionsFlag) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );

        setupBasicContainer( asCurrentUser, setSecurityPermissionsFlag );
        Path dataMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "data-", "/data" );
        container.start();

        // neo4j should now have started, so there'll be stuff in the data folder
        // we need to check that stuff is readable and owned by the correct user
        verifyDataFolderContentsArePresentOnHost( dataMount, asCurrentUser );
    }

    @ParameterizedTest(name = "asUser={0}, secureFlag={1}")
    @MethodSource("defaultUseFlagrSecurePermissionsFlag")
    void testCanMountJustLogsFolder(boolean asCurrentUser, boolean setSecurityPermissionsFlag) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );

        setupBasicContainer( asCurrentUser, setSecurityPermissionsFlag );
        Path logsMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "logs-", "/logs" );
        container.start();

        verifyLogsFolderContentsArePresentOnHost( logsMount, asCurrentUser );
    }

    @ParameterizedTest(name = "asUser={0}, secureFlag={1}")
    @MethodSource("defaultUseFlagrSecurePermissionsFlag")
    void testCanMountDataAndLogsFolder(boolean asCurrentUser, boolean setSecurityPermissionsFlag) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );

        setupBasicContainer( asCurrentUser, setSecurityPermissionsFlag );
        Path dataMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "data-", "/data" );
        Path logsMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "logs-", "/logs" );
        container.start();

        verifyDataFolderContentsArePresentOnHost( dataMount, asCurrentUser );
        verifyLogsFolderContentsArePresentOnHost( logsMount, asCurrentUser );
    }

}
