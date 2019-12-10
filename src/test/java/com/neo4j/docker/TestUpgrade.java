package com.neo4j.docker;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.nio.file.Path;

public class TestUpgrade
{
	private static final Logger log = LoggerFactory.getLogger( TestUpgrade.class );
	private final String user = "neo4j";
	private final String password = "quality";

	private GenericContainer makeContainer(String image)
	{
        GenericContainer container = new GenericContainer( image );
        container.withEnv( "NEO4J_AUTH", user + "/" + password )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474 )
                 .withExposedPorts( 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        return container;
	}

	@Test
	void canUpgradeFromBeforeFilePermissionFix35() throws Exception
	{
		Neo4jVersion beforeFix = new Neo4jVersion( 3,5,3 );
		String beforeFixImage = (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE)?  "neo4j:3.5.3-enterprise":"neo4j:3.5.3";
		Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isNewerThan( beforeFix ), "test only applicable to latest 3.5 docker" );
		Assumptions.assumeFalse( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ),
								"test only applicable to latest 3.5 docker" );

		Path dataMount = HostFileSystemOperations.createTempFolder( "data-upgrade-" );
		log.info( "created folder " + dataMount.toString() + " to test upgrade" );

		try(GenericContainer container = makeContainer( beforeFixImage ))
		{
			HostFileSystemOperations.mountHostFolderAsVolume( container, dataMount, "/data" );
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.putInitialDataIntoContainer( user, password );
		}

		try(GenericContainer container = makeContainer( TestSettings.IMAGE_ID ))
		{
			HostFileSystemOperations.mountHostFolderAsVolume( container, dataMount, "/data" );
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.verifyDataInContainer( user, password );
		}
	}

	// todo add test for 4.0 when I can figure out how to close a container cleanly
}
