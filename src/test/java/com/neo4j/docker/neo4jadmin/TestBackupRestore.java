package com.neo4j.docker.neo4jadmin;

import com.neo4j.docker.neo4jserver.configurations.Configuration;
import com.neo4j.docker.neo4jserver.configurations.Setting;
import com.neo4j.docker.utils.DatabaseIO;

import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.StartupDetector;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestBackupRestore
{
    // with authentication
    // with non-default user
    private static final Logger log = LoggerFactory.getLogger( TestBackupRestore.class );
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @BeforeAll
    static void beforeAll()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ),
                                "These tests only apply to neo4j-admin images of 5.0 and greater");
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "backup and restore only available in Neo4j Enterprise" );
    }

    private GenericContainer createDBContainer( boolean asDefaultUser, String password )
    {
        String auth = "none";
        if(!password.equalsIgnoreCase("none"))
        {
            auth = "neo4j/"+password;
        }
        Map<Setting,Configuration> confNames = Configuration.getConfigurationNameMap();
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", auth )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( confNames.get( Setting.BACKUP_ENABLED ).envName, "true" )
                 .withEnv( confNames.get( Setting.BACKUP_LISTEN_ADDRESS ).envName, "0.0.0.0:6362" )
                 .withExposedPorts( 7474, 7687, 6362 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        StartupDetector.makeContainerWaitForNeo4jReady(container, password, Duration.ofSeconds( 90 ));
        if(!asDefaultUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

    private GenericContainer createAdminContainer( boolean asDefaultUser )
    {
        GenericContainer container = new GenericContainer( TestSettings.ADMIN_IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .withStartupCheckStrategy( new OneShotStartupCheckStrategy().withTimeout( Duration.ofSeconds( 90 ) ) );
        if(!asDefaultUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

    @Test
    void shouldBackupAndRestore_defaultUser_noAuth() throws Exception
    {
        testCanBackupAndRestore( true, "none" );
    }
    @Test
    void shouldBackupAndRestore_nonDefaultUser_noAuth() throws Exception
    {
        testCanBackupAndRestore( false, "none" );
    }
    @Test
    void shouldBackupAndRestore_defaultUser_withAuth() throws Exception
    {
        testCanBackupAndRestore( true, "secretpassword" );
    }
    @Test
    void shouldBackupAndRestore_nonDefaultUser_withAuth() throws Exception
    {
        testCanBackupAndRestore( false, "secretpassword" );
    }

    private void testCanBackupAndRestore(boolean asDefaultUser, String password) throws Exception
    {
        final String dbUser = "neo4j";
        Path testOutputFolder = temporaryFolderManager.createTempFolder( "backupRestore-" );

        // BACKUP
        // start a database and populate data
        GenericContainer neo4j = createDBContainer( asDefaultUser, password );
        Path dataDir = temporaryFolderManager.createTempFolderAndMountAsVolume(
                neo4j, "data-", "/data", testOutputFolder );
        neo4j.start();
        DatabaseIO dbio = new DatabaseIO( neo4j );
        dbio.putInitialDataIntoContainer( dbUser, password );
        dbio.verifyInitialDataInContainer( dbUser, password );

        // start admin container to initiate backup
        String neoDBAddress = neo4j.getHost()+":"+neo4j.getMappedPort( 6362 );
        GenericContainer adminBackup = createAdminContainer( asDefaultUser )
                .withNetworkMode( "host" )
                .waitingFor( new LogMessageWaitStrategy().withRegEx( "^Backup command completed.*" ) )
                .withStartupCheckStrategy( new OneShotStartupCheckStrategy().withTimeout( Duration.ofSeconds( 15 ) ) )
                .withCommand("neo4j-admin",
                             "database",
                             "backup",
                             "--to-path=/backups",
                             "--include-metadata=all",
                             "--from=" + neoDBAddress,
                             "neo4j");


        Path backupDir = temporaryFolderManager.createTempFolderAndMountAsVolume(
                adminBackup, "backup-", "/backups", testOutputFolder );
        adminBackup.start();

        Assertions.assertTrue( neo4j.isRunning(), "neo4j container should still be running" );
        dbio.verifyInitialDataInContainer( dbUser, password );
        adminBackup.stop();

        // find backup file name and verify its existence.
        List<Path> backupFolder = Files.list( backupDir )
                                       .filter( p -> p.toFile().getName().startsWith( "neo4j" ) )
                                       .collect( Collectors.toList());
        Assertions.assertEquals( 1, backupFolder.size(), "No backup file was created" );
        File backupFile = backupFolder.get( 0 ).toFile();

        // RESTORE

        // write more stuff
        dbio.putMoreDataIntoContainer( dbUser, password );
        dbio.verifyMoreDataIntoContainer( dbUser, password, true );

        // do restore
        dbio.runCypherQuery( dbUser, password, "STOP DATABASE neo4j", "system" );
        GenericContainer adminRestore = createAdminContainer( asDefaultUser )
                .waitingFor( Wait.forLogMessage( ".*Restore of database .* completed successfully.*", 1 )
                                 .withStartupTimeout( Duration.ofSeconds( 30 ) ) )
                .withCommand("neo4j-admin",
                        "database",
                        "restore",
                        "--overwrite-destination=true",
                        "--from-path=/backups/"+backupFile.getName(),
                        "neo4j");
        temporaryFolderManager.mountHostFolderAsVolume( adminRestore, backupDir, "/backups" );
        temporaryFolderManager.mountHostFolderAsVolume( adminRestore, dataDir, "/data" );
        adminRestore.start();
        dbio.runCypherQuery( dbUser, password, "START DATABASE neo4j", "system" );

        // verify new stuff is missing
        dbio.verifyMoreDataIntoContainer( dbUser, password, false );

        // clean up
        adminRestore.stop();
        neo4j.stop();
    }
}
