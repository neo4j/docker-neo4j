package com.neo4j.docker;

import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.PwGen;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.Assume;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.*;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

public class TestCausalCluster
{
    private static final int DEFAULT_BOLT_PORT = 7687;

    @Test
    void testCausalClusteringBasic() throws Exception
    {
        Assume.assumeTrue( "No causal clustering for community edition",
                TestSettings.EDITION == TestSettings.Edition.ENTERPRISE );

        Path tmpDir = HostFileSystemOperations.createTempFolder( "CC_cluster_" );

        File compose_file =  new File(tmpDir.toString(), "causal-cluster-compose.yml");
        Files.copy(getResource("causal-cluster-compose.yml"), Paths.get(compose_file.getPath()));

        Files.createDirectories( tmpDir.resolve( "core1" ) );
        Files.createDirectories( tmpDir.resolve( "core2" ) );
        Files.createDirectories( tmpDir.resolve( "core3" ) );
        Files.createDirectories( tmpDir.resolve( "readreplica1" ) );

        String content = new String(Files.readAllBytes(Paths.get(compose_file.getPath())));
        String[] contentLines = content.split(System.getProperty("line.separator"));
        String[] editedLines = new String[contentLines.length];
        int i = 0;

        for (String line : contentLines) {
            editedLines[i] = line.replaceAll("%%IMAGE%%", TestSettings.IMAGE_ID);
            editedLines[i] = editedLines[i].replaceAll("%%LOGS_DIR%%", tmpDir.toAbsolutePath().toString());
            editedLines[i] = editedLines[i].replaceAll("%%USERIDGROUPID%%", SetContainerUser.getCurrentlyRunningUserString());
            i++;
        }

        String editedContent = String.join("\n", editedLines);

        DataOutputStream outstream = new DataOutputStream(new FileOutputStream(compose_file,false));
        outstream.write(editedContent.getBytes());
        outstream.close();
        System.out.println("logs: " + compose_file.getName() + " and " + tmpDir.toString());

        DockerComposeContainer clusteringContainer = new DockerComposeContainer(compose_file)
                .withLocalCompose(true)
                .withExposedService("core1", DEFAULT_BOLT_PORT )
                .withExposedService("core1", 7474, Wait.forHttp( "/" ).forPort( 7474 ).forStatusCode( 200 ).withStartupTimeout( Duration.ofSeconds( 300 ) ))
                .withExposedService("readreplica1", DEFAULT_BOLT_PORT);

        clusteringContainer.start();

        String core1Uri = "bolt://" + clusteringContainer.getServiceHost("core1", DEFAULT_BOLT_PORT)
                          + ":" +
                          clusteringContainer.getServicePort("core1", DEFAULT_BOLT_PORT);
        String rrUri = "bolt://" + clusteringContainer.getServiceHost("readreplica1", DEFAULT_BOLT_PORT)
                + ":" +
                clusteringContainer.getServicePort("readreplica1", DEFAULT_BOLT_PORT);

        try ( Driver coreDriver = GraphDatabase.driver( core1Uri, AuthTokens.basic( "neo4j", "neo")))
        {
            Session session = coreDriver.session();
            StatementResult rs = session.run( "CREATE (arne:dog {name:'Arne'})-[:SNIFFS]->(bosse:dog {name:'Bosse'}) RETURN arne.name");
            Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
        }
        catch (Exception e)
        {
            clusteringContainer.stop();
            return;
        }

        try ( Driver rrDriver = GraphDatabase.driver(rrUri, AuthTokens.basic("neo4j", "neo")))
        {
            Session session = rrDriver.session();
            StatementResult rs = session.run( "MATCH (a:dog)-[:SNIFFS]->(b:dog) RETURN a.name");
            Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher MATCH query" );
        }
        catch (Exception e)
        {
            clusteringContainer.stop();
            return;
        }

        clusteringContainer.stop();

    }

    private InputStream getResource(String path) {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(path);
        return resource;
    }
}
