package com.neo4j.docker;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileSystemOperations;
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
public class TestBackupRestore
{
	private static final Logger log = LoggerFactory.getLogger( TestBackupRestore.class );

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
	void dumpCompletes() throws IOException
	{
		Path backupDir, dumpDir, logDir;

		try(GenericContainer container = createContainer())
		{
			log.info( "creating a populated database to back up" );
			backupDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					"backup-data-",
					"/data" );
            container.start();
            DatabaseIO db = new DatabaseIO( container);
            db.putInitialDataIntoContainer( "","" );
            container.stop();
		}
		log.info( "database created, Neo4j stopped" );

		try(GenericContainer container = createContainer())
		{
			//start container and call neo4j-admin instead of default command
			HostFileSystemOperations.mountHostFolderAsVolume(
					container,
					backupDir,
					"/data");
			dumpDir = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					"dumpdb-",
					"/dump"
			);
			container.withCommand( "neo4j-admin", "dump", "--to=/dump", "--verbose" );
			container.start();
		}
	}
}
