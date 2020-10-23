package com.neo4j.docker;

import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestExtendedConf
{
	private static final Logger log = LoggerFactory.getLogger( TestExtendedConf.class );
	@BeforeAll
	static void ensureFeaturePresent()
	{
		Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4,2,0 ) ),
								"Extended configuration feature not available before 4.2" );
	}

	protected GenericContainer createContainer()
	{
        return new GenericContainer(TestSettings.IMAGE_ID)
                .withEnv("NEO4J_AUTH", "none")
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
				.withEnv( "EXTENDED_CONF", "yeppers" )
                .withExposedPorts(7474, 7687)
				.waitingFor( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) )
                .withLogConsumer(new Slf4jLogConsumer( log));
    }


	@Test
	public void shouldStartWithExtendedConf()
	{
        try(GenericContainer container = createContainer())
        {
            container.setWaitStrategy( Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ) );
            container.start();
            Assertions.assertTrue( container.isRunning() );
        }
	}

	@Ignore
	@Test
	void testReadsTheExtendedConfFile_defaultUser() throws Exception
	{
		// set up test folders
		Path testOutputFolder = HostFileSystemOperations.createTempFolder( "extendedConfIsRead-" );
		Path confFolder = HostFileSystemOperations.createTempFolder( "conf-", testOutputFolder );
		Path logsFolder = HostFileSystemOperations.createTempFolder( "logs-", testOutputFolder );

		// copy configuration file and set permissions
		Path confFile = Paths.get( "src", "test", "resources", "confs", "ExtendedConf.conf" );
		Files.copy( confFile, confFolder.resolve( "neo4j.conf" ) );
		setFileOwnerToNeo4j( confFolder.resolve( "neo4j.conf" ) );
		chmod600( confFolder.resolve( "neo4j.conf" ) );

		// start  container
		try(GenericContainer container = createContainer())
		{
			runContainerAndVerify( container, confFolder, logsFolder );
		}
	}

	@Test
	void testReadsTheExtendedConfFile_nonRootUser() throws Exception
	{
		// set up test folders
		Path testOutputFolder = HostFileSystemOperations.createTempFolder( "extendedConfIsRead-" );
		Path confFolder = HostFileSystemOperations.createTempFolder( "conf-", testOutputFolder );
		Path logsFolder = HostFileSystemOperations.createTempFolder( "logs-", testOutputFolder );

		// copy configuration file and set permissions
		Path confFile = Paths.get( "src", "test", "resources", "confs", "ExtendedConf.conf" );
		Files.copy( confFile, confFolder.resolve( "neo4j.conf" ) );
		chmod600( confFolder.resolve( "neo4j.conf" ) );

		try(GenericContainer container = createContainer())
		{
			SetContainerUser.nonRootUser( container );
			container.withFileSystemBind( "/etc/passwd", "/etc/passwd", BindMode.READ_ONLY );
			container.withFileSystemBind( "/etc/group", "/etc/group", BindMode.READ_ONLY );
			runContainerAndVerify( container, confFolder, logsFolder );
		}
	}

	private void runContainerAndVerify(GenericContainer container, Path confFolder, Path logsFolder) throws Exception
	{
		HostFileSystemOperations.mountHostFolderAsVolume( container, confFolder, "/conf" );
		HostFileSystemOperations.mountHostFolderAsVolume( container, logsFolder, "/logs" );

		container.start();

		Path debugLog = logsFolder.resolve("debug.log");
		Assert.assertTrue("Did not write debug log", debugLog.toFile().exists());

		//Check if the container reads the conf file
		Stream<String> lines = Files.lines( debugLog);
		Optional<String> isMatch = lines.filter( s -> s.contains("dbms.logs.http.rotation.keep_number=20")).findFirst();
		lines.close();
		Assertions.assertTrue(  isMatch.isPresent(), "dbms.max_databases was not set correctly");
	}

	private void chmod600(Path file) throws IOException
	{
		Files.setPosixFilePermissions( file,
									   new HashSet<PosixFilePermission>()
									   {{
										   add( PosixFilePermission.OWNER_READ );
										   add( PosixFilePermission.OWNER_WRITE );
									   }} );
	}

	private void setFileOwnerToNeo4j(Path file) throws Exception
	{
		ProcessBuilder pb = new ProcessBuilder( "chown", "7474:7474", file.toAbsolutePath().toString() ).redirectErrorStream( true );
		Process proc = pb.start();
		proc.waitFor();
		if(proc.exitValue() != 0)
		{
			String errorMsg = new BufferedReader( new InputStreamReader( proc.getInputStream() ) )
					.lines()
					.collect( Collectors.joining() );
			// if we cannot set up test conditions properly, abort test but don't register a test failure.
			Assumptions.assumeTrue( false,
									"Could not change owner of test file to 7474. User needs to be in sudoers list. Error:\n" +
									errorMsg );
		}
		return;
	}
}
