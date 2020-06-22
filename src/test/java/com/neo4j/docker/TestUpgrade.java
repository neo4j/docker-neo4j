package com.neo4j.docker;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.eclipse.collections.impl.block.factory.Comparators;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.neo4j.driver.Record;

import static com.neo4j.docker.utils.HostFileSystemOperations.createTempFolderAndMountAsVolume;
import static com.neo4j.docker.utils.HostFileSystemOperations.mountHostFolderAsVolume;

public class TestUpgrade
{
    private static final Logger log = LoggerFactory.getLogger( TestUpgrade.class );
    private static final List<String> readonlyMounts = Collections.singletonList( "conf" );
    private static final List<String> writableMounts = getWriteableMounts();

    private final String user = "neo4j";
    private final String password = "quality";

    private GenericContainer makeContainer( String image )
    {
        GenericContainer container = new GenericContainer( image );
        container = container.withEnv( "NEO4J_AUTH", user + "/" + password )
                             .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                             .withExposedPorts( 7474 )
                             .withExposedPorts( 7687 )
                             .withLogConsumer( new Slf4jLogConsumer( log ) )
                             .waitingFor( Wait.forHttp( "/" )
                                              .forPort( 7474 )
                                              .forStatusCode( 200 )
                                              .withStartupTimeout( Duration.ofSeconds( 120 ) ) );

        return container;
    }

    private Map<String,Path> createAllMounts( GenericContainer container, Path parentFolder ) throws IOException
    {
        HashMap<String,Path> hostFolders = new HashMap<>( readonlyMounts.size() + writableMounts.size() );
        for ( String mount : readonlyMounts )
        {
            hostFolders.put( mount, createTempFolderAndMountAsVolume( container, parentFolder, mount, "/" + mount ) );
        }
        for ( String mount : writableMounts )
        {
            hostFolders.put( mount, createTempFolderAndMountAsVolume( container, parentFolder, mount, "/" + mount ) );
        }
        return hostFolders;
    }

    @Test
    void canUpgradeFromBeforeFilePermissionFix35() throws Exception
    {
        Neo4jVersion beforeFix = new Neo4jVersion( 3, 5, 3 );
        String beforeFixImage = (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE) ? "neo4j:3.5.3-enterprise" : "neo4j:3.5.3";
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isNewerThan( beforeFix ), "test only applicable to latest 3.5 docker" );
        Assumptions.assumeFalse( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ),
                                 "test only applicable to latest 3.5 docker" );

        Path dataMount = HostFileSystemOperations.createTempFolder( "data-upgrade-" );
        log.info( "created folder " + dataMount.toString() + " to test upgrade" );

        try ( GenericContainer container = makeContainer( beforeFixImage ) )
        {
            mountHostFolderAsVolume( container, dataMount, "/data" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( user, password );
        }

        try ( GenericContainer container = makeContainer( TestSettings.IMAGE_ID ) )
        {
            mountHostFolderAsVolume( container, dataMount, "/data" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.verifyDataInContainer( user, password );
        }
    }

    // TODO: parameterize these tests for different configurations (e.g. running as non-root user)
    @Test
    void canUpgradeFromSameMinorVersion() throws Exception
    {
        Neo4jVersion version = TestSettings.NEO4J_VERSION;

        // If this is the very first in a new minor series we don't expect there to be a released version available to test upgrade
        Assumptions.assumeTrue( version.patch > 0 );

        TestUpgradeFromImage( releaseImageName( version.major, version.minor ) );
    }

    @Test
    void canUpgradeFromPreviousMinorVersion() throws Exception
    {
        Neo4jVersion version = TestSettings.NEO4J_VERSION;

        // If this is the very first in a new major series (i.e. a x.0.0 release) then this test isn't expected to work
        Assumptions.assumeTrue( version.minor > 0 );

        try
        {
            TestUpgradeFromImage( releaseImageName( version.major, version.minor - 1 ) );
        }
        catch ( ContainerLaunchException launchException )
        {
            if ( causedByContainerNotFound( launchException ) )
            {
                // There is a period when we create a new minor branch but the previous minor version has not yet been published.
                // during this time it should be safe to test upgrades from the n-2 minor version - provided n >= 2.
                Assumptions.assumeTrue( version.minor >= 2 );
                TestUpgradeFromImage( releaseImageName( version.major, version.minor - 2 ) );
            }
            else
            {
                throw launchException;
            }
        }
    }

    private void TestUpgradeFromImage( String fromImageName ) throws IOException, InterruptedException
    {
        String testMountDirPrefix = String.format( "upgrade-from-%s", fromImageName ).replaceAll( ":", "_" );
        Path testMountDir = HostFileSystemOperations.createTempFolder( testMountDirPrefix );
        Map<String,Path> allMounts;

        // Start and write data using the released image
        log.info( "Testing upgrade from {}", fromImageName );
        try ( GenericContainer container = makeContainer( fromImageName ) )
        {
            // Make sure we use a reasonably up to date released version
            container.withImagePullPolicy( PullPolicy.ageBased( Duration.ofDays( 1 ) ) );

            // given
            allMounts = createAllMounts( container, testMountDir );
            writeNeoConf( allMounts.get( "conf" ), "dbms.memory.pagecache.size=9m" );
            // sleep a tiny bit otherwise some of the files-last-modified checks fail
            Thread.sleep( 1 );

            // when
            final long startTime = Instant.now().toEpochMilli();
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( user, password );

            // then
            validateDb( db );

            // TODO: test plugin and ssl features work after upgrade

            container.stop();

            // check mounts after container stop to be sure that shutdown doesn't mess with them
            validateMounts( allMounts, startTime );
        }

        // Now try with the current image
        try ( GenericContainer container = makeContainer( TestSettings.IMAGE_ID ) )
        {

            allMounts.forEach( ( mount, hostFolder ) -> mountHostFolderAsVolume( container, hostFolder, "/" + mount ) );

            final long startTime = Instant.now().toEpochMilli();
            container.start();
            DatabaseIO db = new DatabaseIO( container );

            validateDb( db );

            container.stop();

            // check mounts after container stop to be sure that shutdown doesn't mess with them
            validateMounts( allMounts, startTime );
        }
    }

    private void validateMounts( Map<String,Path> allMounts, long startTime )
    {
        // check that writes updated the mounted directories
        writableMounts.forEach( mount -> assertDirectoryModifiedSince( allMounts.get( mount ), startTime ) );
        // check that we didn't write anything to read only directories
        readonlyMounts.forEach( mount -> assertDirectoryNotModifiedSince( allMounts.get( mount ), startTime ) );
    }

    private void writeNeoConf( Path confMount, String... confValues ) throws IOException
    {
        try ( Writer confFile = new FileWriter( confMount.resolve( "neo4j.conf" ).toFile() ) )
        {
            for ( String confString : confValues )
            {
                confFile.write( confString );
            }
        }
    }

    private void validateDb( DatabaseIO db )
    {
        // check that test data is present
        db.verifyDataInContainer( user, password );

        // check the config
        validateConfig( db );

        // check that writes work
        db.createAndDeleteNode( user, password );
    }

    private void validateConfig( DatabaseIO db )
    {
        String cypher = "CALL dbms.listConfig() YIELD name, value WHERE name='dbms.memory.pagecache.size' RETURN value";
        List<Record> configValue = db.runCypherProcedure( user, password, cypher );
        Assertions.assertEquals( "9m", configValue.get( 0 ).get( "value" ).asString() );
    }

    /**
     * Checks that the {@code mountDirectory} contains at lease one file that has been modified after the {@code startTimestamp}
     *
     * @param mountDirectory path to local directory mounted into the docker container.
     * @param startTimestamp timestamp (milliseconds since epoch) to check for modifications since.
     */
    private static void assertDirectoryModifiedSince( Path mountDirectory, long startTimestamp )
    {
        log.info( "Checking {} for files modified since {}", mountDirectory, startTimestamp );
        File lastModifiedFile = getLastModifiedFile( mountDirectory );
        Assertions.assertTrue( lastModifiedFile.lastModified() > startTimestamp );
    }

    /**
     * Checks that the {@code mountDirectory} contains at lease one file that has been modified after the {@code startTimestamp}
     *
     * @param mountDirectory path to local directory mounted into the docker container.
     * @param startTimestamp timestamp (milliseconds since epoch) to check for modifications since.
     */
    private static void assertDirectoryNotModifiedSince( Path mountDirectory, long startTimestamp )
    {
        log.info( "Checking {} does not contain files modified since {}", mountDirectory, startTimestamp );
        File lastModifiedFile = getLastModifiedFile( mountDirectory );
        Assertions.assertTrue( lastModifiedFile.lastModified() < startTimestamp );
    }

    @NotNull
    private static File getLastModifiedFile( Path mountDirectory )
    {
        File dir = mountDirectory.toFile();
        Assertions.assertTrue( dir.isDirectory() );

        try
        {
            Optional<File> lastModified = Files.walk( dir.toPath() )
                                               .filter( Files::isRegularFile )
                                               .map( Path::toFile )
                                               .max( Comparators.byLongFunction( File::lastModified ) );
            Assertions.assertTrue( lastModified.isPresent() );
            return lastModified.get();
        }
        catch ( IOException e )
        {
            // convert to RuntimeException so we can use this in lambdas
            throw new RuntimeException( e );
        }
    }

    private static boolean causedByContainerNotFound( ContainerLaunchException launchException )
    {
        Throwable cause = launchException.getCause();
        return cause != null &&
               cause instanceof ContainerFetchException &&
               cause.getCause() instanceof com.github.dockerjava.api.exception.NotFoundException;
    }

    private static String releaseImageName( int major, int minor )
    {
        return String.format( "neo4j:%d.%d%s", major, minor,
                              (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE) ? "-enterprise" : "" );
    }

    private static List<String> getWriteableMounts()
    {
        switch ( TestSettings.EDITION )
        {
        case COMMUNITY:
            return Arrays.asList( "data", "logs" );
        case ENTERPRISE:
            // /metrics doesn't get chowned in 3.x so doesn't always work
            return TestSettings.NEO4J_VERSION.major < 4 ? Arrays.asList( "data", "logs" ) : Arrays.asList( "data", "logs", "metrics" );
        default:
            Assertions.fail( "Unknown Edition: " + TestSettings.EDITION );
            return Collections.emptyList();
        }
    }
}
