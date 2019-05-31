package com.neo4j.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.sun.security.auth.module.UnixSystem;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.TempDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import utils.Neo4jVersion;
import utils.TestSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import static junit.framework.TestCase.assertTrue;

@Ignore
public class TestMounting
{
    private static Logger log = LoggerFactory.getLogger( TestMounting.class );
    private Neo4jContainer container;
    private Random rng = new Random(  );

    private void setupBasicContainer()
    {
        container = new Neo4jContainer( TestSettings.IMAGE_ID );
        container.withExposedPorts( 7474 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" );
    }

    private Path createHostFolderAndMountAsVolume(String hostFolderNamePrefix, String containerMountPoint ) throws IOException
    {
        String randomStr = String.format( "%4d", rng.nextInt(10000 ) );  // random 4 digit number
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

    private void setUserFlagToCurrentlyRunningUser()
    {
        UnixSystem fs = new UnixSystem();
        String uidgid = fs.getUid() + ":" + fs.getGid() ;
        container.withCreateContainerCmdModifier( (Consumer<CreateContainerCmd>) cmd -> cmd.withUser( uidgid ) );
    }

    @Test
    void testDumpConfig( ) throws Exception
    {
        setupBasicContainer();
        Path confMount = createHostFolderAndMountAsVolume( "conf-", "/conf" );
        setUserFlagToCurrentlyRunningUser();
        container.setWaitStrategy( null ); //otherwise will hang waiting for Neo4j to start
        container.withCommand( "dump-config" );
        container.start();

        Path expectedConfDumpFile = confMount.resolve( "neo4j.conf" );
        assertTrue( "dump-config did not dump the config file to disk", expectedConfDumpFile.toFile().exists() );
    }

    @Test
    void testCanMountDataVolumeWithDefaultUser( ) throws IOException
    {
        Assume.assumeTrue("User checks not valid before 3.1",
                          TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ) );
        setupBasicContainer();
        Path dataMount = createHostFolderAndMountAsVolume( "data-", "/data" );
        container.withEnv( "NEO4J_AUTH", "none" );
        container.start();

        // neo4j should now have started, so there'll be stuff in the data folder
        // we need to check that stuff is readable and owned by the correct user

        Assert.assertTrue( "Neo4j did not write /data/dbms folder",
                           dataMount.resolve( "dbms" ).toFile().exists());
//        /databases /tx-logs
        UserPrincipal x = Files.getOwner( dataMount, LinkOption.NOFOLLOW_LINKS );
//        UnixUserPrincipals x = Files.getOwner( dataMount, LinkOption.NOFOLLOW_LINKS );

    }
}
