package com.neo4j.docker;

import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.WaitingConsumer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.StatementResult;

public class TestPasswords
{
    private static Logger log = LoggerFactory.getLogger( TestPasswords.class);

    private GenericContainer createContainer( String asCurrentUser )
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) );

        if(asCurrentUser.toLowerCase().equals( "true" ))
        {
            SetContainerUser.currentlyRunningUser( container );
        }

        return container;
    }

    private GenericContainer setNeo4jPassword( GenericContainer container, String password)
    {
        return container.withEnv( "NEO4J_AUTH", password );
    }


    @Test
    void testPasswordCantBeNeo4j() throws Exception
    {
        GenericContainer failContainer = new GenericContainer( TestSettings.IMAGE_ID ).withLogConsumer( new Slf4jLogConsumer( log ) );
        if(TestSettings.EDITION == TestSettings.Edition.ENTERPRISE)
        {
            failContainer.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" );
        }
        setNeo4jPassword( failContainer, "neo4j/neo4j" );
        failContainer.start();

        WaitingConsumer waitingConsumer = new WaitingConsumer();
        failContainer.followOutput(waitingConsumer);

        Assertions.assertDoesNotThrow( () -> waitingConsumer.waitUntil( frame -> frame.getUtf8String()
                                               .contains( "Invalid value for password" ),10, TimeUnit.SECONDS ),
                               "did not error due to invalid password");
    }

    private String getBoltURIFromContainer(GenericContainer container)
    {
        return "bolt://"+container.getContainerIpAddress()+":"+container.getMappedPort( 7687 );
    }

    private void putInitialDataIntoContainer( GenericContainer container, String password)
    {
        String boltUri = getBoltURIFromContainer(container);
        Driver driver = GraphDatabase.driver( boltUri, AuthTokens.basic( "neo4j", password));
        try ( Session session = driver.session())
        {
            StatementResult rs = session.run( "CREATE (arne:dog {name:'Arne'})-[:SNIFFS]->(bosse:dog {name:'Bosse'}) RETURN arne.name");
            Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
        }
        driver.close();
    }

    private void verifyDataIntoContainer( GenericContainer container, String password)
    {
        String boltUri = getBoltURIFromContainer(container);
        Driver driver = GraphDatabase.driver( boltUri, AuthTokens.basic( "neo4j", password));
        try ( Session session = driver.session())
        {
            StatementResult rs = session.run( "MATCH (a:dog)-[:SNIFFS]->(b:dog) RETURN a.name");
            Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
        }
        driver.close();
    }

    private void verifyPasswordIsIncorrect( GenericContainer container, String password)
    {
        String boltUri = getBoltURIFromContainer(container);
        Assertions.assertThrows( org.neo4j.driver.exceptions.AuthenticationException.class,
                                 () ->  GraphDatabase.driver( boltUri, AuthTokens.basic( "neo4j", password)));
    }

    // when junit 5.5.0 is released, @ValueSource should support booleans.
    @ParameterizedTest(name = "as current user={0}")
    @ValueSource(strings = {"true", "false"})
    void testCanSetPassword( String asCurrentUser ) throws Exception
    {
        // create container and mount /data folder so that data can persist between sessions
        String password = "some_valid_password";
        GenericContainer firstContainer = createContainer( asCurrentUser );
        setNeo4jPassword( firstContainer, "neo4j/"+password );
        Path dataMount = HostFileSystemOperations.createHostFolderAndMountAsVolume( firstContainer,
                                                                                    "password-defaultuser-data-",
                                                                                    "/data" );
        // create a database with stuff in
        firstContainer.start();
        putInitialDataIntoContainer( firstContainer, password );
        firstContainer.stop();

        // with a new container, check the database data.
        GenericContainer secondContainer = createContainer( asCurrentUser );
        secondContainer.withFileSystemBind( dataMount.toString(), "/data", BindMode.READ_WRITE );
        secondContainer.start();
        verifyDataIntoContainer( secondContainer, password );
    }

    @ParameterizedTest(name = "as current user={0}")
    @ValueSource(strings = {"true", "false"})
    void testSettingNeo4jAuthDoesntOverrideExistingPassword( String asCurrentUser ) throws Exception
    {
        String password = "some_valid_password";
        GenericContainer firstContainer = createContainer( asCurrentUser );
        setNeo4jPassword( firstContainer, "neo4j/"+password );
        Path dataMount = HostFileSystemOperations.createHostFolderAndMountAsVolume( firstContainer,
                                                                                    "password-envoverride-data-",
                                                                                    "/data" );

        // create a database with stuff in
        firstContainer.start();
        putInitialDataIntoContainer( firstContainer, password );
        firstContainer.stop();

        // with a new container, check the database data.
        GenericContainer secondContainer = createContainer( asCurrentUser );
        setNeo4jPassword( secondContainer, "neo4j/not_the_password" );
        secondContainer.withFileSystemBind( dataMount.toString(), "/data", BindMode.READ_WRITE );
        secondContainer.start();
        verifyDataIntoContainer( secondContainer, password );
        verifyPasswordIsIncorrect( secondContainer,"not_the_password"  );
    }
}
