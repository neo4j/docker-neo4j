package com.neo4j.docker;

import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.StatementResult;

@Ignore
public class TestHACluster
{
    private Random rng = new Random(  );
    private static Logger log = LoggerFactory.getLogger( TestHACluster.class);
    private String dbPassword = "neo";

    private void putInitialDataIntoContainer( String boltUri )
    {
        Driver driver = GraphDatabase.driver( boltUri, AuthTokens.basic( "neo4j", dbPassword));
        try ( Session session = driver.session())
        {
            StatementResult rs = session.run( "CREATE (arne:dog {name:'Arne'})-[:SNIFFS]->(bosse:dog {name:'Bosse'}) RETURN arne.name");
            Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
        }
        driver.close();
    }

    private void verifyDataInContainer( String boltUri )
    {
        Driver driver = GraphDatabase.driver( boltUri, AuthTokens.basic( "neo4j", dbPassword));
        try ( Session session = driver.session())
        {
            StatementResult rs = session.run( "MATCH (a:dog)-[:SNIFFS]->(b:dog) RETURN a.name");
            Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
        }
        driver.close();
    }

    private String getBoltUriForService(DockerComposeContainer container, String service)
    {
        return "bolt://" + container.getServiceHost( service, 7687 ) +
               ":" + container.getServicePort( service, 7687 );
    }

    @Ignore
    @Test
    void testHAStartsOK() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "HA Tests don't apply to community version");
        Assumptions.assumeFalse( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,5,0 ) ),
                                 "HA Tests don't apply to versions 3.5 and later");
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isNewerThan( new Neo4jVersion( 3,0,0 )),
                                "HA Tests don't apply before 3.0");

        Path logDir = TestSettings.TEST_TMP_FOLDER.resolve( String.format( "HA_cluster_%04d", rng.nextInt(10000 ) ));
        log.info( "writing HA test logs into "+logDir.toString() );

        Path composeTemplate = Paths.get( "src", "test", "resources", "ha-cluster-compose.yml" );
        Path composeFile = logDir.resolve( "ha-cluster-compose.yml" );

        // read the HA compose file template and replace placeholders
        String composeContent = new String( Files.readAllBytes( composeTemplate ) );
        composeContent = composeContent
                .replaceAll( "%%USERIDGROUPID%%", SetContainerUser.getCurrentlyRunningUserString() )
                .replaceAll( "%%IMAGE%%", TestSettings.IMAGE_ID )
                .replaceAll( "%%LOGS_DIR%%", logDir.toAbsolutePath().toString() );

        // create log folders
        Files.createDirectories( logDir.resolve( "master" ) );
        Files.createDirectories( logDir.resolve( "slave1" ) );
        Files.createDirectories( logDir.resolve( "slave2" ) );

        // save new compose file
        Files.write( composeFile, composeContent.getBytes() );

        // now actually start the cluster
        WaitStrategy waiter = Wait.forListeningPort().withStartupTimeout( Duration.ofSeconds( 90 ) );
        DockerComposeContainer clusteringContainer = new DockerComposeContainer( composeFile.toFile() )
                .withLocalCompose(true)
                .withExposedService( "master", 7687 )
                .withExposedService( "slave1", 7687 )
                .waitingFor( "master", waiter)
                .waitingFor( "slave1", waiter);
        clusteringContainer.start();

        // write some data
        log.info( "Cluster started, writing data to master" );
        putInitialDataIntoContainer( getBoltUriForService( clusteringContainer, "master" ) );

        // read some data
        log.info( "Reading data from slave" );
        verifyDataInContainer( getBoltUriForService( clusteringContainer, "slave1" ) );

        // teardown resources
        clusteringContainer.stop();
    }
}
