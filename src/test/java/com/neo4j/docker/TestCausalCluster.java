package com.neo4j.docker;

import com.neo4j.docker.utils.PwGen;
import com.neo4j.docker.utils.SetContainerUser;
import com.neo4j.docker.utils.TestSettings;
import org.junit.Assume;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.junit.jupiter.causal_cluster.NeedsCausalCluster;
import org.neo4j.junit.jupiter.causal_cluster.Neo4jUri;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.Network;

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

//@NeedsCausalCluster(neo4jVersion = TestSettings.NEO4J_VERSION.toString(), customImageName = TestSettings.IMAGE_ID)
public class TestCausalCluster
{
    static final String USERNAME = "neo4j";
    static final String PASSWORD = "cc";

    @Neo4jUri // (2)
    private static URI neo4jUri;

    @Test
    void testCausalClusteringBasic() throws Exception
    {

        AuthToken authToken = AuthTokens.basic(USERNAME, PASSWORD);
        try (
                Driver driver = GraphDatabase.driver(neo4jUri, authToken);
                Session session = driver.session();
        ) {
            List<Record> list = session.run("CREATE (n) RETURN n").list();
            for (Record record: list) {
                for (String key:record.keys()) {
                    System.out.println("Key: " + key + " Value: " + record.get(key));
                }
            }
        }

        /*Assume.assumeTrue( "No causal clustering for community edition",
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

        Network network = Network.newNetwork();

        String content = new String(Files.readAllBytes(Paths.get(compose_file.getPath())));
        String[] contentLines = content.split(System.getProperty("line.separator"));
        String[] editedLines = new String[contentLines.length];
        int i = 0;

        for (String line : contentLines) {
            editedLines[i] = line.replaceAll("image: .*", "image: " + TestSettings.IMAGE_ID);
            //editedLines[i] = editedLines[i].replaceAll("lan:", "lan: " + network.getId());
            editedLines[i] = editedLines[i].replaceAll("container_name: core.*", "container_name: " + cname);
            editedLines[i] = editedLines[i].replaceAll("container_name: read.*", "container_name: " + rname);
            editedLines[i] = editedLines[i].replaceAll("LOGS_DIR", "./" + logsDir);
            editedLines[i] = editedLines[i].replaceAll("USER_INFO", SetContainerUser.getCurrentlyRunningUserString());
            i++;
        }

        String editedContent = String.join("\n", editedLines);

        DataOutputStream outstream= new DataOutputStream(new FileOutputStream(compose_file,false));
        outstream.write(editedContent.getBytes());
        outstream.close();

        System.out.println("logs: " + compose_file.getName() + ".log and " + logsDir);


        DockerComposeContainer clusteringContainer =
                new DockerComposeContainer("neo4jcomposetest", new File(compose_file.getPath()))
                        .withLocalCompose(true)
                /*.withExposedService("core1", DEFAULT_BOLT_PORT,
                Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

        clusteringContainer.start();*/





        //docker_compose_up "${image}" "${compose_file}" "${cname}" "${rname}" "$(pwd)/${logs_dir}"
        /*
        docker_compose_up() {
          local l_image="$1" l_composefile="$2" l_cname="$3" l_rname="$4"; logs_d="$5"; shift; shift; shift; shift;

          # Create the log directories. If we let docker create them then they will be owned by docker not our current user
          # TODO: use some jq/yq magic to read out the volumes from the docker compose file
          DONE! mkdir --parents "${logs_d}/core1"
          DONE! mkdir --parents "${logs_d}/core2"
          DONE! mkdir --parents "${logs_d}/core3"
          DONE! mkdir --parents "${logs_d}/readreplica1"

          DONE! sed --in-place -e "s|image: .*|image: ${l_image}|g" "${l_composefile}"
          DONE! sed --in-place -e "s|container_name: core.*|container_name: ${l_cname}|g" "${l_composefile}"
          DONE! sed --in-place -e "s|container_name: read.*|container_name: ${l_rname}|g" "${l_composefile}"
          DONE! sed --in-place -e "s|LOGS_DIR|${logs_d}|g" "${l_composefile}"
          DONE! sed --in-place -e "s|USER_INFO|$(id -u):$(id -g)|g" "${l_composefile}"

          DONE! echo "logs: ${l_composefile}.log and ${logs_dir}"

          docker-compose --file "${l_composefile}" --project-name neo4jcomposetest up -d
          trap "docker_compose_cleanup ${l_composefile}" EXIT
        }

         */

    }

    private InputStream getResource(String path) {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(path);
        return resource;
    }
}
