package com.neo4j.docker;

import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.neo4j.cypher.internal.v4_0.expressions.functions.E;


public class TestSSL
{
	private static final Logger log = LoggerFactory.getLogger( TestSSL.class );

	@BeforeAll
	static void filterOutPre40()
	{
		Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ),
								"This test only applies to 4.0 onwards ssl configuration");
	}

	private GenericContainer createBasicContainer()
	{
		GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
		container.withEnv( "NEO4J_AUTH", "none" )
				 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
				 .withExposedPorts( 7473, 7474, 7687 )
				 .withLogConsumer( new Slf4jLogConsumer( log ) )
				 .waitingFor( Wait.forHttp( "/" )
								  .forPort( 7474 )
								  .withStartupTimeout( Duration.ofSeconds( 60 ) ) );
		return container;
	}

	@Test
	void testHttpsNotAlreadyEnabled()
	{
		try(GenericContainer container = createBasicContainer())
		{
			container.start();
			Assertions.assertTrue( container.isRunning(), "container did not start" );
			Assertions.assertFalse( isHttpsResponsive( container ), "HTTPS was enabled when we didn't supply certificates" );
		}
	}

	@Test
	void testCanEncryptHttps() throws Exception
	{
		try(GenericContainer container = createBasicContainer())
		{
			Path sslFolder = createSSLDirectory( container, "https" );
			HostFileSystemOperations.mountHostFolderAsVolume( container, sslFolder, "/var/lib/neo4j/certificates" );
			SetContainerUser.nonRootUser( container );
			container.withEnv( "NEO4J_dbms_connector_https_enabled", "true" )
					 .withEnv( "NEO4J_dbms_ssl_policy_https_enabled", "true" );
			container.start();
			Assertions.assertTrue( isHttpsResponsive( container ), "https port was not responsive");
		}
	}

	@Test
	void testCanEncryptHttpsSimplified() throws Exception
	{
		try(GenericContainer container = createBasicContainer())
		{
			Path sslFolder = createSSLDirectory( container, "https" );
			HostFileSystemOperations.mountHostFolderAsVolume( container, sslFolder, "/ssl" );
			SetContainerUser.nonRootUser( container );
			container.start();
			Assertions.assertTrue( isHttpsResponsive( container ), "https port was not responsive");
		}
	}

	@Test
	void testCanEncryptHttpsAndBoltSimplified() throws Exception
	{
		try(GenericContainer container = createBasicContainer())
		{
			Path sslFolder = createSSLDirectory( container, "https", "bolt" );
			HostFileSystemOperations.mountHostFolderAsVolume( container, sslFolder, "/ssl" );
			SetContainerUser.nonRootUser( container );
			container.start();
			Assertions.assertTrue( isHttpsResponsive( container ), "https port was not responsive");
		}
	}

	@Test
	void testWarnsIfNoSSLPoliciesFound() throws Exception
	{
		try(GenericContainer container = createBasicContainer())
		{
			Path sslFolder = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "ssl-empty-", "/ssl" );
			SetContainerUser.nonRootUser( container );
			container.start();
			String stdout = container.getLogs();
			Assertions.assertTrue( stdout.contains( "no valid ssl policies" ), "Did not print warning that no ssl policies were found");
		}
	}

	private boolean isHttpsResponsive( GenericContainer container)
	{
		// it's not ideal to shell out to curl, but the https library in java was incredibly complicated
		// if you're using self-signed, untrustworthy certificates. This solution isn't great, but is much more readable.
		try
		{
			String url = "https://" + container.getContainerIpAddress() +":"+ container.getMappedPort( 7473 );
			ProcessBuilder pb = new ProcessBuilder( "curl", "-sk", url ).redirectErrorStream( true );
			Process proc = pb.start();
			proc.waitFor();
			BufferedReader reader = new BufferedReader( new InputStreamReader( proc.getInputStream()));
			StringBuilder stdout = new StringBuilder();

			String line = reader.readLine();
			while(line != null)
			{
				stdout.append( line );
				line = reader.readLine();
			}
			log.debug( "curl returned: "+ stdout.toString() );
			reader.close();
			proc.destroy();
			return (proc.exitValue() == 0);
		}
		catch ( IOException | InterruptedException ex )
		{
			log.error(ex.getMessage());
			ex.printStackTrace();
		}
		return false;
	}

	private Path createSSLDirectory( GenericContainer container, String... scopes ) throws IOException
	{
		Path sslFolder = HostFileSystemOperations.createTempFolder( "ssl-" );

		for(String scope : scopes)
		{
			Path scopedFolder = Files.createDirectories( sslFolder.resolve( scope ) );
			Files.createDirectories( scopedFolder.resolve( "trusted" ) );
			Files.createDirectories( scopedFolder.resolve( "revoked" ) );
			// copy certificates to the required locations
			Files.copy( getResource( "ssl/private.key" ), scopedFolder.resolve( "private.key" ) );
			Files.copy( getResource( "ssl/public.crt" ), scopedFolder.resolve( "public.crt" ) );
			Files.copy( getResource( "ssl/public.crt" ), scopedFolder.resolve( "trusted" ).resolve( "public.crt" ) );
		}
		return sslFolder;
	}

	private InputStream getResource(String path)
	{
        return getClass().getClassLoader().getResourceAsStream(path);
    }
}
