package com.neo4j.docker.utils;

import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.stream.Collectors;

public class HostFileSystemOperations
{
	public static Path createTempFolderAndMountAsVolume( GenericContainer container, String hostFolderNamePrefix,
														 String containerMountPoint ) throws IOException
	{
        return null;
	}

    public static Path createTempFolderAndMountAsVolume( GenericContainer container, String hostFolderNamePrefix,
														 String containerMountPoint, Path parentFolder ) throws IOException
    {
        return null;
    }

    public static void mountHostFolderAsVolume(GenericContainer container, Path hostFolder, String containerMountPoint)
    {
    }

    public static Path createTempFolder( String folderNamePrefix ) throws IOException
    {
    	return null;
    }

    public static Path createTempFolder( String folderNamePrefix, Path parentFolder ) throws IOException
    {
        return null;
    }

    public static void setFileOwnerToCurrentUser(Path file) throws Exception
    {
        setFileOwnerTo( SetContainerUser.getNonRootUserString(), file );
    }

    public static void setFileOwnerToNeo4j(Path file) throws Exception
    {
        setFileOwnerTo( "7474:7474", file );
    }

    private static void setFileOwnerTo(String userAndGroup, Path ...files) throws Exception
    {
        // uses docker privileges to set file owner, since probably the current user is not a sudoer.

        // Using an nginx because it's easy to verify that the image started.
        try(GenericContainer container = new GenericContainer( DockerImageName.parse( "nginx:latest")))
        {
            container.withExposedPorts( 80 )
                     .waitingFor( Wait.forHttp( "/" ).withStartupTimeout( Duration.ofSeconds( 5 ) ) );
            for(Path p : files)
            {
                mountHostFolderAsVolume( container, p.getParent(), p.getParent().toAbsolutePath().toString() );
            }
            container.start();
            for(Path p : files)
            {
                Container.ExecResult x =
                        container.execInContainer( "chown", "-R", userAndGroup,
                                                   p.getParent().toAbsolutePath().toString() );
            }
            container.stop();
        }
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
                                    "Could not change owner of test file to "+userAndGroup+
                                    ". User needs to be in sudoers list. Error:\n" +
                                    errorMsg );
        }
        return;
    }
}
