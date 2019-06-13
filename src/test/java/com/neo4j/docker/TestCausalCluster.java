package com.neo4j.docker;

import com.neo4j.docker.utils.PwGen;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.Assume;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.junit.jupiter.causal_cluster.NeedsCausalCluster;
import org.neo4j.junit.jupiter.causal_cluster.Neo4jUri;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class TestCausalCluster
{
    private static final int DEFAULT_BOLT_PORT = 7687;

    @Test
    void testCausalClusteringBasic() throws Exception
    {
        Assume.assumeTrue( "No causal clustering for community edition",
                TestSettings.EDITION == TestSettings.Edition.ENTERPRISE );

        String cname="core-" + UUID.randomUUID().toString();
        String rname="read-" + UUID.randomUUID().toString();
        String backupName = "backup-" + UUID.randomUUID().toString();

        Path tmpDir = Files.createDirectories(Paths.get("tmp/out/"));

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
            //editedLines[i] = editedLines[i].replaceAll("lan:", "lan: " + network.getId());
            //editedLines[i] = editedLines[i].replaceAll("container_name: core.*", "container_name: " + cname);
            //editedLines[i] = editedLines[i].replaceAll("container_name: read.*", "container_name: " + rname);
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

        WaitStrategy waitForHttp = (new HttpWaitStrategy()).forPort(DEFAULT_BOLT_PORT).forStatusCodeMatching((response) -> {
            return response == 200;
        });

        DockerComposeContainer clusteringContainer =
                new DockerComposeContainer("neo4jcomposetest", new File(compose_file.getPath()))
                        .withLocalCompose(true);
                        //.withExposedService(cname, 7474, waitForHttp);
                        //.withExposedService(rname, 7474, waitForHttp);
                /*.withExposedService("core1", DEFAULT_BOLT_PORT,
                Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));*/

        clusteringContainer.start();
       
    }

    private InputStream getResource(String path) {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(path);
        return resource;
    }
}
