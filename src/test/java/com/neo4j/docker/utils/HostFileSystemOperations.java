package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;

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

    public static void emptyTestTemporaryFolder()
    {
        Path mountPoint = Paths.get("/", "deleteme");
        // using debian image to avoid an entrypoint script introducing weird ownership and permission problems
        GenericContainer container = new GenericContainer<>("debian:buster-slim")
                .withStartupCheckStrategy( new OneShotStartupCheckStrategy()
                                                   .withTimeout( Duration.ofSeconds( 90 ) ) )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .withCommand( "find", mountPoint.toString(), "-not", "-name", mountPoint.getFileName().toString(), "-delete" );
        mountHostFolderAsVolume( container, TestSettings.TEST_TMP_FOLDER, mountPoint.toString() );
        try
        {
            container.start();
        }
        catch(Exception e)
        {
            log.error( "couldn't delete temporary folder", e );
        }
        finally
        {
            container.stop();
        }
    }
}
