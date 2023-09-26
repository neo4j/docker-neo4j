package com.neo4j.docker.coredb;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

@Tag("upgrade")
public class TestUpgrade
{
	private static final Logger log = LoggerFactory.getLogger( TestUpgrade.class );
	private final String user = "neo4j";
	private final String password = "verylongpassword";
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

	private GenericContainer makeContainer( DockerImageName image)
	{
        GenericContainer container = new GenericContainer<>( image );
        container.withEnv( "NEO4J_AUTH", user + "/" + password )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474 )
                 .withExposedPorts( 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );
        return container;
	}

	private static List<Neo4jVersion> upgradableNeo4jVersionsPre5()
	{
		return Arrays.asList( new Neo4jVersion( 3, 5, 3 ), // 3.5.6 image introduced file permission changes, so we need to test upgrades before that version
							  new Neo4jVersion( 3, 5, 7 ),
							  Neo4jVersion.NEO4J_VERSION_400,
							  new Neo4jVersion( 4, 1, 0 ),
							  new Neo4jVersion( 4, 4, 0 ));
    }

    private static List<Neo4jVersion> upgradableNeo4jVersions()
    {
        List<Neo4jVersion> out = new ArrayList<>(TestSettings.NEO4J_VERSION.minor);
        for(int minor=1; minor < TestSettings.NEO4J_VERSION.minor; minor++ )
        {
            out.add( new Neo4jVersion( 5, minor, 0 ) );
        }
        return out;
    }

    private static void assumeUpgradeSupported( Neo4jVersion upgradeFrom )
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isNewerThan( upgradeFrom ),
                    "cannot upgrade from " + upgradeFrom + " to " + TestSettings.NEO4J_VERSION);
        if(isArm()) Assumptions.assumeTrue( upgradeFrom.isAtLeastVersion( new Neo4jVersion( 4, 4, 0 ) ), "ARM only supported since 4.4" );

        // if we're preparing a new release, then it's possible the version we're upgrading from hasn't been released to
        // dockerhub, so the test will fail when pulling the upgrade-from image.
        // If this happens we should ignore rather than fail the test.
        try
        {
            RemoteDockerImage img = new RemoteDockerImage( getUpgradeFromImage( upgradeFrom ) );
            img.get(); // docker pull
        }
        catch ( NotFoundException nfex )
        {
            // purposely fail an assumption if the image was not found
            Assumptions.assumeTrue( false, "neo4j:"+upgradeFrom+" is not available on dockerhub yet. Ignoring test.");
        }
    }

    private static boolean isArm()
    {
        return System.getProperty( "os.arch" ).equals( "aarch64" );
    }

	@ParameterizedTest(name = "from_{0}")
    @MethodSource( "upgradableNeo4jVersionsPre5" )
	void canUpgradeNeo4j_fileMountsPre5( Neo4jVersion upgradeFrom) throws Exception
	{
		assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ),
                    "this test only for upgrades before 5.0: " + TestSettings.NEO4J_VERSION );
		testUpgradeFileMounts( upgradeFrom );
	}

	@ParameterizedTest(name = "from_{0}")
	@MethodSource( "upgradableNeo4jVersionsPre5" )
	void canUpgradeNeo4j_namedVolumesPre5(Neo4jVersion upgradeFrom) throws Exception
	{
		assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( Neo4jVersion.NEO4J_VERSION_500 ),
                    "this test only for upgrades before 5.0: " + TestSettings.NEO4J_VERSION );
		testUpgradeNamedVolumes( upgradeFrom );
	}

	@ParameterizedTest(name = "from_{0}")
    @MethodSource( "upgradableNeo4jVersions" )
	void canUpgradeNeo4j_fileMounts( Neo4jVersion upgradeFrom) throws Exception
	{
		assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ),
                    "this test only for upgrades after 5.0: " + TestSettings.NEO4J_VERSION );
		testUpgradeFileMounts( upgradeFrom );
	}

	@ParameterizedTest(name = "from_{0}")
	@MethodSource( "upgradableNeo4jVersions" )
	void canUpgradeNeo4j_namedVolumes(Neo4jVersion upgradeFrom) throws Exception
	{
		assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ),
                    "this test only for upgrades after 5.0: " + TestSettings.NEO4J_VERSION );
		testUpgradeNamedVolumes( upgradeFrom );
	}

	private void testUpgradeFileMounts( Neo4jVersion upgradeFrom ) throws IOException
	{
		assumeUpgradeSupported( upgradeFrom );

		Path tmpMountFolder = temporaryFolderManager.createTempFolder( "upgrade-" + upgradeFrom.toString() + "-" );
		Path data, logs, imports, metrics;

		try(GenericContainer container = makeContainer( getUpgradeFromImage( upgradeFrom ) ))
		{
			data = temporaryFolderManager.createTempFolderAndMountAsVolume( container,
                                                                              "data-",
                                                                              "/data",
																			  tmpMountFolder );
			logs = temporaryFolderManager.createTempFolderAndMountAsVolume( container,
                                                                              "logs-",
                                                                              "/logs",
																			  tmpMountFolder );
			imports = temporaryFolderManager.createTempFolderAndMountAsVolume( container,
                                                                                 "import-",
                                                                                 "/import",
																				 tmpMountFolder );
			metrics = temporaryFolderManager.createTempFolderAndMountAsVolume( container,
                                                                                 "metrics-",
                                                                                 "/metrics",
																				 tmpMountFolder );
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.putInitialDataIntoContainer( user, password );
			// stops container cleanly so that neo4j process has enough time to end. The autoclose doesn't seem to block.
			container.getDockerClient().stopContainerCmd( container.getContainerId() ).exec();
		}

		try(GenericContainer container = makeContainer( TestSettings.IMAGE_ID ))
		{
			temporaryFolderManager.mountHostFolderAsVolume( container, data, "/data" );
			temporaryFolderManager.mountHostFolderAsVolume( container, logs, "/logs" );
			temporaryFolderManager.mountHostFolderAsVolume( container, imports, "/import" );
			temporaryFolderManager.mountHostFolderAsVolume( container, metrics, "/metrics" );
			container.withEnv( "NEO4J_dbms_allow__upgrade", "true" );
			container.start();
			DatabaseIO db = new DatabaseIO( container );
			db.verifyInitialDataInContainer( user, password );
		}
	}

	private void testUpgradeNamedVolumes( Neo4jVersion upgradeFrom )
	{
		assumeUpgradeSupported(upgradeFrom);

		String id = String.format( "%04d", new Random().nextInt( 10000 ));
		log.info( "creating volumes with id: "+id );

		try(GenericContainer container = makeContainer(getUpgradeFromImage( upgradeFrom )))
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
			db.verifyInitialDataInContainer( user, password );
		}
	}

	private static DockerImageName getUpgradeFromImage(Neo4jVersion ver)
	{
		if(TestSettings.EDITION == TestSettings.Edition.ENTERPRISE)
		{
			return DockerImageName.parse("neo4j:" + ver.toString() + "-enterprise");
		}
		else
		{
			return DockerImageName.parse("neo4j:" + ver.toString());
		}
	}
}
