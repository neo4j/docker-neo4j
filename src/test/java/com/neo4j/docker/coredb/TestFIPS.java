package com.neo4j.docker.coredb;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.neo4j.docker.utils.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;


public class TestFIPS
{
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
    public static final String FIPS_FLAG = "NEO4J_OPENSSL_FIPS_ENABLE";
    public static final String PASSWORD = "MYsuperSECRETpassword123";
    public static final String OPENSSL_VERSION = "3.0.9";
    private static final Logger log = LoggerFactory.getLogger(TestFIPS.class);
    private static Path certificates;

    /*TODO
       generate certificates
       test happy path with ssl certs
       test errors if fips and debian
       test error if offline
    *  */
    @BeforeAll
    static void skipInvalidTestScenariosGenerateKeys()
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion(new Neo4jVersion(5, 17, 0)),
                "FIPS compliance was introduced after 5.19.0.");
        Assumptions.assumeFalse(TestSettings.BASE_OS == TestSettings.BaseOS.UBI8,
                "UBI8 images are deprecated and are not FIPS compliant");
    }

    private synchronized Path generateSSLKeys() throws Exception
    {
        if(certificates == null) {
            // using nginx image because it's easy to verify startup and it has openssl already installed
            try (GenericContainer container = new GenericContainer(DockerImageName.parse("nginx:latest"))) {
                certificates = temporaryFolderManager.createFolderAndMountAsVolume(container, "/certificates");
                container.withExposedPorts(80)
                        .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofSeconds(20)));
                container.start();
                container.execInContainer("openssl", "req", "-x509", "-sha1", "-nodes",
                        "-newkey", "rsa:2048", "-keyout", "/certificates/private.key1",
                        "-out", "/certificates/selfsigned.crt",
                        "-subj", "/C=SE/O=Example/OU=ExampleCluster/CN=Server0",
                        "-days", "1");
                container.execInContainer("openssl", "pkcs8", "-topk8", "-nocrypt",
                        "-in", "/certificates/private.key1", "-out", "/certificates/private.key");
                container.execInContainer("rm", "/certificates/private.key1");
                container.execInContainer("chown", "-R", "7474:7474", "/certificates");
            }
        }
        return certificates;
    }

    GenericContainer createFIPSContainer()
    {
        GenericContainer<?> container = new GenericContainer<>(TestSettings.IMAGE_ID)
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withEnv("NEO4J_AUTH", "neo4j/"+PASSWORD)
                .withEnv("NEO4J_DEBUG", "yes")
                .withEnv(FIPS_FLAG, "true")
                .withExposedPorts(7474, 7687)
                .withLogConsumer(new Slf4jLogConsumer(log))
                .waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD, Duration.ofSeconds(60)));
        return container;
    }

    private void configureContainerForSSL(GenericContainer container) throws Exception
    {
        Path certs = generateSSLKeys();
        temporaryFolderManager.mountHostFolderAsVolume(container, certs, "/certificates");
        Path conf = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
        FileWriter confFile = new FileWriter(conf.resolve("neo4j.conf").toFile());
        confFile.write("server.bolt.tls_level=OPTIONAL\n");
        confFile.write("dbms.ssl.policy.bolt.enabled=true\n");
        confFile.write("dbms.ssl.policy.bolt.base_directory=/certificates\n");
        confFile.write("dbms.ssl.policy.bolt.private_key=private.key\n");
        confFile.write("dbms.ssl.policy.bolt.public_certificate=selfsigned.crt\n");
        confFile.write("dbms.netty.ssl.provider=OPENSSL\n");
        confFile.flush();
        confFile.close();
    }

    @Test
    void testOpensslIsInstalledWithFIPS() throws Exception
    {
        try(GenericContainer container = createFIPSContainer())
        {
            container.start();
            Container.ExecResult versionOut = container.execInContainer("openssl", "version", "-a");
            Container.ExecResult providersOut = container.execInContainer("openssl", "list", "-providers");

            // verify openssl version
            Assertions.assertEquals(0, versionOut.getExitCode(), "OpenSSL command failed. Full output:\n"+versionOut);
            List<String> openssl = Arrays.stream(versionOut.getStdout().split("\n")).toList();
            Assertions.assertTrue(openssl.get(0).contains("OpenSSL " + OPENSSL_VERSION),
                    "OpenSSL "+ OPENSSL_VERSION +" is not installed.\n"+versionOut);
            Assertions.assertTrue(openssl.stream().anyMatch(s -> s.matches("OPENSSLDIR:.*/usr/local/ssl\"?\\s*")),
            "Openssl is not using the expected ssl config directory:\n"+versionOut);
            Assertions.assertTrue(openssl.stream().anyMatch(s -> s.matches("MODULESDIR:.*/usr/local/lib64/ossl-modules\"?\\s*")),
            "Openssl is not using the expected modules directory:\n"+versionOut);

            // verify FIPS provider is set
            Assertions.assertEquals(0, providersOut.getExitCode(), "OpenSSL command failed. Full output:\n"+providersOut);
            List<String> providers = Arrays.stream(providersOut.getStdout().split("\n")).map(String::trim).toList();
            Assertions.assertTrue(providers.stream().anyMatch(s -> s.matches("fips")),
                    "FIPS was not listed as an OpenSSL provider:\n"+providersOut);
            int fipsIdx = providers.indexOf("fips");
            for(int i=fipsIdx; i < fipsIdx+3; i++)
            {
                String line = providers.get(i);
                if (line.startsWith("name")) {
                    Assertions.assertTrue(line.matches("name:\\s+OpenSSL FIPS Provider"),
                            "FIPS provider has an unexpected name\n" + providersOut);
                } else if (line.startsWith("version")) {
                    Assertions.assertTrue(line.matches("version:\\s+"+OPENSSL_VERSION.replace(".", "\\.")),
                            "FIPS version is not "+OPENSSL_VERSION+":\n" + providersOut);
                } else if (line.startsWith("status")) {
                    Assertions.assertTrue(line.matches("status:\\s+active"),
                            "FIPS is not the active provider:\n" + providersOut);
                }
            }
        }
    }

    /*We need to test that the FIPS enabled OpenSSL version is actually used by neo4j when we use bolt.
    To test this, we run start neo4j with strace and then analyse the trace afterwards.
     * */
    @Test
    void testFIPSOpensslLibrariesUsed() throws Exception
    {
        Path ioPath = temporaryFolderManager.createFolder("strace");
        File startScript = ioPath.resolve("start.sh").toFile();
        File straceLog = ioPath.resolve("out.log").toFile();

        // create wrapper for entrypoint that installs strace then starts entrypoint with a trace
        FileWriter wrapperScript = new FileWriter(startScript);
        wrapperScript.write("#!/bin/bash\n");
        switch (TestSettings.BASE_OS)
        {
            case BULLSEYE -> {
                wrapperScript.write("apt update\n");
                wrapperScript.write("apt install -y strace\n");
            }
            case UBI9 -> wrapperScript.write("microdnf install -y strace\n");
        }
        wrapperScript.write("strace -fyz --trace=openat -o /strace/"+straceLog.getName() + " /startup/docker-entrypoint.sh neo4j");
        wrapperScript.flush();
        wrapperScript.close();
        startScript.setExecutable(true);

        try(GenericContainer container = createFIPSContainer())
        {
            temporaryFolderManager.mountHostFolderAsVolume(container, ioPath, "/strace");
            configureContainerForSSL(container);
            // override the actual entrypoint with our strace wrapper
            container.withCreateContainerCmdModifier((Consumer<CreateContainerCmd>) cmd ->
                    cmd.withEntrypoint("tini", "-g", "--", "/strace/"+startScript.getName()));
            container.start();
            DatabaseIO dbio = new DatabaseIO(container);
            dbio.putInitialDataIntoContainer("neo4j", PASSWORD);
        }
        Assertions.assertTrue(straceLog.exists(), "Test did not create an strace log");
        List<String> strace = Files.readAllLines(straceLog.toPath());
        List<String> libsslReads = strace.stream().filter(t -> t.contains("libssl")).toList();
    }
}
