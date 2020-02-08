package com.neo4j.docker;

import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

public class TestSelfSignedCerts
{
    private static Logger log = LoggerFactory.getLogger( TestSelfSignedCerts.class );

    static Stream<Arguments> defaultUserFlag()
    {
        // "asUser={0}, secureFlag={1}"
        // expected behaviour is that if you set --user flag, your data should be read/writable
        // if you don't set --user flag then read/writability should be controlled by the secure file permissions flag
        // the asUser=true, secureflag=false combination is tested separately because the container should fail to start.
        return Stream.of( Arguments.arguments( false ), Arguments.arguments( true ) );
    }

    private Neo4jContainer setupBasicContainer( boolean asCurrentUser )
    {
        log.info( "Running as user {}, {}", asCurrentUser ? "non-root" : "root" );

        Neo4jContainer container = new Neo4jContainer( TestSettings.IMAGE_ID );
        container.withExposedPorts( 7473, 7474, 7687 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .withEnv( "SSL_GENERATE_CERTIFICATES", "yes" )
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" );

        if ( asCurrentUser )
        {
            SetContainerUser.nonRootUser( container );
        }
        return container;
    }

    @ParameterizedTest( name = "asUser={0}" )
    @MethodSource( "defaultUserFlag" )
    public void testSelfSignedCerts( boolean asCurrentUser ) throws IOException
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 0, 0 ) ), "SSL cert checks not valid before 4.0" );

        try ( Neo4jContainer container = setupBasicContainer( asCurrentUser ) )
        {
            container.start();

            container.setWaitStrategy( Wait.forHttps( "/" ).forPort( 7473 ).forStatusCode( 200 ) );
            container.start();
            Assertions.assertTrue( container.isRunning() );

            try ( Driver coreDriver = GraphDatabase.driver( container.getBoltUrl(), AuthTokens.basic( "neo4j", container.getAdminPassword()), Config.builder().withEncryption().withTrustStrategy( Config.TrustStrategy.trustAllCertificates() ).build()))
            {
                Session session = coreDriver.session();
                Result rs = session.run( "MATCH (a) RETURN COUNT(a)");
                Assertions.assertEquals( 0, rs.single().get( 0 ).asInt(), "did not receive expected result from cypher" );
            }
        }
    }

/*
    @ParameterizedTest(name = "asUser={0}, secureFlag={1}")
    @MethodSource( "defaultUserFlagSecurePermissionsFlag" )
    void testCanMountJustDataFolder(boolean asCurrentUser, boolean isSecurityFlagSet) throws IOException
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,1,0 ) ),
                               "User checks not valid before 3.1" );

        try(GenericContainer container = setupBasicContainer( asCurrentUser, isSecurityFlagSet ))
        {
            Path dataMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "data-canmountjustdata-", "/data" );
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
            Path logsMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "logs-canmountjustlogs-", "/logs" );
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
            Path dataMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "data-canmountdataandlogs-", "/data" );
            Path logsMount = HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "logs-canmountdataandlogs-", "/logs" );
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
            HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "data-nopermissioninsecuremode-", "/data" );

            // currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
            // container.setWaitStrategy( Wait.forLogMessage( "[fF]older /data is not accessible for user", 1 ).withStartupTimeout( Duration.ofSeconds( 20 ) ) );
            container.setWaitStrategy( Wait.forListeningPort().withStartupTimeout( Duration.ofSeconds( 20 ) ) );
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
            HostFileSystemOperations.createTempFolderAndMountAsVolume( container, "logs-nopermissioninsecuremode-", "/logs" );

            // currently Neo4j will try to start and fail. It should be fixed to throw an error and not try starting
            // container.setWaitStrategy( Wait.forLogMessage( "[fF]older /logs is not accessible for user", 1 ).withStartupTimeout( Duration.ofSeconds( 20 ) ) );
            container.setWaitStrategy( Wait.forListeningPort().withStartupTimeout( Duration.ofSeconds( 20 ) ) );
            Assertions.assertThrows( org.testcontainers.containers.ContainerLaunchException.class,
                                     () -> container.start(),
                                     "Neo4j should not start in secure mode if logs folder is unwritable" );
        }
    }
    */
}
