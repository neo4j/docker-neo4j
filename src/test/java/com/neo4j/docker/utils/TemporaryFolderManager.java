package com.neo4j.docker.utils;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**JUnit extension to create temporary folders and compress them after each test class runs.
 * <p>
 * <h2>WHY</h2>
 * Starting a clean neo4j pre-allocates 500MB of space for the data folder. With all these docker tests
 * this ends up allocating a huge amount of empty space that fills the test machine memory.
 * There are enough unit tests now, that we frequently get test failures just because
 * the machine running the tests ran out of space.
 * This empty space can easily be freed by compressing the mounted folders once we are finished with them.
 * <p>
 *
 * <h2>HOW</h2>
 * To use this utility, create an object as a class field, and use @RegisterExtension annotation.
 *
 * On using TemporaryFolderManager to create a folder for the  first time in a test method, it will create a folder
 * with the method name (plus a little salt), and put all the method's temporary folders and data inside.
 * This means...
 * <ul>
 *     <li>when creating temporary folders, you don't need to worry about using the same folder name as another test.</li>
 *     <li>you can give folders generic names without having to worry too much about them being descriptive.</li>
 *     <li>Folder names can be automatically generated from the mountpoint if desired</li>
 * </ul>
 *
 * <h2>EXAMPLE USE CASES</h2>
 *
 * First, to get a TemporaryFolderManager instantiation do:
 * <pre>{@code
 @RegisterExtension
 public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
 * }</pre>
 *
 * <h3>SIMPLE: Just create one or two unrelated folders and mount them</h3>
 * Most of the time, this is all you'll need.
 * Assuming you already have a container...
 * <pre>{@code
Path confpath = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
Path logpath = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
 * }</pre>
 * This will create a folder in {@link TestSettings#TEST_TMP_FOLDER} with the name
 * <code>CLASSNAME_METHODNAME_RANDOMNUMBER</code> and inside it, there will be a folder called <code>conf</code> and
 * a folder called <code>logs</code>. These will be mounted to <code>container</code> at <code>/conf</code> and <code>/logs</code>.
 * <p>
 * For example if your test class is TestMounting.java and the method is called <code>shouldWriteToMount</code>
 * the folders created will be together inside <code>com.neo4j.docker.coredb.TestMounting_shouldWriteToMount_RANDOMNUMBER</code>.
 *
 * <h3>HARDER: Mount the same folder to two different (consecutive) containers</h3>
 * <pre>{@code
Path confpath;
try(container1 = makeAContainer())
{
    confpath = temporaryFolderManager.createFolderAndMountAsVolume(container1, "/conf");
}
try(container2 = makeAContainer())
{
    temporaryFolderManager.mountHostFolderAsVolume(container2, confpath, "/conf");
}
 * }</pre>
 *
 * <h3>HARDEST: Two containers, two different folders, same mount point each time</h3>
 * <pre>{@code
try(container1 = makeAContainer())
{
    Path confpath1 = temporaryFolderManager.createNamedFolderAndMountAsVolume( container1, "conf1", "/conf" );
}
try(container2 = makeAContainer())
{
    Path confpath2 = temporaryFolderManager.createNamedFolderAndMountAsVolume( container2, "conf2", "/conf" );
}
 * }</pre>
 * */
public class TemporaryFolderManager implements AfterAllCallback, BeforeEachCallback
{
    private final Logger log = LoggerFactory.getLogger( TemporaryFolderManager.class );
    // if we ever run parallel tests, random number generator and
    // list of folders to compress need to be made thread safe
    private Random rng = new Random(  );
    private final Path folderRoot;
    protected Path methodOutputFolder;    // protected scope for testing
    protected Set<Path> toCompressAfterAll = new HashSet<>();    // protected scope for testing

    public TemporaryFolderManager( )
    {
        this(TestSettings.TEST_TMP_FOLDER);
    }
    public TemporaryFolderManager( Path testOutputParentFolder )
    {
        this.folderRoot = testOutputParentFolder;
    }

    @Override
    public void beforeEach( ExtensionContext extensionContext ) throws Exception
    {
        String methodOutputFolderName = extensionContext.getTestClass().get().getName() + "_" +
                                 extensionContext.getTestMethod().get().getName();
        if(!extensionContext.getDisplayName().startsWith( extensionContext.getTestMethod().get().getName() ))
        {
            methodOutputFolderName += "_" + extensionContext.getDisplayName()
                                                            .replace( ' ', '_' );
        }
        // finally add some salt so  that we can run the same test method twice and not get naming clashes.
        methodOutputFolderName += String.format( "_%04d", rng.nextInt(10000 ) );
        log.info( "Recommended folder prefix is " + methodOutputFolderName );
        methodOutputFolder = folderRoot.resolve( methodOutputFolderName );
    }

    @Override
    public void afterAll( ExtensionContext extensionContext ) throws Exception
    {
        triggerCleanup();
    }

    public void triggerCleanup() throws Exception
    {
        if(TestSettings.SKIP_MOUNTED_FOLDER_TARBALLING)
        {
            log.info( "Cleanup of test artifacts skipped by request" );
            return;
        }
        log.info( "Performing cleanup of {}", folderRoot );
        // create tar archive of data
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
                    try( InputStream fileStream = Files.newInputStream( fileToBeArchived ))
                    {
                        ArchiveEntry entry = archiver.createArchiveEntry( fileToBeArchived, folderRoot.relativize( fileToBeArchived ).toString() );
                        archiver.putArchiveEntry( entry );
                        IOUtils.copy( fileStream, archiver );
                        archiver.closeArchiveEntry();
                    } catch (IOException ioe)
                    {
                        // consume the error, because sometimes, file permissions won't let us copy
                        log.warn( "Could not archive "+ fileToBeArchived, ioe);
                    }
                }
                archiver.finish();
            }
        }
        // delete original folders
        log.debug( "Re owning folders: {}", toCompressAfterAll.stream()
                                                              .map( Path::toString )
                                                              .collect( Collectors.joining(", ")));
        setFolderOwnerTo( SetContainerUser.getNonRootUserString(),
                          toCompressAfterAll.toArray(new Path[toCompressAfterAll.size()]) );

        for(Path p : toCompressAfterAll)
        {
            log.debug( "Deleting test output folder {}", p.getFileName().toString() );
            FileUtils.deleteDirectory( p.toFile() );
        }
        toCompressAfterAll.clear();
    }

    public Path createNamedFolderAndMountAsVolume( GenericContainer container, String hostFolderName, String containerMountPoint ) throws IOException
    {
        Path tempFolder = createFolder( hostFolderName );
        mountHostFolderAsVolume( container, tempFolder, containerMountPoint );
        return tempFolder;
    }

    public Path createFolderAndMountAsVolume( GenericContainer container, String containerMountPoint ) throws IOException
    {
        Path tempFolder = createFolder( getFolderNameFromMountPoint( containerMountPoint ) );
        mountHostFolderAsVolume( container, tempFolder, containerMountPoint );
        return tempFolder;
    }

//    public Path createNamedFolderAndMountAsVolume( GenericContainer container, String hostFolderName,
//                                                   Path parentFolder, String containerMountPoint ) throws IOException
//    {
//        Path tempFolder = createFolder( hostFolderName, parentFolder );
//        mountHostFolderAsVolume( container, tempFolder, containerMountPoint );
//        return tempFolder;
//    }

//    public Path createFolderAndMountAsVolume( GenericContainer container, String containerMountPoint, Path parentFolder ) throws IOException
//    {
//        return null;
//        Path hostFolder = createTempFolder( hostFolderNamePrefix, parentFolder );
//        mountHostFolderAsVolume( container, hostFolder, containerMountPoint );
//        return hostFolder;
//    }

    public void mountHostFolderAsVolume(GenericContainer container, Path hostFolder, String containerMountPoint)
    {
        container.withFileSystemBind( hostFolder.toAbsolutePath().toString(),
                                      containerMountPoint,
                                      BindMode.READ_WRITE );
    }

    public Path createFolder( String folderName ) throws IOException
    {
    	return createFolder( folderName, methodOutputFolder );
    }

    public Path createFolder( String folderName, Path parentFolder ) throws IOException
    {
        if(!parentFolder.startsWith( folderRoot ))
        {
            throw new IOException("Requested to create temp folder outside of " + folderRoot +". " +
                                  "This is a problem with the test.");
        }
        Path hostFolder = parentFolder.resolve(folderName);
        try
        {
            Files.createDirectories( hostFolder );
        }
        catch ( IOException e )
        {
            log.error( "could not create directory: {}", hostFolder.toAbsolutePath() );
            e.printStackTrace();
            throw e;
        }
        log.info( "Created folder {}", hostFolder );
        // flag top level methodOutputFolder for cleanup
        toCompressAfterAll.add( methodOutputFolder ); // toCompressAfterAll is a set, so automatically removes duplicates.
        return hostFolder;
    }

    public void setFolderOwnerToCurrentUser(Path file) throws Exception
    {
        setFolderOwnerTo( SetContainerUser.getNonRootUserString(), file );
    }

    public void setFolderOwnerToNeo4j(Path file) throws Exception
    {
        setFolderOwnerTo( "7474:7474", file );
    }

    protected String getFolderNameFromMountPoint(String containerMountPoint)
    {
        return containerMountPoint.substring( 1 )
                                  .replace( '/', '_' )
                                  .replace( ' ', '_' );
    }

    private void setFolderOwnerTo(String userAndGroup, Path... files) throws Exception
    {
        // uses docker privileges to set file owner, since probably the current user is not a sudoer.

        // Using nginx because it's easy to verify that the image started.
        try(GenericContainer container = new GenericContainer( DockerImageName.parse( "nginx:latest")))
        {
            container.withExposedPorts( 80 )
                     .waitingFor( Wait.forHttp( "/" ).withStartupTimeout( Duration.ofSeconds( 20 ) ) );
            for(Path p : files)
            {
                mountHostFolderAsVolume( container, p, p.toAbsolutePath().toString() );
            }
            container.start();
            for(Path p : files)
            {
                Container.ExecResult x =
                        container.execInContainer( "chown", "-R", userAndGroup,
                                                   p.toAbsolutePath().toString() );
            }
            container.stop();
        }
    }
}
