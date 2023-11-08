package com.neo4j.docker.coredb;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TestMounting
{
    private static Logger log = LoggerFactory.getLogger( TestMounting.class );

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @AfterEach
    void archiveTestArtifacts() throws Exception
    {
        temporaryFolderManager.triggerCleanup();
    }

    static Stream<Arguments> defaultUserFlagSecurePermissionsFlag()
    {
        // "asUser={0}, secureFlag={1}"
        // expected behaviour is that if you set --user flag, your data should be read/writable
        // if you don't set --user flag then read/writability should be controlled by the secure file permissions flag
        // the asCurrentUser=false, secureflag=true combination is tested separately because the container should fail to start.
        return Stream.of(
                Arguments.arguments( false, false ),
                Arguments.arguments( true, false ),
                Arguments.arguments( true, true ) );
    }

    private GenericContainer setupBasicContainer( boolean asCurrentUser, boolean isSecurityFlagSet )
    {
        log.info( "Running as user {}, {}",
                  asCurrentUser ? "non-root" : "root",
                  isSecurityFlagSet ? "with secure file permissions" : "with unsecured file permissions" );

        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( "NEO4J_AUTH", "none" )
                 .waitingFor( WaitStrategies.waitForNeo4jReady( "none" ) );
        if ( asCurrentUser )
        {
            SetContainerUser.nonRootUser( container );
        }
        if ( isSecurityFlagSet )
        {
            container.withEnv( "SECURE_FILE_PERMISSIONS", "yes" );
        }
        return container;
    }

    private void verifySingleFolder( Path folderToCheck, boolean shouldBeWritable )
    {
        String folderForDiagnostics = folderToCheck.toAbsolutePath().toString();

        Assertions.assertTrue( folderToCheck.toFile().exists(), "did not create " + folderForDiagnostics + " folder on host" );
        if ( shouldBeWritable )
        {
            Assertions.assertTrue( folderToCheck.toFile().canRead(), "cannot read host " + folderForDiagnostics + " folder" );
            Assertions.assertTrue( folderToCheck.toFile().canWrite(), "cannot write to host " + folderForDiagnostics + " folder" );
        }
    }

    private void verifyDataFolderContentsArePresentOnHost( Path dataMount, boolean shouldBeWritable )
    {
        verifySingleFolder( dataMount.resolve( "databases" ), shouldBeWritable );

        if ( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ) )
        {
            verifySingleFolder( dataMount.resolve( "transactions" ), shouldBeWritable );
        }
    }

    private void verifyLogsFolderContentsArePresentOnHost( Path logsMount, boolean shouldBeWritable )
    {
        verifySingleFolder( logsMount, shouldBeWritable );
        Assertions.assertTrue( logsMount.resolve( "debug.log" ).toFile().exists(),
                               "Neo4j did not write a debug.log file to " + logsMount.toString() );
        Assertions.assertEquals( shouldBeWritable,
                                 logsMount.resolve( "debug.log" ).toFile().canWrite(),
                                 String.format( "The debug.log file should %sbe writable", shouldBeWritable ? "" : "not " ) );
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource( booleans = {true, false} )
    void canDumpConfig( boolean asCurrentUser ) throws Exception
    {
        File confFile;
        Path confMount;
        String assertMsg = "Conf file was not successfully dumped when running container as "
                           + (asCurrentUser? "current user" : "root");

        try ( GenericContainer container = setupBasicContainer( asCurrentUser, false ) )
        {
            //Mount /conf
            confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            confFile = confMount.resolve( "neo4j.conf" ).toFile();

            //Start the container
            container.setWaitStrategy(
                    Wait.forLogMessage( ".*Config Dumped.*", 1 )
                        .withStartupTimeout( Duration.ofSeconds( 30 ) ) );
            container.setStartupCheckStrategy( new OneShotStartupCheckStrategy() );
            container.setCommand( "dump-config" );
            container.start();
        }

        // verify conf file was written
        Assertions.assertTrue( confFile.exists(), assertMsg );
        // verify conf folder does not have new owner if not running as root
        if ( asCurrentUser )
        {
            int fileUID = (Integer) Files.getAttribute( confFile.toPath(), "unix:uid" );
            int expectedUID = Integer.parseInt( SetContainerUser.getNonRootUserString().split( ":" )[0] );
            Assertions.assertEquals( expectedUID, fileUID, "Owner of dumped conf file is not the currently running user" );
        }
    }

    @Test
    void canDumpConfig_errorsWithoutConfMount() throws Exception
    {
        try ( GenericContainer container = setupBasicContainer( false, false ) )
        {
            container.setWaitStrategy(
                    Wait.forLogMessage( ".*Config Dumped.*", 1 )
                        .withStartupTimeout( Duration.ofSeconds( 30 ) ) );
            container.setStartupCheckStrategy( new OneShotStartupCheckStrategy() );
            container.setCommand( "dump-config" );
            Assertions.assertThrows( ContainerLaunchException.class,
                                     () -> container.start(),
                                     "Did not error when dump config requested without mounted /conf folder" );
            String stderr = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue( stderr.endsWith( "You must mount a folder to /conf so that the configuration file(s) can be dumped to there.\n" ) );
        }
    }

    @ParameterizedTest( name = "asUser={0}, secureFlag={1}" )
    @MethodSource( "defaultUserFlagSecurePermissionsFlag" )
    void testCanMountJustDataFolder( boolean asCurrentUser, boolean isSecurityFlagSet ) throws IOException
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3, 1, 0 ) ),
                                "User checks not valid before 3.1" );

        try ( GenericContainer container = setupBasicContainer( asCurrentUser, isSecurityFlagSet ) )
        {
            Path dataMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/data");
            container.start();

            // neo4j should now have started, so there'll be stuff in the data folder
            // we need to check that stuff is readable and owned by the correct user
            verifyDataFolderContentsArePresentOnHost( dataMount, asCurrentUser );
        }
    }

    @ParameterizedTest( name = "asUser={0}, secureFlag={1}" )
    @MethodSource( "defaultUserFlagSecurePermissionsFlag" )
    void testCanMountJustLogsFolder( boolean asCurrentUser, boolean isSecurityFlagSet ) throws IOException
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3, 1, 0 ) ),
                                "User checks not valid before 3.1" );

        try ( GenericContainer container = setupBasicContainer( asCurrentUser, isSecurityFlagSet ) )
        {
            Path logsMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            container.start();

            verifyLogsFolderContentsArePresentOnHost( logsMount, asCurrentUser );
        }
    }

    @ParameterizedTest( name = "asUser={0}, secureFlag={1}" )
    @MethodSource( "defaultUserFlagSecurePermissionsFlag" )
    void testCanMountDataAndLogsFolder( boolean asCurrentUser, boolean isSecurityFlagSet ) throws IOException
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3, 1, 0 ) ),
                                "User checks not valid before 3.1" );

        try ( GenericContainer container = setupBasicContainer( asCurrentUser, isSecurityFlagSet ) )
        {
            Path dataMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/data");
            Path logsMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            container.start();

            verifyDataFolderContentsArePresentOnHost( dataMount, asCurrentUser );
            verifyLogsFolderContentsArePresentOnHost( logsMount, asCurrentUser );
        }
    }

    @Test
    void testCantWriteIfSecureEnabledAndNoPermissions_data() throws IOException
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3, 1, 0 ) ),
                                "User checks not valid before 3.1" );

        try ( GenericContainer container = setupBasicContainer( false, true ) )
        {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/data");

            // currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
            container.setWaitStrategy( Wait.forLogMessage( "[fF]older /data is not accessible for user", 1 )
                                           .withStartupTimeout( Duration.ofSeconds( 20 ) ) );
            Assertions.assertThrows( ContainerLaunchException.class,
                                     () -> container.start(),
                                     "Neo4j should not start in secure mode if data folder is unwritable" );
        }
    }

    @Test
    void testCantWriteIfSecureEnabledAndNoPermissions_logs() throws IOException
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3, 1, 0 ) ),
                                "User checks not valid before 3.1" );

        try ( GenericContainer container = setupBasicContainer( false, true ) )
        {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");

            // currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
            container.setWaitStrategy( Wait.forLogMessage( "[fF]older /logs is not accessible for user", 1 )
                                           .withStartupTimeout( Duration.ofSeconds( 20 ) ) );
            Assertions.assertThrows( ContainerLaunchException.class,
                                     () -> container.start(),
                                     "Neo4j should not start in secure mode if logs folder is unwritable" );
        }
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource( booleans = {true, false} )
    void canMountAllTheThings_fileMounts( boolean asCurrentUser ) throws Exception
    {
        try ( GenericContainer container = setupBasicContainer( asCurrentUser, false ) )
        {
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/data");
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/import");
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/metrics");
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/plugins");
            container.start();
            DatabaseIO databaseIO = new DatabaseIO( container );
            // do some database writes so that we try writing to writable folders.
            databaseIO.putInitialDataIntoContainer( "neo4j", "none" );
            databaseIO.verifyInitialDataInContainer( "neo4j", "none" );
        }
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource( booleans = {true, false} )
    void canMountAllTheThings_namedVolumes( boolean asCurrentUser ) throws Exception
    {
        String id = String.format( "%04d", new Random().nextInt( 10000 ) );
        try ( GenericContainer container = setupBasicContainer( asCurrentUser, false ) )
        {
            container.withCreateContainerCmdModifier(
                    (Consumer<CreateContainerCmd>) cmd -> cmd.getHostConfig().withBinds(
                            Bind.parse( "conf-" + id + ":/conf" ),
                            Bind.parse( "data-" + id + ":/data" ),
                            Bind.parse( "import-" + id + ":/import" ),
                            Bind.parse( "logs-" + id + ":/logs" ),
                            //Bind.parse("metrics-"+id+":/metrics"), 	//todo metrics needs to be writable but we aren't chowning in the dockerfile, so a named volume for metrics will fail
                            Bind.parse( "plugins-" + id + ":/plugins" )
                    ) );
            container.start();
            DatabaseIO databaseIO = new DatabaseIO( container );
            // do some database writes so that we try writing to writable folders.
            databaseIO.putInitialDataIntoContainer( "neo4j", "none" );
            databaseIO.verifyInitialDataInContainer( "neo4j", "none" );
        }
    }

    @Test
    void shouldReownSubfilesToNeo4j() throws Exception
    {
        Assumptions.assumeTrue(
                TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 0, 0 ) ),
                "User checks not valid before 4.0" );

        Path logMount = temporaryFolderManager.createFolder( "subfileownership" );
        Path debugLog = logMount.resolve( "debug.log" );
        // put file in logMount
        Files.write( debugLog, "some log words".getBytes() );
        // make neo4j own the conf folder but NOT the neo4j.conf
        temporaryFolderManager.setFolderOwnerToNeo4j( logMount );
        temporaryFolderManager.setFolderOwnerToCurrentUser( debugLog );

        try ( GenericContainer container = setupBasicContainer( false, false ) )
        {
            temporaryFolderManager.mountHostFolderAsVolume( container, logMount, "/logs" );
            container.start();
            // if debug.log doesn't get re-owned, neo4j will not start and this test will fail here
        }
    }
}
