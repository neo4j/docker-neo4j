package com.neo4j.docker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;


public class TestMounting
{
    private static Logger log = LoggerFactory.getLogger( TestMounting.class );
    private Neo4jContainer container;
    private static Random rng = new Random(  );

    private void setupBasicContainer()
    {
        container = new Neo4jContainer( TestSettings.IMAGE_ID );
        container.withExposedPorts( 7474 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withEnv( "NEO4J_AUTH", "none" );
    }

    private Path createHostFolderAndMountAsVolume( String hostFolderNamePrefix, String containerMountPoint ) throws IOException
    {
        String randomStr = String.format( "%04d", rng.nextInt(10000 ) );  // random 4 digit number
        Path hostFolder = TestSettings.TEST_TMP_FOLDER.resolve( hostFolderNamePrefix + randomStr);
        try
        {
            Files.createDirectories( hostFolder );
        }
        catch ( IOException e )
        {
            log.error( "could not create directory: " + hostFolder.toAbsolutePath().toString() );
            e.printStackTrace();
            throw e;
        }
        container.withFileSystemBind( hostFolder.toAbsolutePath().toString(),
                                      containerMountPoint,
                                      BindMode.READ_WRITE );

        return hostFolder;
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
        setupBasicContainer();
        Path confMount = createHostFolderAndMountAsVolume( "conf-", "/conf" );
        SetContainerUser.currentlyRunningUser( container );
        container.setWaitStrategy( null ); //otherwise will hang waiting for Neo4j to start
        container.withCommand( "dump-config" );
        container.start();

        Path expectedConfDumpFile = confMount.resolve( "neo4j.conf" );
        Assertions.assertTrue( expectedConfDumpFile.toFile().exists(), "dump-config did not dump the config file to disk" );
    }

    // ==== just mounting /data volume

    @Test
    void testCanMountDataVolumeWithDefaultUser( ) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );
        setupBasicContainer();
        Path dataMount = createHostFolderAndMountAsVolume( "data-", "/data" );
        container.start();

        // neo4j should now have started, so there'll be stuff in the data folder
        // we need to check that stuff is readable and owned by the correct user
        verifyDataFolderContentsArePresentOnHost( dataMount, false );
    }

    @Test
    void testCanMountDataVolumeWithSpecifiedUser( ) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );
        setupBasicContainer();
        SetContainerUser.currentlyRunningUser( container );
        Path dataMount = createHostFolderAndMountAsVolume(  "data-", "/data" );
        container.start();

        verifyDataFolderContentsArePresentOnHost( dataMount, true );
    }

    // ==== just mounting /logs volume


    @Test
    void testCanMountLogsVolumeWithDefaultUser( ) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );
        setupBasicContainer();
        Path logsMount = createHostFolderAndMountAsVolume(  "logs-", "/logs" );
        container.start();

        verifyLogsFolderContentsArePresentOnHost( logsMount, false );
    }

    @Test
    void testCanMountLogsVolumeWithSpecifiedUser( ) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );
        setupBasicContainer();
        SetContainerUser.currentlyRunningUser( container );
        Path logsMount = createHostFolderAndMountAsVolume(  "logs-", "/logs" );
        container.start();

        verifyLogsFolderContentsArePresentOnHost( logsMount, true );
    }

    // ==== mounting /data and /logs volumes

    @Test
    void testCanMountDataAndLogsVolumesWithDefaultUser( ) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );
        setupBasicContainer();
        Path dataMount = createHostFolderAndMountAsVolume(  "data-", "/data" );
        Path logsMount = createHostFolderAndMountAsVolume(  "logs-", "/logs" );
        container.start();

        // neo4j should now have started, so there'll be stuff in the data folder
        // we need to check that stuff is readable and owned by the correct user
        verifyDataFolderContentsArePresentOnHost( dataMount, false );
        verifyLogsFolderContentsArePresentOnHost( logsMount, false );
    }

    @Test
    void testCanMountDataAndLogsVolumesWithSpecifiedUser( ) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );
        setupBasicContainer();
        SetContainerUser.currentlyRunningUser( container );
        Path dataMount = createHostFolderAndMountAsVolume(  "data-", "/data" );
        Path logsMount = createHostFolderAndMountAsVolume(  "logs-", "/logs" );
        container.start();

        verifyDataFolderContentsArePresentOnHost( dataMount, true );
        verifyLogsFolderContentsArePresentOnHost( logsMount, true );
    }
}
