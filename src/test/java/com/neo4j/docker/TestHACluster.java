package com.neo4j.docker;

import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.Ignore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;

@Ignore
public class TestHACluster
{
    private Random rng = new Random(  );
    private static Logger log = LoggerFactory.getLogger( TestHACluster.class);

    @Test
    void testHAStartsOK() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                "HA Tests don't apply to community version");
        Assumptions.assumeFalse( TestSettings.NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 3,5,0 ) ),
                                 "HA Tests don't apply to versions 3.5 and later");
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isNewerThan( new Neo4jVersion( 3,0,0 )),
                                "HA Tests don't apply before 3.0");

        String testID = String.format( "%04d", rng.nextInt(10000 ) );
        Path logDir = TestSettings.TEST_TMP_FOLDER.resolve( "HA_cluster_" + testID );
        log.info( "writing HA test logs into "+logDir.toString() );

        Path composeTemplate = Paths.get( "src", "test", "resources", "ha-cluster-compose.yml" );
        Path composeFile = logDir.resolve( "ha-cluster-compose.yml" );
        String masterContainer = String.format( "master_" + testID );
        String slaveContainer = String.format( "slave_" + testID );

        // read the HA compose file template and replace placeholders
        String composeContent = new String( Files.readAllBytes( composeTemplate ) );
        composeContent = composeContent
                .replaceAll( "%%USERIDGROUPID%%", SetContainerUser.getCurrentlyRunningUserString() )
                .replaceAll( "%%IMAGE%%", TestSettings.IMAGE_ID )
                .replaceAll( "%%MASTER_CONTAINER%%", masterContainer )
                .replaceAll( "%%SLAVE_CONTAINER%%", slaveContainer )
                .replaceAll( "%%LOGS_DIR%%", logDir.toAbsolutePath().toString() );

        // create log folders
        Files.createDirectories( logDir.resolve( "master" ) );
        Files.createDirectories( logDir.resolve( "slave1" ) );
        Files.createDirectories( logDir.resolve( "slave2" ) );

        // save new compose file
        Files.write( composeFile, composeContent.getBytes() );

        // now actually start the cluster
        DockerComposeContainer clusteringContainer = new DockerComposeContainer( "HA_cluster_"+testID, composeFile.toFile() )
                .withLocalCompose(true)
                .withExposedService( "master", 7687 )
                .waitingFor( "master", Wait.forListeningPort().withStartupTimeout( Duration.ofSeconds( 90 ) ));
        clusteringContainer.start();
        // write some data

        // read some data
    }
}
