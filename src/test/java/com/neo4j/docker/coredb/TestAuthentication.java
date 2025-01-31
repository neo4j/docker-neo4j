package com.neo4j.docker.coredb;

import com.neo4j.docker.coredb.configurations.Configuration;
import com.neo4j.docker.coredb.configurations.Setting;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.WaitStrategies;
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
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.WaitingConsumer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TestAuthentication
{
    private static Logger log = LoggerFactory.getLogger(TestAuthentication.class);
    private static final String NEO4J_AUTH_FILE_ENV = "NEO4J_AUTH_PATH";

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    private GenericContainer createContainer( boolean asCurrentUser )
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor(WaitStrategies.waitForBoltReady());
        if(asCurrentUser)
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

    private Path setInitialPasswordWithSecretsFile(GenericContainer container, String password) throws IOException
    {
        Path secretsFolder = temporaryFolderManager.createFolderAndMountAsVolume( container, "/secrets" );
        Files.writeString( secretsFolder.resolve( "passwordfile" ), "neo4j/"+password );
        container.withEnv( NEO4J_AUTH_FILE_ENV, "/secrets/passwordfile" );
        return secretsFolder;
    }

	@Test
	void testNoPassword() throws IOException
    {
		// we test that setting NEO4J_AUTH to "none" lets the database start in TestBasic.java,
        // but that does not test that we can read/write the database
		try(GenericContainer container = createContainer( false ))
		{
			container.withEnv( "NEO4J_AUTH", "none");
            temporaryFolderManager.createFolderAndMountAsVolume( container, "/data" );
            temporaryFolderManager.createFolderAndMountAsVolume( container, "/logs" );

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
            container.waitingFor( WaitStrategies.waitForNeo4jReady( "neo4j" ) );
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            // try with no password, this should fail because the default password should be applied with no NEO4J_AUTH env variable
			Assertions.assertThrows( org.neo4j.driver.exceptions.AuthenticationException.class,
									 () -> db.putInitialDataIntoContainer( "neo4j", "none" ),
									 "Able to access database with no password, even though NEO4J_AUTH=none was not specified!");
			Assertions.assertThrows( org.neo4j.driver.exceptions.ClientException.class,
									 () -> db.putInitialDataIntoContainer( "neo4j", "neo4j" ),
									 "Was not prompted for a new password when using default");
			db.changePassword( "neo4j", "neo4j", "newpassword" );
            db.putInitialDataIntoContainer( "neo4j", "newpassword" );
        }
	}

	@ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource(booleans = {true, false})
    void testCanSetPassword( boolean asCurrentUser ) throws Exception
    {
        // create container and mount /data folder so that data can persist between sessions
        String password = "some_valid_password";
        Path dataMount;

        try(GenericContainer firstContainer = createContainer( asCurrentUser ))
		{
			firstContainer.withEnv( "NEO4J_AUTH", "neo4j/"+password )
                          .waitingFor(WaitStrategies.waitForNeo4jReady(password));
			dataMount = temporaryFolderManager.createFolderAndMountAsVolume(firstContainer, "/data");
			log.info( String.format( "Starting first container as %s user and setting password",
									 asCurrentUser? "current" : "default" ) );
            // create a database with stuff in
            firstContainer.start();
            DatabaseIO db = new DatabaseIO(firstContainer);
            db.putInitialDataIntoContainer( "neo4j", password );
        }

        // with a new container, check the database data.
        try(GenericContainer secondContainer = createContainer( asCurrentUser )
                .waitingFor(WaitStrategies.waitForNeo4jReady(password)))
        {
            temporaryFolderManager.mountHostFolderAsVolume( secondContainer, dataMount, "/data" );
            log.info( "starting new container with same /data mount as same user without setting password" );
            secondContainer.start();
            DatabaseIO db = new DatabaseIO(secondContainer);
            db.verifyInitialDataInContainer( "neo4j", password );
        }
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource(booleans = {true, false})
    void testCanSetPasswordFromSecretsFile( boolean asCurrentUser ) throws Exception
    {
        String password = "some_valid_password";

        try(GenericContainer container = createContainer( asCurrentUser )
                .waitingFor(WaitStrategies.waitForNeo4jReady(password)))
		{
			setInitialPasswordWithSecretsFile( container, password );
			log.info( String.format( "Starting first container as %s user and setting password",
									 asCurrentUser? "current" : "default" ) );
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            db.putInitialDataIntoContainer( "neo4j", password );
            db.verifyInitialDataInContainer( "neo4j", password );
        }
    }

    @Test
    void testSecretsFileTakesPriorityOverEnvAuthentication() throws Exception
    {
        String password = "some_valid_password";
        String wrongPassword = "not_the_password";

        try(GenericContainer container = createContainer(false )
                .waitingFor(WaitStrategies.waitForNeo4jReady(password)))
		{
            container.withEnv( "NEO4J_AUTH", "neo4j/" + wrongPassword );
			setInitialPasswordWithSecretsFile( container, password );
            container.start();
            DatabaseIO db = new DatabaseIO(container);
			Assertions.assertThrows( org.neo4j.driver.exceptions.AuthenticationException.class,
									 () -> db.putInitialDataIntoContainer("neo4j", wrongPassword),
									 "Able to access database with password set in the environment rather than secrets");

            db.putInitialDataIntoContainer( "neo4j", password );
            db.verifyInitialDataInContainer( "neo4j", password );
        }
    }

    @Test
    void testFailsIfSecretsFileSetButMissing()
    {
        try(GenericContainer failContainer = createContainer( false ))
		{
            WaitStrategies.waitUntilContainerFinished( failContainer, Duration.ofSeconds( 30 ) );
            failContainer.withEnv( NEO4J_AUTH_FILE_ENV, "/secrets/doesnotexist.secret" );

            Assertions.assertThrows( ContainerLaunchException.class, failContainer::start,
                                     "Neo4j started even though the password file does not exist" );
            // an error message should be printed to stderr
            String errors = failContainer.getLogs( OutputFrame.OutputType.STDERR);
            Assertions.assertTrue( errors.contains( "The password file '/secrets/doesnotexist.secret' does not exist" ),
                                   "Did not error about missing password file. Actual error was: "+errors);
        }
    }

    @Test
    void testCanSetPasswordWithDebugging() throws Exception
    {
        String password = "some_valid_password";

        try ( GenericContainer container = createContainer( false  ) )
        {
            container.withEnv( "NEO4J_AUTH", "neo4j/" + password )
                     .withEnv( "NEO4J_DEBUG", "yes" )
                     .waitingFor(WaitStrategies.waitForNeo4jReady( password ));
            // create a database with stuff in
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( "neo4j", password );
        }
    }

    @ParameterizedTest(name = "as_current_user_{0}")
    @ValueSource(booleans = {true, false})
    void testSettingNeo4jAuthDoesntOverrideExistingPassword( boolean asCurrentUser ) throws Exception
    {
        String password = "some_valid_password";
        Path dataMount;

		try(GenericContainer firstContainer = createContainer( asCurrentUser ))
		{
			firstContainer.withEnv( "NEO4J_AUTH", "neo4j/"+password )
                          .waitingFor(WaitStrategies.waitForNeo4jReady( password));
			dataMount = temporaryFolderManager.createFolderAndMountAsVolume(firstContainer, "/data");

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
            container.withEnv("NEO4J_AUTH", user+"/"+intialPass+"/true" )
                     .waitingFor(   WaitStrategies.waitForNeo4jReady( intialPass ) );
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

    @ParameterizedTest(name = "use_secrets_file_{0}")
    @ValueSource(booleans = {true, false})
    void testWarnAndFailIfPasswordLessThan8Chars(boolean usePasswordFile) throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        String shortPassword = "123";
        try(GenericContainer failContainer = createContainer( false ))
        {
            if(usePasswordFile)
            {
                setInitialPasswordWithSecretsFile( failContainer, shortPassword );
            }
            else
            {
                failContainer.withEnv( "NEO4J_AUTH", "neo4j/"+shortPassword );
            }
            WaitStrategies.waitUntilContainerFinished( failContainer, Duration.ofSeconds( 30 ) );
            Assertions.assertThrows( ContainerLaunchException.class, failContainer::start,
                                     "Neo4j started even though initial password was too short" );
            String logsOut = failContainer.getLogs();
            Assertions.assertTrue( logsOut.contains( "Invalid value for password" ),
                                   "did not error due to too short password");
            Assertions.assertFalse( logsOut.contains( "Remote interface available at http://localhost:7474/" ),
                                    "Neo4j started even though an invalid password was set");
        }
    }

    @ParameterizedTest(name = "use_secrets_file_{0}")
    @ValueSource(booleans = {true, false})
    void testWarnAndFailIfPasswordLessThanOverride(boolean usePasswordFile) throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        String shortPassword = "123";
        try(GenericContainer failContainer = createContainer( false ))
        {
            if(usePasswordFile)
            {
                setInitialPasswordWithSecretsFile( failContainer, shortPassword );
            }
            else
            {
                failContainer.withEnv( "NEO4J_AUTH", "neo4j/"+shortPassword );
            }

            WaitStrategies.waitUntilContainerFinished( failContainer, Duration.ofSeconds( 30 ) )
                          .withEnv(Configuration.getConfigurationNameMap().get( Setting.MINIMUM_PASSWORD_LENGTH ).envName, "20");
            Assertions.assertThrows( ContainerLaunchException.class, failContainer::start,
                                     "Neo4j started even though initial password was too short" );
            String logsOut = failContainer.getLogs();
            Assertions.assertTrue( logsOut.contains( "Invalid value for password" ),
                                   "did not error due to too short password");
            Assertions.assertFalse( logsOut.contains( "Remote interface available at http://localhost:7474/" ),
                                    "Neo4j started even though an invalid password was set");
        }
    }

    @ParameterizedTest(name = "use_secrets_file_{0}")
    @ValueSource(booleans = {true, false})
    void shouldNotWarnAboutMinimumPasswordLengthIfSettingOverridden_env(boolean usePasswordFile) throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        String shortPassword = "123";
        try(GenericContainer container = createContainer( false ))
        {
            if(usePasswordFile)
            {
                setInitialPasswordWithSecretsFile( container, shortPassword );
            }
            else
            {
                container.withEnv( "NEO4J_AUTH", "neo4j/"+shortPassword );
            }
            container.withEnv(Configuration.getConfigurationNameMap().get( Setting.MINIMUM_PASSWORD_LENGTH ).envName, "2");
            verifyDoesNotWarnAboutMinimumPasswordLengthIfSettingOverridden( container, shortPassword );
        }
    }

    @ParameterizedTest(name = "use_secrets_file_{0}")
    @ValueSource(booleans = {true, false})
    void shouldNotWarnAboutMinimumPasswordLengthIfSettingOverridden_conf(boolean usePasswordFile) throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 5,2,0 ) ),
                                "Minimum password length introduced in 5.2.0");
        String shortPassword = "123";
        try(GenericContainer container = createContainer( false ))
        {
            if(usePasswordFile)
            {
                setInitialPasswordWithSecretsFile( container, shortPassword );
            }
            else
            {
                container.withEnv( "NEO4J_AUTH", "neo4j/"+shortPassword );
            }
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            Files.writeString(confMount.resolve( "neo4j.conf" ),
                              Configuration.getConfigurationNameMap().get( Setting.MINIMUM_PASSWORD_LENGTH ).name+"=2");
            verifyDoesNotWarnAboutMinimumPasswordLengthIfSettingOverridden( container, shortPassword );
        }
    }

    private void verifyDoesNotWarnAboutMinimumPasswordLengthIfSettingOverridden(GenericContainer container, String password) throws Exception
    {
            container.start();
            String logs = container.getLogs();
            Assertions.assertFalse( logs.contains( "Invalid value for password. The minimum password length is 8 characters." ),
                                    "Should not error about minimum password length if overridden.");
            DatabaseIO db = new DatabaseIO( container );
            db.putInitialDataIntoContainer( "neo4j", password );
            db.verifyInitialDataInContainer( "neo4j", password );
    }
}
