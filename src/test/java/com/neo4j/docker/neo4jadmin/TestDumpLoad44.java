package com.neo4j.docker.neo4jadmin;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.WaitStrategies;
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

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

public class TestDumpLoad44
{
    private static Logger log = LoggerFactory.getLogger( TestDumpLoad44.class );
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @BeforeAll
    static void beforeAll()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 4, 0 )),
                                "Neo4j admin image not available before 4.4.0");
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ),
                                "These Neo4j admin tests are only for 4.4");
    }

    private GenericContainer createDBContainer( boolean asDefaultUser, String password )
    {
        String auth = "none";
        if(!password.equalsIgnoreCase("none"))
        {
            auth = "neo4j/"+password;
        }

        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", auth )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( WaitStrategies.waitForNeo4jReady( password ) )
                 // the default testcontainer framework behaviour is to just stop the process entirely,
                 // preventing clean shutdown. This means we can run the stop command and
                 // it'll send a SIGTERM to initiate neo4j shutdown. See also stopContainer method.
                 .withCreateContainerCmdModifier(
                         (Consumer<CreateContainerCmd>) cmd -> cmd.withStopSignal( "SIGTERM" ).withStopTimeout( 20 ));
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
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( new LogMessageWaitStrategy().withRegEx( "^Done: \\d+ files, [\\d\\.,]+[KMGi]*B processed.*" ) )
                 .withStartupCheckStrategy( new OneShotStartupCheckStrategy().withTimeout( Duration.ofSeconds( 90 ) ) );
        if(!asDefaultUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

    @Test
    void shouldDumpAndLoad_defaultUser_noAuth() throws Exception
    {
        shouldCreateDumpAndLoadDump( true, "none" );
    }

    @Test
    void shouldDumpAndLoad_nonDefaultUser_noAuth() throws Exception
    {
        shouldCreateDumpAndLoadDump( false, "none" );
    }

    @Test
    void shouldDumpAndLoad_defaultUser_withAuth() throws Exception
    {
        shouldCreateDumpAndLoadDump( true, "verysecretpassword" );
    }

    @Test
    void shouldDumpAndLoad_nonDefaultUser_withAuth() throws Exception
    {
        shouldCreateDumpAndLoadDump( false, "verysecretpassword" );
    }

    //container.stop() actually runs the killContainer Command, preventing clean shutdown.
    // This runs the actual stop command. Which we set up in createDBContainer to send SIGTERM
    private void stopContainer(GenericContainer container)
    {
        log.info( "issuing container stop command" );
        container.getDockerClient().stopContainerCmd( container.getContainerId() ).exec();
        log.info( "Container stopped" );
    }

    private void shouldCreateDumpAndLoadDump( boolean asDefaultUser, String password ) throws Exception
    {
        Path firstDataDir;
        Path secondDataDir;
        Path backupDir;

        // start a database and populate it
        try(GenericContainer container = createDBContainer( asDefaultUser, password ))
        {
            firstDataDir = temporaryFolderManager.createNamedFolderAndMountAsVolume( container,
                                                                                     "data1",
                                                                                     "/data" );
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", password );
            stopContainer( container );
        }

        // use admin container to create dump
        try(GenericContainer admin = createAdminContainer( asDefaultUser ))
        {
            temporaryFolderManager.mountHostFolderAsVolume( admin, firstDataDir, "/data" );
            backupDir = temporaryFolderManager.createFolderAndMountAsVolume(admin, "/backups");
            admin.withCommand( "neo4j-admin", "dump", "--database=neo4j", "--to=/backups/neo4j.dump" );
            admin.start();
        }
        Assertions.assertTrue( backupDir.resolve( "neo4j.dump" ).toFile().exists(), "dump file not created");

        // dump file exists. Now try to load it into a new database.
        // use admin container to create dump
        try(GenericContainer admin = createAdminContainer( asDefaultUser ))
        {
            secondDataDir = temporaryFolderManager.createNamedFolderAndMountAsVolume( admin,
                                                                                     "data2",
                                                                                     "/data" );
            temporaryFolderManager.mountHostFolderAsVolume( admin, backupDir, "/backups" );
            admin.withCommand( "neo4j-admin", "load", "--database=neo4j", "--from=/backups/neo4j.dump" );
            admin.start();
        }

        // verify data in 2nd data directory by starting a database and verifying data we populated earlier
        try(GenericContainer container = createDBContainer( asDefaultUser, password ))
        {
            temporaryFolderManager.mountHostFolderAsVolume( container, secondDataDir, "/data" );
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.verifyInitialDataInContainer( "neo4j", password );
        }
    }
}
