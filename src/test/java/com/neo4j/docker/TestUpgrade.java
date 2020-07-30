package com.neo4j.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

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

	private static List<Neo4jVersion> upgradableNeo4jVersions()
	{
		return Arrays.asList( new Neo4jVersion( 3, 5, 3 ),
							  new Neo4jVersion( 3, 5, 7 ),
							  Neo4jVersion.NEO4J_VERSION_400,
							  new Neo4jVersion( 4,1,0 ));
	}


	@ParameterizedTest(name = "upgrade from {0}")
    @MethodSource("upgradableNeo4jVersions")
	void canUpgradeNeo4j_fileMounts(Neo4jVersion upgradeFrom) throws Exception
	{
		Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isNewerThan( upgradeFrom ), "cannot upgrade from newer version "+upgradeFrom.toString() );
		String upgradeFromImage = getUpgradeFromImage( upgradeFrom );
		Path tmpMountFolder = HostFileSystemOperations.createTempFolder( "upgrade-"+upgradeFrom.major+upgradeFrom.minor+"-" );
		Path data, logs, imports, metrics;

		try(GenericContainer container = makeContainer( upgradeFromImage ))
		{
			data = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, tmpMountFolder, "data-", "/data" );
			logs = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, tmpMountFolder, "logs-", "/logs" );
			imports = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, tmpMountFolder, "import-", "/import" );
			metrics = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, tmpMountFolder, "metrics-", "/metrics" );
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.putInitialDataIntoContainer( user, password );
			// stops container cleanly so that neo4j process has enough time to end. The autoclose doesn't seem to block.
			container.getDockerClient().stopContainerCmd( container.getContainerId() ).exec();
		}

		try(GenericContainer container = makeContainer( TestSettings.IMAGE_ID ))
		{
			HostFileSystemOperations.mountHostFolderAsVolume( container, data, "/data" );
			HostFileSystemOperations.mountHostFolderAsVolume( container, logs, "/logs" );
			HostFileSystemOperations.mountHostFolderAsVolume( container, imports, "/import" );
			HostFileSystemOperations.mountHostFolderAsVolume( container, metrics, "/metrics" );
			container.withEnv( "NEO4J_dbms_allow__upgrade", "true" );
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.verifyDataInContainer( user, password );
		}
	}

	@ParameterizedTest(name = "upgrade from {0}")
	@MethodSource("upgradableNeo4jVersions")
	void canUpgradeNeo4j_namedVolumes(Neo4jVersion upgradeFrom) throws Exception
	{
		Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isNewerThan( upgradeFrom ), "cannot upgrade from newer version "+upgradeFrom.toString() );
		String upgradeFromImage = getUpgradeFromImage( upgradeFrom );
		String id = String.format( "%04d", new Random().nextInt( 10000 ));
		log.info( "creating volumes with id: "+id );

		try(GenericContainer container = makeContainer( upgradeFromImage ))
		{
			container.withCreateContainerCmdModifier(
					(Consumer<CreateContainerCmd>) cmd -> cmd.getHostConfig().withBinds(
							Bind.parse("upgrade-conf-"+id+":/conf"),
							Bind.parse("upgrade-data-"+id+":/data"),
							Bind.parse("upgrade-import-"+id+":/import"),
							Bind.parse("upgrade-logs-"+id+":/logs"),
							Bind.parse("upgrade-metrics-"+id+":/metrics"),
							Bind.parse("upgrade-plugins-"+id+":/plugins")
					));
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.putInitialDataIntoContainer( user, password );
			container.getDockerClient().stopContainerCmd( container.getContainerId() ).exec();
		}

		try(GenericContainer container = makeContainer( TestSettings.IMAGE_ID ))
		{
			container.withCreateContainerCmdModifier(
					(Consumer<CreateContainerCmd>) cmd -> cmd.getHostConfig().withBinds(
							Bind.parse("upgrade-conf-"+id+":/conf"),
							Bind.parse("upgrade-data-"+id+":/data"),
							Bind.parse("upgrade-import-"+id+":/import"),
							Bind.parse("upgrade-logs-"+id+":/logs"),
							Bind.parse("upgrade-metrics-"+id+":/metrics"),
							Bind.parse("upgrade-plugins-"+id+":/plugins")
					));
			container.withEnv( "NEO4J_dbms_allow__upgrade", "true" );
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.verifyDataInContainer( user, password );
		}
	}


	private String getUpgradeFromImage(Neo4jVersion ver)
	{
		if(TestSettings.EDITION == TestSettings.Edition.ENTERPRISE)
		{
			return "neo4j:" + ver.toString() + "-enterprise";
		}
		else
		{
			return "neo4j:" + ver.toString();
		}
	}
}
