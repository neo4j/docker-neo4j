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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestUpgrade
{
    private static final Logger log = LoggerFactory.getLogger( TestUpgrade.class );
    private final String user = "neo4j";
    private final String password = "quality";

    private GenericContainer makeContainer( String image )
    {
        GenericContainer container = new GenericContainer( image );
        container = container.withEnv( "NEO4J_AUTH", user + "/" + password )
                             .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                             .withExposedPorts( 7474 )
                             .withExposedPorts( 7687 )
                             .withLogConsumer( new Slf4jLogConsumer( log ) );
        container.setWaitStrategy( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) );
        container = container.withStartupTimeout( Duration.ofMinutes( 2 ) );
        ;
        return container;
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
            HostFileSystemOperations.mountHostFolderAsVolume( container, dataMount, "/data" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( user, password );
        }

        try ( GenericContainer container = makeContainer( TestSettings.IMAGE_ID ) )
        {
            HostFileSystemOperations.mountHostFolderAsVolume( container, dataMount, "/data" );
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.verifyDataInContainer( user, password );
        }
    }

    @Test
    void canUpgradeFromReleasedVersion() throws Exception
    {
        var targetNeo4jVersion = TestSettings.NEO4J_VERSION;

        // If this is the very first in a new major series (i.e. a .0.0 release) then this test isn't expected to work
        Assumptions.assumeFalse( targetNeo4jVersion.major == 0 && targetNeo4jVersion.minor == 0 );

        // TODO: update this when moving to the next minor release
        // I am taking a guess here that we will have published 4.1.0 by 1st of July
        if ( Instant.now().isBefore( Instant.parse( "2020-07-01T00:00:00.00Z" ) ) )
        {
            // This sort-of-hack is necessary when we cut a new minor branch before the previous minor branch has been published to dockerhub.
            Assumptions.assumeTrue( Neo4jVersion.NEO4J_VERSION_420.isNewerThan( targetNeo4jVersion ) );
        }
        String fromImageName = dockerImageToUpgradeFrom( targetNeo4jVersion );

        var testMountDirPrefix = String.format( "upgrade-from-%s", fromImageName ).replaceAll( ":", "_" );
        Path testMountDir = HostFileSystemOperations.createTempFolder( testMountDirPrefix );

        // TODO: test /plugins and /ssl directories
        Map<String,@NotNull Path> readonlyMounts = createDirectories( testMountDir, List.of( "/conf" ) );
        Map<String,@NotNull Path> writableMounts = createDirectories( testMountDir, List.of( "/data", "/logs", "/metrics" ) );

        // write a value to neo4j.conf
        try ( var confFile = new FileWriter( readonlyMounts.get( "/conf" ).resolve( "neo4j.conf" ).toFile() ) )
        {
            confFile.write( "dbms.memory.pagecache.size=8m" );
        }

        var allMounts = new HashMap<String,Path>();
        allMounts.putAll( readonlyMounts );
        allMounts.putAll( writableMounts );

        try ( var container = makeContainer( fromImageName ) )
        {
            allMounts.forEach( ( containerMount, hostFolder ) -> HostFileSystemOperations.mountHostFolderAsVolume( container, hostFolder, containerMount ) );

            final var startTime = Instant.now().toEpochMilli();
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( user, password );

            validateConfig( db );

            // validate that writes actually went to the mounted directories
            writableMounts.forEach( ( containerMount, hostFolder ) -> assertDirectoryModifiedSince( hostFolder, startTime ) );
            container.stop();
        }

        // when
        try ( var container = makeContainer( TestSettings.IMAGE_ID ) )
        {
            allMounts.forEach( ( k, v ) -> HostFileSystemOperations.mountHostFolderAsVolume( container, v, k ) );

            final var startTime = Instant.now().toEpochMilli();
            container.start();
            DatabaseIO db = new DatabaseIO( container );

            // then
            // verify that data from previous version is still present (and that reads work)
            db.verifyDataInContainer( user, password );

            // verify config still loaded
            validateConfig( db );

            // check that writes work
            db.putInitialDataIntoContainer( user, password );

            // validate that writes updated the mounted directories
            writableMounts.forEach( ( containerMount, hostFolder ) -> assertDirectoryModifiedSince( hostFolder, startTime ) );
            container.stop();
        }
    }

    @NotNull
    private Map<String,@NotNull Path> createDirectories( Path testMountDirectory, List<String> strings )
    {
        return strings
                .stream()
                .collect( Collectors.toMap( c -> c, c -> createDirectory( testMountDirectory, c.replaceFirst( "/", "" ) ) ) );
    }

    private String dockerImageToUpgradeFrom( Neo4jVersion targetNeo4jVersion )
    {
        // The most recent minor release that we expect to already have been released.
        var minorVersionToUpgradeFrom = targetNeo4jVersion.patch == 0 ? targetNeo4jVersion.minor - 1 : targetNeo4jVersion.minor;

        // given
        return String.format( "neo4j:%d.%d%s", targetNeo4jVersion.major, minorVersionToUpgradeFrom,
                              (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE) ? "-enterprise" : "" );
    }

    private void validateConfig( DatabaseIO db )
    {
        var configValue = db.runCypherProcedure( user, password,
                                                 "CALL dbms.listConfig() YIELD name, value WHERE name='dbms.memory.pagecache.size' RETURN value" );
        Assertions.assertEquals( "8m", configValue.get( 0 ).get( "value" ).asString() );
    }

    /**
     * Checks that the {@code mountDirectory} contains at lease one file that has been modified after the {@code startTimestamp}
     *
     * @param mountDirectory path to local directory mounted into the docker container.
     * @param startTimestamp timestamp (milliseconds since epoch) to check for modifications since.
     */
    private void assertDirectoryModifiedSince( Path mountDirectory, long startTimestamp )
    {
        log.info( "Checking {}", mountDirectory );
        var dir = mountDirectory.toFile();
        Assertions.assertTrue( dir.isDirectory() );
        var files = dir.listFiles();
        Assertions.assertTrue( files.length > 0 );
        var lastModified = Arrays.stream( files ).max( Comparators.byLongFunction( File::lastModified ) );
        Assertions.assertTrue( lastModified.get().lastModified() > startTimestamp );
    }

    @NotNull
    private Path createDirectory( Path mount, String s )
    {
        var subfolder = mount.resolve( s );
        log.info( "created folder " + subfolder.toString() + " to test upgrade" );
        try
        {
            return Files.createDirectories( subfolder );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }
}
