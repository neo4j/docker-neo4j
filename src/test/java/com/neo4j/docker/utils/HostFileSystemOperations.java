package com.neo4j.docker.utils;

import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Collectors;

public class HostFileSystemOperations
{
    private static Logger log = LoggerFactory.getLogger( HostFileSystemOperations.class);
    private static Random rng = new Random(  );

	public static Path createTempFolderAndMountAsVolume( GenericContainer container, String hostFolderNamePrefix,
														 String containerMountPoint ) throws IOException
	{
		return createTempFolderAndMountAsVolume( container, hostFolderNamePrefix, containerMountPoint,
												 TestSettings.TEST_TMP_FOLDER );
	}

    public static Path createTempFolderAndMountAsVolume( GenericContainer container, String hostFolderNamePrefix,
														 String containerMountPoint, Path parentFolder ) throws IOException
    {
        String randomStr = String.format( "%04d", rng.nextInt(10000 ) );  // random 4 digit number
        Path hostFolder = parentFolder.resolve( hostFolderNamePrefix + randomStr);
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
        log.info( "Created folder "+hostFolder.toString() );
        mountHostFolderAsVolume( container, hostFolder, containerMountPoint );
        return hostFolder;
    }

    public static void mountHostFolderAsVolume(GenericContainer container, Path hostFolder, String containerMountPoint)
    {
        container.withFileSystemBind( hostFolder.toAbsolutePath().toString(),
                                      containerMountPoint,
                                      BindMode.READ_WRITE );
    }

    public static Path createTempFolder( String folderNamePrefix ) throws IOException
    {
    	return createTempFolder( folderNamePrefix, TestSettings.TEST_TMP_FOLDER );
    }

    public static Path createTempFolder( String folderNamePrefix, Path parentFolder ) throws IOException
    {
        String randomStr = String.format( "%04d", rng.nextInt(10000 ) );  // random 4 digit number
        Path hostFolder = parentFolder.resolve( folderNamePrefix + randomStr);
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

        return hostFolder;
    }

    public static void setFileOwnerToCurrentUser(Path file) throws Exception
    {
        setFileOwnerTo( file, SetContainerUser.getNonRootUserString() );
    }

    public static void setFileOwnerToNeo4j(Path file) throws Exception
    {
        setFileOwnerTo( file, "7474:7474" );
    }

    private static void setFileOwnerTo(Path file, String userAndGroup) throws Exception
    {
        ProcessBuilder pb = new ProcessBuilder( "chown", userAndGroup, file.toAbsolutePath().toString() ).redirectErrorStream( true );
        Process proc = pb.start();
        proc.waitFor();
        if(proc.exitValue() != 0)
        {
            String errorMsg = new BufferedReader( new InputStreamReader( proc.getInputStream() ) )
                    .lines()
                    .collect( Collectors.joining() );
            // if we cannot set up test conditions properly, abort test but don't register a test failure.
            Assumptions.assumeTrue( false,
                                    "Could not change owner of test file to 7474. User needs to be in sudoers list. Error:\n" +
                                    errorMsg );
        }
        return;
    }
}
