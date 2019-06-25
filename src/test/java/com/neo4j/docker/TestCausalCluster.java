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

        String logs_id = PwGen.getPass(8);
        File compose_file =  new File(tmpDir.toString(), logs_id + ".yml");
        Files.copy(getResource("causal-cluster-compose.yml"), Paths.get(compose_file.getPath()));

        String logsDir = tmpDir + "/" + logs_id;

        File core1 = new File(logsDir + "/core1");
        core1.mkdirs();
        File core2 = new File(logsDir + "/core2");
        core2.mkdirs();
        File core3 = new File(logsDir + "/core3");
        core3.mkdirs();
        File rr1 = new File(logsDir + "/readreplica1");
        rr1.mkdirs();

        String content = new String(Files.readAllBytes(Paths.get(compose_file.getPath())));
        String[] contentLines = content.split(System.getProperty("line.separator"));
        String[] editedLines = new String[contentLines.length];
        int i = 0;

        for (String line : contentLines) {
            editedLines[i] = line.replaceAll("image: .*", "image: " + TestSettings.IMAGE_ID);
            editedLines[i] = editedLines[i].replaceAll("container_name: core.*", "");
            editedLines[i] = editedLines[i].replaceAll("container_name: read.*", "");
            editedLines[i] = editedLines[i].replaceAll("LOGS_DIR", "./" + logsDir);
            editedLines[i] = editedLines[i].replaceAll("USER_INFO", SetContainerUser.getCurrentlyRunningUserString());
            i++;
        }

        String editedContent = String.join("\n", editedLines);

        DataOutputStream outstream= new DataOutputStream(new FileOutputStream(compose_file,false));
        outstream.write(editedContent.getBytes());
        outstream.close();

        System.out.println("logs: " + compose_file.getName() + ".log and " + logsDir);

        WaitStrategy waitForport = Wait.forListeningPort()
                .withStartupTimeout(Duration.ofSeconds(90));

        DockerComposeContainer clusteringContainer =
                new DockerComposeContainer(compose_file)
                        .withLocalCompose(true)
                        .withExposedService("core1", DEFAULT_BOLT_PORT)
                        .withExposedService("readreplica1", DEFAULT_BOLT_PORT)
                        .waitingFor("core1", waitForport);

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
