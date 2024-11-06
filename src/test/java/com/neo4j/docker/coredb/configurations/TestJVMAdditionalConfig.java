package com.neo4j.docker.coredb.configurations;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.nio.file.Files;
import java.nio.file.Path;

public class TestJVMAdditionalConfig
{
    private final Logger log = LoggerFactory.getLogger( TestJVMAdditionalConfig.class );
    private static final String PASSWORD = "SuperSecretPassword";
    private static final String AUTH = "neo4j/"+PASSWORD ;
    private static Path confFolder;
    private static final Configuration JVM_ADDITIONAL_CONFIG = Configuration.getConfigurationNameMap().get( Setting.JVM_ADDITIONAL );
    private static final String DEFAULT_JVM_CONF = "-XX:+UseG1GC";

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    @BeforeAll
    static void getVersionSpecificConfigurationSettings()
    {
        confFolder = Configuration.getConfigurationResourcesFolder();
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_440 ),
                                "JVM Additional tests not applicable before 4.4.0");
    }

    private GenericContainer createContainer()
    {
        return new GenericContainer(TestSettings.IMAGE_ID)
                .withEnv("NEO4J_AUTH", AUTH)
                .withEnv("NEO4J_DEBUG", AUTH)
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withExposedPorts(7474, 7687)
                .withLogConsumer(new Slf4jLogConsumer( log))
                .waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD));
    }

    @Test
    void testJvmAdditionalNotOverridden_noEnv() throws Exception
    {
        String expectedJvmAdditional = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005";
        testJvmAdditionalNotOverridden(expectedJvmAdditional, "" );
    }

    @Test
    void testJvmAdditionalNotOverridden_withEnv() throws Exception
    {
        String jvmAdditionalFromEnv = "-XX:+HeapDumpOnOutOfMemoryError";
        String expectedJvmAdditional = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005\n"+jvmAdditionalFromEnv;
        testJvmAdditionalNotOverridden(expectedJvmAdditional, jvmAdditionalFromEnv );
    }

    void testJvmAdditionalNotOverridden( String expectedJvmAdditional, String jvmAdditionalEnv ) throws Exception
    {
        try( GenericContainer container = createContainer())
        {
            //Mount /conf
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            SetContainerUser.nonRootUser( container );
            container.withEnv( JVM_ADDITIONAL_CONFIG.envName, jvmAdditionalEnv );
            //Create JvmAdditionalNotOverridden.conf file
            Path confFile = confFolder.resolve( "JvmAdditionalNotOverridden.conf" );
            Files.copy( confFile, confMount.resolve( "neo4j.conf" ) );
            //Start the container
            container.start();
            // verify setting correctly loaded into neo4j
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.verifyConfigurationSetting( "neo4j", PASSWORD, JVM_ADDITIONAL_CONFIG, expectedJvmAdditional);
        }
    }

    @Test
    void testJVMAdditionalDefaultsNotOverwrittenByEnv() throws Exception
    {
        String expectedJvmAdditional = "-XX:+HeapDumpOnOutOfMemoryError";
        try( GenericContainer container = createContainer())
        {
            container.withEnv( JVM_ADDITIONAL_CONFIG.envName, expectedJvmAdditional );
            verifyJvmAdditional( container, expectedJvmAdditional, DEFAULT_JVM_CONF );
        }
    }

    @Test
    void testSpecialCharInJvmAdditional_space_conf() throws Exception
    {
        testJvmAdditionalSpecialCharacters_conf("space", "-XX:OnOutOfMemoryError=\"/usr/bin/echo oh no oom\"");
    }

    @Test
    void testSpecialCharInJvmAdditional_space_env() throws Exception
    {
        testJvmAdditionalSpecialCharacters_env( "-XX:OnOutOfMemoryError=\"/usr/bin/echo oh no oom\"");
    }

    @Test
    void testSpecialCharInJvmAdditional_dollar_conf() throws Exception
    {
        testJvmAdditionalSpecialCharacters_conf("dollar",
                                                "-Dnot.a.real.parameter=\"beepbeep$boop1boop2\"" );
    }

    @Test
    void testSpecialCharInJvmAdditional_dollar_env() throws Exception {
        testJvmAdditionalSpecialCharacters_env( "-Dnot.a.real.parameter=\"bleepblorp$bleep1blorp4\"");
    }

    void testJvmAdditionalSpecialCharacters_conf(String charName, String expectedJvmAdditional) throws Exception
    {
        try(GenericContainer container = createContainer())
        {
            //Mount /conf
            Path confMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
            //copy test conf file
            String confContent = JVM_ADDITIONAL_CONFIG.name + "=" + expectedJvmAdditional;
            Files.write( confMount.resolve( "neo4j.conf" ), confContent.getBytes() );
            //Start the container
            verifyJvmAdditional( container, expectedJvmAdditional );
        }
    }

    void testJvmAdditionalSpecialCharacters_env( String expectedJvmAdditional ) throws Exception
    {
        try(GenericContainer container = createContainer())
        {
            container.withEnv( JVM_ADDITIONAL_CONFIG.envName, expectedJvmAdditional);
            verifyJvmAdditional( container, expectedJvmAdditional, DEFAULT_JVM_CONF );
        }
    }

    void verifyJvmAdditional( GenericContainer container, String... expectedValues )
    {
        SetContainerUser.nonRootUser( container );
        //Start the container
        container.start();
        // verify setting correctly loaded into neo4j
        DatabaseIO dbio = new DatabaseIO( container );
        String actualConfValue = dbio.getConfigurationSettingAsString( "neo4j", PASSWORD, JVM_ADDITIONAL_CONFIG );
        
        for(String expectedJvmAdditional : expectedValues)
        {
            Assertions.assertTrue( actualConfValue.contains( expectedJvmAdditional ) );
        }
    }
}
