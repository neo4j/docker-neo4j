package com.neo4j.docker;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

@Disabled
public class TestDumpLoad
{
	private static final Logger log = LoggerFactory.getLogger( TestDumpLoad.class );

    private GenericContainer createContainer( )
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
				 .withEnv( "NEO4J_AUTH", "none" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( Wait.forHttp( "/" )
								  .forPort( 7474 )
								  .forStatusCode( 200 )
								  .withStartupTimeout( Duration.ofSeconds( 90 ) ) );
        return container;
    }

	@Test
	void dumpCompletes() throws IOException, InterruptedException
	{
		Path dataDir, dumpDir, logDir;
		Path testOutputFolder = HostFileSystemOperations.createTempFolder( "dumpCompletes-" );

		try(GenericContainer container = createContainer())
		{
			log.info( "creating a populated database to back up" );
			dataDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					testOutputFolder,
					"data-",
					"/data" );
			logDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					testOutputFolder,
					"logs-",
					"/logs"
			);
			SetContainerUser.nonRootUser( container );
            container.start();
            DatabaseIO db = new DatabaseIO( container);
            db.putInitialDataIntoContainer( "","" );
		}
		// at this point, because we exited the try, the container should have closed and neo4j should be shut down.
		// However, it looks like the dump command fails because the database isn't shutdown properly.
		// This works when I run the docker stop command from a script but not here.

		log.info( "database created, Neo4j stopped" );

		try(GenericContainer container = createContainer())
		{
			log.info( "Doing database dump" );
			//start container and call neo4j-admin instead of default command
			HostFileSystemOperations.mountHostFolderAsVolume(
					container,
					dataDir,
					"/data");
			HostFileSystemOperations.mountHostFolderAsVolume(
					container,
					logDir,
					"/logs");
			dumpDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					testOutputFolder,
					"dump-",
					"/dump"
			);
			// if we don't set the user, then neo4j-admin will fail because of write permissions on the destination folder.
			SetContainerUser.nonRootUser( container );
			container.withCommand( "neo4j-admin", "dump", "--to=/dump", "--verbose" );
			container.start();
		}

		// do some stuff to load dumpfile back into a database
		// neo4j-admin load --from=/dump/neo4j.dump --database=neo4j
	}
}
