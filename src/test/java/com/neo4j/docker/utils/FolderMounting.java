package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.sun.security.auth.module.UnixSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Neo4jContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Consumer;

public class FolderMounting
{
    private static Random rng = new Random(  );
    private static Logger log = LoggerFactory.getLogger( FolderMounting.class);

    public static Path createHostFolderAndMountAsVolume( Neo4jContainer container, String hostFolderNamePrefix, String containerMountPoint ) throws IOException
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

    public static void setUserFlagToCurrentlyRunningUser( Neo4jContainer container )
    {
        UnixSystem fs = new UnixSystem();
        String uidgid = fs.getUid() + ":" + fs.getGid() ;
        container.withCreateContainerCmdModifier( (Consumer<CreateContainerCmd>) cmd -> cmd.withUser( uidgid ) );
    }
}
