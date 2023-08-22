package com.neo4j.docker.coredb;

import com.neo4j.docker.coredb.configurations.Configuration;
import com.neo4j.docker.coredb.configurations.Setting;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.StartupDetector;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TestPasswords
{
    private static Logger log = LoggerFactory.getLogger( TestPasswords.class);
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    private GenericContainer createContainer( boolean asCurrentUser )
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( Wait.forHttp( "/" )
                                  .forPort( 7474 )
                                  .forStatusCode( 200 )
                                  .withStartupTimeout( Duration.ofSeconds( 90 ) ) );
        if(asCurrentUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

	@Test
	void testNoPassword()
	{
		// we test that setting NEO4J_AUTH to none lets the database start in TestBasic.java but not that we can read/write the database
		try(GenericContainer container = createContainer( false ))
		{
			container.withEnv( "NEO4J_AUTH", "none");
            StartupDetector.makeContainerWaitForNeo4jReady(container, "none", Duration.ofSeconds( 90 ));
			container.start();
            DatabaseIO db = new DatabaseIO(container);
            db.putInitialDataIntoContainer( "neo4j", "none" );
			db.verifyInitialDataInContainer( "neo4j", "none" );
		}
	}

	@Test
    void testPasswordCantBeNeo4j() throws Exception
    {
        try(GenericContainer failContainer = new GenericContainer( TestSettings.IMAGE_ID ).withLogConsumer( new Slf4jLogConsumer( log ) ))
        {
            if ( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE )
            {
                failContainer.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" );
            }
            failContainer.withEnv( "NEO4J_AUTH", "neo4j/neo4j" );
            failContainer.start();

            WaitingConsumer waitingConsumer = new WaitingConsumer();
            failContainer.followOutput( waitingConsumer );

            Assertions.assertDoesNotThrow( () -> waitingConsumer.waitUntil(
                    frame -> frame.getUtf8String().contains("Invalid value for password" ), 20, TimeUnit.SECONDS ),
                                           "did not error due to invalid password" );
        }
    }

	@Test
	void testDefaultPasswordAndPasswordResetIfNoNeo4jAuthSet()
	{
		try(GenericContainer container = createContainer( true ))
        {
            log.info( "Starting first container as current user and not specifying NEO4J_AUTH" );
            StartupDetector.makeContainerWaitForDatabaseReady(container, "neo4j", "neo4j", "neo4j", Duration.ofSeconds(90));
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            // try with no password, this should fail because the default password should be applied with no NEO4J_AUTH env variable
			Assertions.assertThrows( org.neo4j.driver.exceptions.AuthenticationException.class,
									 () -> db.putInitialDataIntoContainer( "neo4j", "" ),
									 "Able to access database with no password, even though NEO4J_AUTH=none was not specified!");
			Assertions.assertThrows( org.neo4j.driver.exceptions.ClientException.class,
									 () -> db.putInitialDataIntoContainer( "neo4j", "neo4j" ),
									 "Was not prompted for a new password when using default");
			db.changePassword( "neo4j", "neo4j", "newpassword" );
            db.putInitialDataIntoContainer( "neo4j", "newpassword" );
        }
	}

	@ParameterizedTest(name = "as current user={0}")
    @ValueSource(booleans = {true, false})
    void testCanSetPassword( boolean asCurrentUser ) throws Exception
    {
        // create container and mount /data folder so that data can persist between sessions
        String password = "some_valid_password";
        Path dataMount;

        try(GenericContainer firstContainer = createContainer( asCurrentUser ))
		{
			firstContainer.withEnv( "NEO4J_AUTH", "neo4j/"+password );
            StartupDetector.makeContainerWaitForNeo4jReady(firstContainer, password);
			dataMount = temporaryFolderManager.createTempFolderAndMountAsVolume(
					firstContainer,
					"password-defaultuser-data-",
					"/data" );
			log.info( String.format( "Starting first container as %s user and setting password",
									 asCurrentUser? "current" : "default" ) );
            // create a database with stuff in
            firstContainer.start();
            DatabaseIO db = new DatabaseIO(firstContainer);
            db.putInitialDataIntoContainer( "neo4j", password );
        }

        // with a new container, check the database data.
        try(GenericContainer secondContainer = createContainer( asCurrentUser ))
        {
            temporaryFolderManager.mountHostFolderAsVolume( secondContainer, dataMount, "/data" );
            log.info( "starting new container with same /data mount as same user without setting password" );
            StartupDetector.makeContainerWaitForNeo4jReady(secondContainer, password);
            secondContainer.start();
            DatabaseIO db = new DatabaseIO(secondContainer);
            db.verifyInitialDataInContainer( "neo4j", password );
        }
    }

    @Test
    void testCanSetPasswordWithDebugging() throws Exception
    {
        String password = "some_valid_password";

        try ( GenericContainer container = createContainer( false  ) )
        {
            container.withEnv( "NEO4J_AUTH", "neo4j/" + password )
                     .withEnv( "NEO4J_DEBUG", "yes" );
            StartupDetector.makeContainerWaitForNeo4jReady( container, password );
            // create a database with stuff in
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( "neo4j", password );
        }
    }

    @ParameterizedTest(name = "as current user={0}")
    @ValueSource(booleans = {true, false})
    void testSettingNeo4jAuthDoesntOverrideExistingPassword( boolean asCurrentUser ) throws Exception
    {
        String password = "some_valid_password";
        Path dataMount;

		try(GenericContainer firstContainer = createContainer( asCurrentUser ))
		{
			firstContainer.withEnv( "NEO4J_AUTH", "neo4j/"+password );
            StartupDetector.makeContainerWaitForNeo4jReady(firstContainer, password);
			dataMount = temporaryFolderManager.createTempFolderAndMountAsVolume(
					firstContainer,
					"password-envoverride-data-",
					"/data" );

			// create a database with stuff in
			log.info( String.format( "Starting first container as %s user and setting password",
									 asCurrentUser? "current" : "default" ) );
			firstContainer.start();
			DatabaseIO db = new DatabaseIO(firstContainer);
			db.putInitialDataIntoContainer( "neo4j", password );
        }

        // with a new container, check the database data.
        try(GenericContainer secondContainer = createContainer( asCurrentUser ))
        {
            String wrongPassword = "not_the_password";
            secondContainer.withEnv( "NEO4J_AUTH", "neo4j/"+wrongPassword );
            temporaryFolderManager.mountHostFolderAsVolume( secondContainer, dataMount, "/data" );
            log.info( "starting new container with same /data mount as same user without setting password" );
            secondContainer.start();
            DatabaseIO db = new DatabaseIO(secondContainer);
            db.verifyInitialDataInContainer( "neo4j", password );
        Assertions.assertThrows( org.neo4j.driver.exceptions.AuthenticationException.class,
                () -> db.verifyConnectivity( "neo4j", wrongPassword) );
        }
    }

    @Test
    void testPromptsForPasswordReset()
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,6,0 ) ),
                                "Require password reset is only a feature in 3.6 onwards");
        try(GenericContainer container = createContainer( false ))
        {
            String user = "neo4j";
            String intialPass = "apassword";
            String resetPass = "new_password";
            container.withEnv("NEO4J_AUTH", user+"/"+intialPass+"/true" );
            StartupDetector.makeContainerWaitForDatabaseReady(container, user, intialPass, "neo4j",
                    Duration.ofSeconds(60));
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            Assertions.assertThrows( org.neo4j.driver.exceptions.ClientException.class,
                                     () -> db.putInitialDataIntoContainer( user, intialPass ),
                                     "Neo4j did not error because of password reset requirement");

            db.changePassword( user, intialPass, resetPass );
            db.putInitialDataIntoContainer( user, resetPass );
            db.verifyInitialDataInContainer( user, resetPass );
        }
    }

    @Test
    void testWarnAndFailIfPasswordLessThan8Chars() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        try(GenericContainer failContainer = createContainer( false ))
        {
            failContainer.withEnv( "NEO4J_AUTH", "neo4j/123" )
                         .withStartupCheckStrategy( new OneShotStartupCheckStrategy() );
            Assertions.assertThrows( ContainerLaunchException.class, () -> failContainer.start(),
                                     "Neo4j started even though initial password was too short" );
            String logsOut = failContainer.getLogs();
            Assertions.assertTrue( logsOut.contains( "Invalid value for password" ),
                                   "did not error due to too short password");
            Assertions.assertFalse( logsOut.contains( "Remote interface available at http://localhost:7474/" ),
                                    "Neo4j started even though an invalid password was set");
        }
    }

    @Test
    void testWarnAndFailIfPasswordLessThanOverride() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        try(GenericContainer failContainer = createContainer( false ))
        {
            failContainer.withEnv( "NEO4J_AUTH", "neo4j/123" )
                         .withEnv(Configuration.getConfigurationNameMap().get( Setting.MINIMUM_PASSWORD_LENGTH ).envName, "20")
                         .withStartupCheckStrategy( new OneShotStartupCheckStrategy() );
            Assertions.assertThrows( ContainerLaunchException.class, () -> failContainer.start(),
                                     "Neo4j started even though initial password was too short" );
            String logsOut = failContainer.getLogs();
            Assertions.assertTrue( logsOut.contains( "Invalid value for password" ),
                                   "did not error due to too short password");
            Assertions.assertFalse( logsOut.contains( "Remote interface available at http://localhost:7474/" ),
                                    "Neo4j started even though an invalid password was set");
        }
    }

    @Test
    void shouldNotWarnAboutMinimumPasswordLengthIfSettingOverridden_env() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        try(GenericContainer container = createContainer( false ))
        {
            container.withEnv( "NEO4J_AUTH", "neo4j/123" )
                     .withEnv(Configuration.getConfigurationNameMap().get( Setting.MINIMUM_PASSWORD_LENGTH ).envName, "2");
            container.start();
            String logs = container.getLogs();
            Assertions.assertFalse( logs.contains( "Invalid value for password. The minimum password length is 8 characters." ),
                                    "Should not error about minimum password length if overridden.");
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( "neo4j", "123" );
        }
    }

    @Test
    void shouldNotWarnAboutMinimumPasswordLengthIfSettingOverridden_conf() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        try(GenericContainer container = createContainer( false ))
        {
            Path confMount = temporaryFolderManager.createTempFolderAndMountAsVolume(
            		container,
					"noMinimumPasswordLength-conf-",
					"/conf" );
            Files.writeString(confMount.resolve( "neo4j.conf" ),
                              Configuration.getConfigurationNameMap().get( Setting.MINIMUM_PASSWORD_LENGTH ).name+"=2");

            container.withEnv( "NEO4J_AUTH", "neo4j/123" );
            container.start();
            String logs = container.getLogs();
            Assertions.assertFalse( logs.contains( "Invalid value for password. The minimum password length is 8 characters." ),
                                    "Should not error about minimum password length if overridden.");
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( "neo4j", "123" );
        }
    }
}
