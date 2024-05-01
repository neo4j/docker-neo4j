package com.neo4j.docker.coredb;

import com.neo4j.docker.utils.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.driver.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.FileWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;


public class TestFIPS
{
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
    public static final String FIPS_FLAG = "NEO4J_OPENSSL_FIPS_ENABLE";
    public static final String PASSWORD = "MYsuperSECRETpassword123";
    public static final String SSL_KEY_PASSPHRASE = "abcdef1234567890";
    public static final String OPENSSL_VERSION = "3.0.9";
    public static final String OPENSSL_INSTALL_DIR = "/usr/local/openssl";
    private static final Logger log = LoggerFactory.getLogger(TestFIPS.class);
    private static Path certificates;

    @BeforeAll
    static void skipInvalidTestScenarios()
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion(new Neo4jVersion(5, 19, 0)),
                "FIPS compliance was introduced after 5.19.0.");
        Assumptions.assumeTrue(TestSettings.BASE_OS == TestSettings.BaseOS.UBI9,
                "UBI9 images are the only FIPS compliant ones at the moment.");
    }

    private synchronized Path generateSSLKeys() throws Exception
    {
        if(certificates == null) {
            // using nginx image because it's easy to verify startup and it has openssl already installed
            try (GenericContainer container = new GenericContainer(DockerImageName.parse("nginx:latest")))
            {
                certificates = temporaryFolderManager.createFolderAndMountAsVolume(container, "/certificates");
                container.withExposedPorts(80)
                        .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofSeconds(20)));
                container.start();
                container.execInContainer("openssl", "req", "-x509", "-sha1", "-nodes",
                        "-newkey", "rsa:2048", "-keyout", "/certificates/private.key1",
                        "-out", "/certificates/selfsigned.crt",
                        "-subj", "/C=SE/O=Example/OU=ExampleCluster/CN=localhost",
                        "-days", "1");
                container.execInContainer("openssl", "pkcs8", "-topk8",
                        "-in", "/certificates/private.key1", "-out", "/certificates/private.key",
                        "-passout", "pass:"+SSL_KEY_PASSPHRASE);
                container.execInContainer("rm", "/certificates/private.key1");
                container.execInContainer("chown", "-R", "7474:7474", "/certificates");
                // copy the certificate and make it readable by the user running the unit tests
                // otherwise we can't validate commands on the client side
                container.execInContainer("cp", "/certificates/selfsigned.crt", "/certificates/localselfsigned.crt");
                container.execInContainer("chown", SetContainerUser.getNonRootUserString(), "/certificates/localselfsigned.crt");
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
        confFile.write("dbms.netty.ssl.provider=OPENSSL\n");
        confFile.write("server.https.enabled=false\n");
        confFile.write("server.bolt.tls_level=REQUIRED\n");
        confFile.write("dbms.ssl.policy.bolt.enabled=true\n");
        confFile.write("dbms.ssl.policy.bolt.client_auth=NONE\n");
        confFile.write("dbms.ssl.policy.bolt.trust_all=false\n");
        confFile.write("dbms.ssl.policy.bolt.tls_versions=TLSv1.3\n");
        confFile.write("dbms.ssl.policy.bolt.private_key_password="+SSL_KEY_PASSPHRASE+"\n");
        confFile.write("dbms.ssl.policy.bolt.base_directory=/certificates\n");
        confFile.write("dbms.ssl.policy.bolt.private_key=private.key\n");
        confFile.write("dbms.ssl.policy.bolt.public_certificate=selfsigned.crt\n");
        confFile.flush();
        confFile.close();
    }

    @Test
    void testOpenSSLIsInstalledWithFIPS() throws Exception
    {
        Container.ExecResult versionOut, providersOut;
        try(GenericContainer container = createFIPSContainer())
        {
            container.start();
            versionOut = container.execInContainer("openssl", "version", "-a");
            providersOut = container.execInContainer("openssl", "list", "-providers");
        }

        // verify openssl version
        Assertions.assertEquals(0, versionOut.getExitCode(), "OpenSSL command failed. Full output:\n"+versionOut);
        List<String> openssl = Arrays.stream(versionOut.getStdout().split("\n")).toList();
        Assertions.assertTrue(openssl.get(0).contains("OpenSSL " + OPENSSL_VERSION),
                "OpenSSL "+ OPENSSL_VERSION +" is not installed.\n"+versionOut);
        Assertions.assertTrue(openssl.stream().anyMatch(s -> s.matches("OPENSSLDIR:\s*\""+OPENSSL_INSTALL_DIR+"\"?\\s*")),
                "OpenSSL is not using the expected ssl config directory:\n"+versionOut);
        Assertions.assertTrue(openssl.stream().anyMatch(s -> s.matches("MODULESDIR:\s*\""+OPENSSL_INSTALL_DIR+"/lib64/ossl-modules\"?\\s*")),
                "OpenSSL is not using the expected modules directory:\n"+versionOut);

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

    @Test
    void testFIPSOpenSSLLibrariesUsed() throws Exception
    {
        String filesInUse;
        try(GenericContainer container = createFIPSContainer())
        {
            configureContainerForSSL(container);
            container.start();
            String neo4jPID = container.execInContainer("cat", "/var/lib/neo4j/run/neo4j.pid").getStdout();
            filesInUse = container.execInContainer("su-exec", "neo4j", "cat", "/proc/"+neo4jPID+"/maps").getStdout();
        }
        List<String> procMap = Arrays.stream(filesInUse.split("\n")).toList();
        verifyProcessAccessesFile(procMap, "libssl.so", OPENSSL_INSTALL_DIR);
        verifyProcessAccessesFile(procMap, "libcrypto.so", OPENSSL_INSTALL_DIR);
        verifyProcessAccessesFile(procMap, "fips.so", OPENSSL_INSTALL_DIR);
    }

    private void verifyProcessAccessesFile(List<String> procMap, String filename, String expectedPath)
    {
        List<String> fileReads = procMap.stream()
                .filter(t -> t.contains(filename))
                .toList();
        Assertions.assertFalse(fileReads.isEmpty(), "Neo4j did not use "+filename+" at all");
        String regex = String.format(".*%s/.*%s.*", expectedPath, filename);
        for(String line : fileReads)
        {
            Assertions.assertTrue(line.matches(regex),
                    "Did not use "+filename+" under path "+expectedPath+". Actual file: "+line);
        }
    }

    @Test
    void testEndToEndSSLEncryption() throws Exception
    {
        try(GenericContainer container = createFIPSContainer())
        {
            configureContainerForSSL(container);
            container.start();
            DatabaseIO dbio = new DatabaseIO(container,
                    Config.builder()
                            .withEncryption()
                            .withTrustStrategy(Config.TrustStrategy
                                    .trustCustomCertificateSignedBy(
                                            certificates.resolve("localselfsigned.crt").toFile()
                                    ))
                            .build());
            dbio.verifyConnectivity("neo4j", PASSWORD);
            dbio.putInitialDataIntoContainer("neo4j", PASSWORD);
            dbio.verifyInitialDataInContainer("neo4j", PASSWORD);
            container.execInContainer("microdnf", "install", "-y", "nmap");
            String nmapOut = container.execInContainer("nmap", "--script", "ssl-enum-ciphers", "-p", "7687", "localhost").getStdout();
            log.info("nmap scan returned:\n"+nmapOut);
            List<String> nmap = Arrays.stream(nmapOut.split("\n"))
                    .filter(line -> line.contains("least strength: A"))
                    .toList();
            Assertions.assertEquals(1, nmap.size(), "NMap scan shows port 7687 is not secure.");
        }
    }

    // This test is only valid until we can get the netty-tcnative openssl3 compatibility fixes shipped in neo4j.
    @Test
    void testErrorsIfNoInternet()
    {
        List<String> errorLogs;
        try(GenericContainer container = createFIPSContainer())
        {
            container.withNetworkMode("none");
            WaitStrategies.waitUntilContainerFinished(container, Duration.ofSeconds(20));
            Assertions.assertThrows( ContainerLaunchException.class, container::start,
                    "Container should have failed on start up" );
            errorLogs = Arrays.stream(container.getLogs(OutputFrame.OutputType.STDERR).split("\n")).toList();
        }
        Assertions.assertFalse(errorLogs.isEmpty(), "There should have been errors reported to stderr");
        Assertions.assertTrue(errorLogs.stream().anyMatch(line -> line.contains("Could not download files:")),
                "Did not give a nice error message when unable to download files");
        Assertions.assertTrue(errorLogs.stream().anyMatch(line -> line.contains("tcnativerebuilds/netty-tcnative-classes-2.0.66.Final-SNAPSHOT.jar")),
                "Did not give direct link to download netty-tcnative library");
    }
}
