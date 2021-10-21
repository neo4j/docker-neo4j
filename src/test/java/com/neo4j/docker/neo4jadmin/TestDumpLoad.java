package com.neo4j.docker.neo4jadmin;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

public class TestDumpLoad
{
    // test as default user
    // test as not default user
    // test with auth
    // test without auth

    private static Logger log = LoggerFactory.getLogger( TestDumpLoad.class );

    private GenericContainer createDBContainer( boolean asDefaultUser )
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( Wait.forHttp( "/" )
                                  .forPort( 7474 )
                                  .forStatusCode( 200 )
                                  .withStartupTimeout( Duration.ofSeconds( 90 ) ) )
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
        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( new LogMessageWaitStrategy().withRegEx( "^Done: \\d+ files, [\\d\\.,]+[KMGi]+B processed\\..*" ) )
//                 .waitingFor( new LogMessageWaitStrategy().withRegEx( "^Done: .*" ) )
                 .withStartupCheckStrategy( new OneShotStartupCheckStrategy().withTimeout( Duration.ofSeconds( 90 ) ) );
        if(!asDefaultUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

    @Test
    void shouldDumpAndLoad_defaultUser() throws Exception
    {
        shouldCreateDumpAndLoadDump( true );
    }

    @Test
    void shouldDumpAndLoad_nonDefaultUser() throws Exception
    {
        shouldCreateDumpAndLoadDump( false );
    }

    //container.stop() actually runs the killContainer Command, preventing clean shutdown.
    // This runs the actual stop command. Which we set up in createDBContainer to send SIGTERM
    private void stopContainer(GenericContainer container)
    {
        container.getDockerClient().stopContainerCmd( container.getContainerId() ).exec();
    }

    private void shouldCreateDumpAndLoadDump( boolean asDefaultUser ) throws Exception
    {
        Path testOutputFolder = HostFileSystemOperations.createTempFolder( "dumpandload-" );
        Path firstDataDir;
        Path secondDataDir;
        Path backupDir;

        // start a database and populate it
        try(GenericContainer container = createDBContainer( asDefaultUser ))
        {
            firstDataDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
                    container, "data1-", "/data", testOutputFolder );
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", "none" );
            stopContainer( container );
        }

        // use admin container to create dump
        try(GenericContainer admin = createAdminContainer( asDefaultUser ))
        {
            HostFileSystemOperations.mountHostFolderAsVolume( admin, firstDataDir, "/data" );
            backupDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
                    admin, "dump-", "/backup", testOutputFolder );
            admin.withCommand( "neo4j-admin", "dump", "--database=neo4j", "--to=/backup/neo4j.dump" );
            admin.start();
        }
        Assertions.assertTrue( backupDir.resolve( "neo4j.dump" ).toFile().exists(), "dump file not created");

        // dump file exists. Now try to load it into a new database.
        // use admin container to create dump
        try(GenericContainer admin = createAdminContainer( asDefaultUser ))
        {
            secondDataDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
                    admin, "data2-", "/data", testOutputFolder );
            HostFileSystemOperations.mountHostFolderAsVolume( admin, backupDir, "/backup" );
            admin.withCommand( "neo4j-admin", "load", "--database=neo4j", "--from=/backup/neo4j.dump" );
            admin.start();
        }

        // verify data in 2nd data directory by starting a database and verifying data we populated earlier
        try(GenericContainer container = createDBContainer( asDefaultUser ))
        {
            HostFileSystemOperations.mountHostFolderAsVolume( container, secondDataDir, "/data" );
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.verifyDataInContainer( "neo4j", "none" );
        }
    }
}
