package com.neo4j.docker.utils;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TemporaryFolderManager implements AfterAllCallback
{
    private static final Logger log = LoggerFactory.getLogger( TemporaryFolderManager.class );
    // if we ever run parallel tests, random number generator and
    // list of folders to compress need to be made thread safe
    private Random rng = new Random(  );
    private List<Path> toCompressAfterAll = new ArrayList<>();
    private final Path testOutputParentFolder;

    public TemporaryFolderManager( )
    {
        this(TestSettings.TEST_TMP_FOLDER);
    }
    public TemporaryFolderManager( Path testOutputParentFolder )
    {
        this.testOutputParentFolder = testOutputParentFolder;
    }

    @Override
    public void afterAll( ExtensionContext extensionContext ) throws Exception
    {
        log.info( "AfterAll extension running" );
    }

    void zipTempFolders() throws IOException
    {
        for(Path p : toCompressAfterAll)
        {
            String tarOutName = p.getFileName().toString() + ".tar.gz";
            try ( OutputStream fo = Files.newOutputStream( p.getParent().resolve( tarOutName ) );
                  OutputStream gzo = new GzipCompressorOutputStream( fo );
                  TarArchiveOutputStream archiver = new TarArchiveOutputStream( gzo ) )
            {
                archiver.setLongFileMode( TarArchiveOutputStream.LONGFILE_POSIX );
                List<Path> files = Files.walk( p ).toList();
                for(Path fileToBeArchived : files)
                {
                    // don't archive directories...
                    if(fileToBeArchived.toFile().isDirectory()) continue;
                    ArchiveEntry entry = archiver.createArchiveEntry( fileToBeArchived, testOutputParentFolder.relativize( fileToBeArchived ).toString() );
                    archiver.putArchiveEntry( entry );
                    IOUtils.copy(Files.newInputStream( fileToBeArchived ), archiver);
                    archiver.closeArchiveEntry();
                }
                archiver.finish();
            }
        }
    }

    public Path createTempFolderAndMountAsVolume( GenericContainer container, String hostFolderNamePrefix,
                                                         String containerMountPoint ) throws IOException
	{
		return createTempFolderAndMountAsVolume( container, hostFolderNamePrefix, containerMountPoint,
												 testOutputParentFolder );
	}

    public Path createTempFolderAndMountAsVolume( GenericContainer container, String hostFolderNamePrefix,
														 String containerMountPoint, Path parentFolder ) throws IOException
    {
        Path hostFolder = createTempFolder( hostFolderNamePrefix, parentFolder );
        mountHostFolderAsVolume( container, hostFolder, containerMountPoint );
        return hostFolder;
    }

    public void mountHostFolderAsVolume(GenericContainer container, Path hostFolder, String containerMountPoint)
    {
        container.withFileSystemBind( hostFolder.toAbsolutePath().toString(),
                                      containerMountPoint,
                                      BindMode.READ_WRITE );
    }

    public Path createTempFolder( String folderNamePrefix ) throws IOException
    {
    	return createTempFolder( folderNamePrefix, testOutputParentFolder );
    }

    public Path createTempFolder( String folderNamePrefix, Path parentFolder ) throws IOException
    {
        String randomStr = String.format( "%04d", rng.nextInt(10000 ) );  // random 4 digit number
        Path hostFolder = parentFolder.resolve( folderNamePrefix + randomStr);
        try
        {
            Files.createDirectories( hostFolder );
        }
        catch ( IOException e )
        {
            log.error( "could not create directory: {}", hostFolder.toAbsolutePath().toString() );
            e.printStackTrace();
            throw e;
        }
        log.info( "Created folder {}", hostFolder.toString() );
        if(parentFolder.equals( testOutputParentFolder ))
        {
            toCompressAfterAll.add( hostFolder );
        }
        return hostFolder;
    }
}
