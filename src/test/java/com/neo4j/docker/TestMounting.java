package com.neo4j.docker;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import com.neo4j.docker.utils.FolderMounting;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;

import java.io.IOException;
import java.nio.file.Path;


public class TestMounting
{
    private static Logger log = LoggerFactory.getLogger( TestMounting.class );
    private Neo4jContainer container;

    private void setupBasicContainer()
    {
        container = new Neo4jContainer( TestSettings.IMAGE_ID );
        container.withExposedPorts( 7474 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( "NEO4J_AUTH", "none" );
    }


    private void verifySingleFolder(Path folderToCheck, boolean shouldBeWritable)
    {
        String folderForDiagnostics = TestSettings.TEST_TMP_FOLDER.relativize( folderToCheck ).toString();

        Assert.assertTrue( "did not create "+folderForDiagnostics+" folder on host", folderToCheck.toFile().exists());
        if(shouldBeWritable)
        {
            Assert.assertTrue( "cannot read host "+folderForDiagnostics+" folder", folderToCheck.toFile().canRead());
            Assert.assertTrue( "cannot write to host "+folderForDiagnostics+" folder", folderToCheck.toFile().canWrite());
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
        Assert.assertTrue( "Neo4j did not write a debug.log file to logs",
                           logsMount.resolve( "debug.log" ).toFile().exists() );

        Assert.assertEquals( String.format( "The debug.log file should %sbe writable", shouldBeWritable?"":"not "),
                             shouldBeWritable,
                             logsMount.resolve( "debug.log" ).toFile().canWrite() );
    }


    @Test
    void testDumpConfig( ) throws Exception
    {
        setupBasicContainer();
        Path confMount = FolderMounting.createHostFolderAndMountAsVolume( container, "conf-", "/conf" );
        FolderMounting.setUserFlagToCurrentlyRunningUser(container);
        container.setWaitStrategy( null ); //otherwise will hang waiting for Neo4j to start
        container.withCommand( "dump-config" );
        container.start();

        Path expectedConfDumpFile = confMount.resolve( "neo4j.conf" );
        Assert.assertTrue( "dump-config did not dump the config file to disk", expectedConfDumpFile.toFile().exists() );
    }

    // ==== just mounting /data volume

    @Test
    void testCanMountDataVolumeWithDefaultUser( ) throws IOException
    {
        Assume.assumeTrue("User checks not valid before 3.1",
                          TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ) );
        setupBasicContainer();
        Path dataMount = FolderMounting.createHostFolderAndMountAsVolume( container, "data-", "/data" );
        container.start();

        // neo4j should now have started, so there'll be stuff in the data folder
        // we need to check that stuff is readable and owned by the correct user
        verifyDataFolderContentsArePresentOnHost( dataMount, false );
    }

    @Test
    void testCanMountDataVolumeWithSpecifiedUser( ) throws IOException
    {
        Assume.assumeTrue("User checks not valid before 3.1",
                          TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ) );
        setupBasicContainer();
        FolderMounting.setUserFlagToCurrentlyRunningUser(container);
        Path dataMount = FolderMounting.createHostFolderAndMountAsVolume(container,  "data-", "/data" );
        container.start();

        verifyDataFolderContentsArePresentOnHost( dataMount, true );
    }

    // ==== just mounting /logs volume


    @Test
    void testCanMountLogsVolumeWithDefaultUser( ) throws IOException
    {
        Assume.assumeTrue("User checks not valid before 3.1",
                          TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ) );
        setupBasicContainer();
        Path logsMount = FolderMounting.createHostFolderAndMountAsVolume(container,  "logs-", "/logs" );
        container.start();

        verifyLogsFolderContentsArePresentOnHost( logsMount, false );
    }

    @Test
    void testCanMountLogsVolumeWithSpecifiedUser( ) throws IOException
    {
        Assume.assumeTrue("User checks not valid before 3.1",
                          TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ) );
        setupBasicContainer();
        FolderMounting.setUserFlagToCurrentlyRunningUser(container);
        Path logsMount = FolderMounting.createHostFolderAndMountAsVolume(container,  "logs-", "/logs" );
        container.start();

        verifyLogsFolderContentsArePresentOnHost( logsMount, true );
    }

    // ==== mounting /data and /logs volumes

    @Test
    void testCanMountDataAndLogsVolumesWithDefaultUser( ) throws IOException
    {
        Assume.assumeTrue("User checks not valid before 3.1",
                          TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ) );
        setupBasicContainer();
        Path dataMount = FolderMounting.createHostFolderAndMountAsVolume(container,  "data-", "/data" );
        Path logsMount = FolderMounting.createHostFolderAndMountAsVolume(container,  "logs-", "/logs" );
        container.start();

        // neo4j should now have started, so there'll be stuff in the data folder
        // we need to check that stuff is readable and owned by the correct user
        verifyDataFolderContentsArePresentOnHost( dataMount, false );
        verifyLogsFolderContentsArePresentOnHost( logsMount, false );
    }

    @Test
    void testCanMountDataAndLogsVolumesWithSpecifiedUser( ) throws IOException
    {
        Assume.assumeTrue("User checks not valid before 3.1",
                          TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ) );
        setupBasicContainer();
        FolderMounting.setUserFlagToCurrentlyRunningUser(container);
        Path dataMount = FolderMounting.createHostFolderAndMountAsVolume(container,  "data-", "/data" );
        Path logsMount = FolderMounting.createHostFolderAndMountAsVolume(container,  "logs-", "/logs" );
        container.start();

        verifyDataFolderContentsArePresentOnHost( dataMount, true );
        verifyLogsFolderContentsArePresentOnHost( logsMount, true );
    }
}
