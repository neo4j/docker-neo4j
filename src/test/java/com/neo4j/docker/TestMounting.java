package com.neo4j.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileSystemOperations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;


public class TestMounting
{
	private static Logger log = LoggerFactory.getLogger( TestMounting.class );

	static Stream<Arguments> defaultUserFlagSecurePermissionsFlag()
	{
		// "asUser={0}, secureFlag={1}"
		// expected behaviour is that if you set --user flag, your data should be read/writable
		// if you don't set --user flag then read/writability should be controlled by the secure file permissions flag
		// the asUser=true, secureflag=false combination is tested separately because the container should fail to start.
		return Stream.of(
				Arguments.arguments( false, false ),
				Arguments.arguments(  true, false ),
				Arguments.arguments(  true, true  ));
	}

	private GenericContainer setupBasicContainer( boolean asCurrentUser, boolean isSecurityFlagSet )
	{
		log.info( "Running as user {}, {}",
				  asCurrentUser?"non-root":"root",
				  isSecurityFlagSet?"with secure file permissions":"with unsecured file permissions" );

		GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
		container.withExposedPorts( 7474, 7687 )
				 .withLogConsumer( new Slf4jLogConsumer( log ) )
				 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
				 .withEnv( "NEO4J_AUTH", "none" );
		if(asCurrentUser)
		{
			SetContainerUser.nonRootUser( container );
		}
		if(isSecurityFlagSet)
		{
			container.withEnv( "SECURE_FILE_PERMISSIONS", "yes" );
		}
		return container;
	}

	private void verifySingleFolder( Path folderToCheck, boolean shouldBeWritable )
	{
		String folderForDiagnostics = folderToCheck.toAbsolutePath().toString();

		Assertions.assertTrue( folderToCheck.toFile().exists(), "did not create " + folderForDiagnostics + " folder on host" );
		if( shouldBeWritable )
		{
			Assertions.assertTrue( folderToCheck.toFile().canRead(), "cannot read host "+folderForDiagnostics+" folder" );
			Assertions.assertTrue(folderToCheck.toFile().canWrite(),  "cannot write to host "+folderForDiagnostics+" folder" );
		}
	}

	private void verifyDataFolderContentsArePresentOnHost( Path dataMount, boolean shouldBeWritable )
	{
		//verifySingleFolder( dataMount.resolve( "dbms" ), shouldBeWritable );
		verifySingleFolder( dataMount.resolve( "databases" ), shouldBeWritable );

		if(TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ))
		{
			verifySingleFolder( dataMount.resolve( "transactions" ), shouldBeWritable );
		}
	}

	private void verifyLogsFolderContentsArePresentOnHost( Path logsMount, boolean shouldBeWritable )
	{
		verifySingleFolder( logsMount, shouldBeWritable );
		Assertions.assertTrue( logsMount.resolve( "debug.log" ).toFile().exists(),
							   "Neo4j did not write a debug.log file to "+logsMount.toString() );
		Assertions.assertEquals( shouldBeWritable,
								 logsMount.resolve( "debug.log" ).toFile().canWrite(),
								 String.format( "The debug.log file should %sbe writable", shouldBeWritable ? "" : "not ") );
	}


	@Test
	void testDumpConfig( ) throws Exception
	{
		try(GenericContainer container = setupBasicContainer( true, false ))
		{
			Path confMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					"dumpconfig-conf-",
					"/conf" );
			container.setWaitStrategy(
					Wait.forLogMessage( ".*Config Dumped.*", 1 )
						.withStartupTimeout( Duration.ofSeconds( 30 ) ) );
			container.withCommand( "dump-config" );
			container.start();

			Path expectedConfDumpFile = confMount.resolve( "neo4j.conf" );
			Assertions.assertTrue( expectedConfDumpFile.toFile().exists(),
								   "dump-config did not dump the config file to " + confMount.toString() );
		}
	}


	@ParameterizedTest(name = "asUser={0}, secureFlag={1}")
	@MethodSource( "defaultUserFlagSecurePermissionsFlag" )
	void testCanMountJustDataFolder(boolean asCurrentUser, boolean isSecurityFlagSet) throws IOException
	{
		Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
							   "User checks not valid before 3.1" );

		try(GenericContainer container = setupBasicContainer( asCurrentUser, isSecurityFlagSet ))
		{
			Path dataMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					"canmountjustdata-",
					"/data" );
			container.start();

			// neo4j should now have started, so there'll be stuff in the data folder
			// we need to check that stuff is readable and owned by the correct user
			verifyDataFolderContentsArePresentOnHost( dataMount, asCurrentUser );
		}
	}

	@ParameterizedTest(name = "asUser={0}, secureFlag={1}")
	@MethodSource( "defaultUserFlagSecurePermissionsFlag" )
	void testCanMountJustLogsFolder(boolean asCurrentUser, boolean isSecurityFlagSet) throws IOException
	{
		Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
							   "User checks not valid before 3.1" );

		try(GenericContainer container = setupBasicContainer( asCurrentUser, isSecurityFlagSet ))
		{
			Path logsMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					"canmountjustlogs-",
					"/logs" );
			container.start();

			verifyLogsFolderContentsArePresentOnHost( logsMount, asCurrentUser );
		}
	}

	@ParameterizedTest(name = "asUser={0}, secureFlag={1}")
	@MethodSource( "defaultUserFlagSecurePermissionsFlag" )
	void testCanMountDataAndLogsFolder(boolean asCurrentUser, boolean isSecurityFlagSet) throws IOException
	{
		Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
							   "User checks not valid before 3.1" );

		try(GenericContainer container = setupBasicContainer( asCurrentUser, isSecurityFlagSet ))
		{
			Path testOutputFolder = HostFileSystemOperations.createTempFolder( "canmountdataandlogs-" );
			Path dataMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					testOutputFolder,
					"data-",
					"/data" );
			Path logsMount = HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					testOutputFolder,
					"logs-",
					"/logs" );
			container.start();

			verifyDataFolderContentsArePresentOnHost( dataMount, asCurrentUser );
			verifyLogsFolderContentsArePresentOnHost( logsMount, asCurrentUser );
		}
	}

	@Test
	void testCantWriteIfSecureEnabledAndNoPermissions_data() throws IOException
	{
		Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
							   "User checks not valid before 3.1" );

		try(GenericContainer container = setupBasicContainer( false, true ))
		{
			HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					"nopermissioninsecuremode-data-",
					"/data" );

			// currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
			// container.setWaitStrategy( Wait.forLogMessage( "[fF]older /data is not accessible for user", 1 ).withStartupTimeout( Duration.ofSeconds( 20 ) ) );
			container.setWaitStrategy( Wait.forListeningPort()
										   .withStartupTimeout( Duration.ofSeconds( 20 ) ) );
			Assertions.assertThrows( org.testcontainers.containers.ContainerLaunchException.class,
									 () -> container.start(),
									 "Neo4j should not start in secure mode if data folder is unwritable" );
		}
	}

	@Test
	void testCantWriteIfSecureEnabledAndNoPermissions_logs() throws IOException
	{
		Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
							   "User checks not valid before 3.1" );

		try(GenericContainer container = setupBasicContainer( false, true ))
		{
			HostFileSystemOperations.createTempFolderAndMountAsVolume(
					container,
					"nopermissioninsecuremode-logs-",
					"/logs" );

			// currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
			// container.setWaitStrategy( Wait.forLogMessage( "[fF]older /logs is not accessible for user", 1 ).withStartupTimeout( Duration.ofSeconds( 20 ) ) );
			container.setWaitStrategy( Wait.forListeningPort()
										   .withStartupTimeout( Duration.ofSeconds( 20 ) ) );
			Assertions.assertThrows( org.testcontainers.containers.ContainerLaunchException.class,
									 () -> container.start(),
									 "Neo4j should not start in secure mode if logs folder is unwritable" );
		}
	}

	@ParameterizedTest(name = "as current user={0}")
	@ValueSource(booleans = {true, false})
	void canMountAllTheThings_fileMounts(boolean asCurrentUser) throws Exception
	{
		Path testOutputFolder = HostFileSystemOperations.createTempFolder( "mount-everything-" );
		try(GenericContainer container = setupBasicContainer( asCurrentUser, false ))
		{
			HostFileSystemOperations.createTempFolderAndMountAsVolume( container, testOutputFolder, "conf", "/conf" );
			HostFileSystemOperations.createTempFolderAndMountAsVolume( container, testOutputFolder, "data", "/data" );
			HostFileSystemOperations.createTempFolderAndMountAsVolume( container, testOutputFolder, "import", "/import" );
			HostFileSystemOperations.createTempFolderAndMountAsVolume( container, testOutputFolder, "logs", "/logs" );
			HostFileSystemOperations.createTempFolderAndMountAsVolume( container, testOutputFolder, "metrics", "/metrics" );
			HostFileSystemOperations.createTempFolderAndMountAsVolume( container, testOutputFolder, "plugins", "/plugins" );
			container.start();
			DatabaseIO databaseIO = new DatabaseIO( container );
			// do some database writes so that we try writing to writable folders.
			databaseIO.putInitialDataIntoContainer( "neo4j", "none" );
			databaseIO.verifyDataInContainer( "neo4j", "none" );
		}
	}

	@ParameterizedTest(name = "as current user={0}")
	@ValueSource(booleans = {true, false})
	void canMountAllTheThings_namedVolumes(boolean asCurrentUser) throws Exception
	{
		String id = String.format( "%04d", new Random().nextInt( 10000 ));
		try(GenericContainer container = setupBasicContainer( asCurrentUser, false ))
		{
			container.withCreateContainerCmdModifier(
					(Consumer<CreateContainerCmd>) cmd -> cmd.getHostConfig().withBinds(
							Bind.parse("conf-"+id+":/conf"),
							Bind.parse("data-"+id+":/data"),
							Bind.parse("import-"+id+":/import"),
							Bind.parse("logs-"+id+":/logs"),
							//Bind.parse("metrics-"+id+":/metrics"), 	//todo metrics needs to be writable but we aren't chowning in the dockerfile, so a named volume for metrics will fail
							Bind.parse("plugins-"+id+":/plugins")
					));
			container.start();
			DatabaseIO databaseIO = new DatabaseIO( container );
			// do some database writes so that we try writing to writable folders.
			databaseIO.putInitialDataIntoContainer( "neo4j", "none" );
			databaseIO.verifyDataInContainer( "neo4j", "none" );
		}
	}
}
