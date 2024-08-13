package com.neo4j.docker.coredb;

import com.neo4j.docker.utils.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.driver.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


@Tag("BundleTest")
public class TestSSL
{
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
    public static final String FIPS_FLAG = "NEO4J_OPENSSL_FIPS_ENABLE";
    public static final String PASSWORD = "MYsuperSECRETpassword123";
    public static final String SSL_KEY_PASSPHRASE = "abcdef1234567890";
    public static final String OPENSSL_VERSION = "3.0.9";
    public static final String NETTY_TCNATIVE_VERSION = "2.0.65.Final";
    public static final String OPENSSL_INSTALL_DIR = "/usr/local/openssl";
    private static final Logger log = LoggerFactory.getLogger(TestSSL.class);
    private static Path tcnativeBoringSSLJar = null;


    private void assumeFIPSCompatible()
    {
        Assumptions.assumeTrue(TestSettings.NEO4J_VERSION.isAtLeastVersion(new Neo4jVersion(5, 21, 0)),
                "FIPS compliance was introduced after 5.21.0.");
        Assumptions.assumeTrue(TestSettings.BASE_OS == TestSettings.BaseOS.UBI9,
                "Test only applies to UBI9 based image.");
    }

    private GenericContainer createContainer()
    {
        GenericContainer<?> container = new GenericContainer<>(TestSettings.IMAGE_ID)
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withEnv("NEO4J_AUTH", "neo4j/"+PASSWORD)
                .withEnv("NEO4J_DEBUG", "yes")
                .withExposedPorts(7474, 7687)
                .withLogConsumer(new Slf4jLogConsumer(log))
                .waitingFor(WaitStrategies.waitForNeo4jReady(PASSWORD, Duration.ofSeconds(60)));
        return container;
    }

    private Path configureContainerForSSL(GenericContainer container) throws Exception
    {
        // generate certificates
        Path certificates = temporaryFolderManager.createFolder("certificates");
        new SSLCertificateFactory(certificates)
                .withSSLKeyPassphrase(SSL_KEY_PASSPHRASE, true)
                .withOwnerNeo4j()
                .build();
        temporaryFolderManager.mountHostFolderAsVolume(container, certificates, "/ssl");
        // configure Neo4j for SSL over bolt
        Path conf = temporaryFolderManager.createFolderAndMountAsVolume(container, "/conf");
        FileWriter confFile = new FileWriter(conf.resolve("neo4j.conf").toFile());
        confFile.write("dbms.netty.ssl.provider=OPENSSL\n");
        confFile.write("server.https.enabled=false\n");
        confFile.write("server.bolt.tls_level=REQUIRED\n");
        confFile.write("dbms.ssl.policy.bolt.tls_level=REQUIRED\n");
        confFile.write("dbms.ssl.policy.bolt.enabled=true\n");
        confFile.write("dbms.ssl.policy.bolt.client_auth=NONE\n");
        confFile.write("dbms.ssl.policy.bolt.trust_all=false\n");
        confFile.write("dbms.ssl.policy.bolt.tls_versions=TLSv1.3\n");
        confFile.write("dbms.ssl.policy.bolt.private_key_password=$(sh -c \"" +
                SSLCertificateFactory.getPassphraseDecryptCommand("/ssl")+ "\")\n");
        confFile.write("dbms.ssl.policy.bolt.base_directory=/ssl\n");
        confFile.write("dbms.ssl.policy.bolt.private_key=private.key\n");
        confFile.write("dbms.ssl.policy.bolt.public_certificate=selfsigned.crt\n");
        confFile.flush();
        confFile.close();
        // use extended conf feature to expand private key passphrase
        container.withEnv("EXTENDED_CONF", "true");
        Files.setPosixFilePermissions(conf.resolve("neo4j.conf"), new HashSet<>()
        {{
            add(PosixFilePermission.OWNER_READ);
            add(PosixFilePermission.OWNER_WRITE);
            add(PosixFilePermission.GROUP_READ);
        }});
        temporaryFolderManager.setFolderOwnerToNeo4j(conf.resolve("neo4j.conf"));
        return certificates;
    }

    @Test
    void testOpenSSLIsInstalledWithFIPS() throws Exception
    {
        assumeFIPSCompatible();
        Container.ExecResult whichOpenSSL, versionOut, providersOut;
        try(GenericContainer container = createContainer())
        {
            container.withEnv(FIPS_FLAG, "true");
            container.start();
            whichOpenSSL = container.execInContainer("which", "openssl");
            log.info("OpenSSL location is \""+whichOpenSSL.getStdout()+"\"");
            versionOut = container.execInContainer("openssl", "version", "-a");
            log.info("openssl version -a:\n"+versionOut.getStdout());
            providersOut = container.execInContainer("openssl", "list", "-providers");
            log.info("openssl providers:\n"+providersOut.getStdout());
        }

        // verify openssl version
        Assertions.assertEquals(0, whichOpenSSL.getExitCode(),
                "openssl not installed! Full output:\n" + whichOpenSSL);
        Assertions.assertTrue(whichOpenSSL.getStdout().startsWith(OPENSSL_INSTALL_DIR),
                "Using OpenSSL in dir " + whichOpenSSL.getStdout() + " instead of " + OPENSSL_INSTALL_DIR);
        Assertions.assertEquals(0, versionOut.getExitCode(),
                "OpenSSL command failed. Full output:\n" + versionOut);
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
        assumeFIPSCompatible();
        String filesInUse;
        try(GenericContainer container = createContainer())
        {
            container.withEnv(FIPS_FLAG, "true");
            configureContainerForSSL(container);
            container.start();
            String neo4jPID = container.execInContainer("cat", "/var/lib/neo4j/run/neo4j.pid").getStdout();
            filesInUse = container.execInContainer("su-exec", "neo4j", "cat", "/proc/"+neo4jPID+"/maps").getStdout();
        }
        verifyProcessAccessesFile(filesInUse, "libssl.so", OPENSSL_INSTALL_DIR);
        verifyProcessAccessesFile(filesInUse, "libcrypto.so", OPENSSL_INSTALL_DIR);
        verifyProcessAccessesFile(filesInUse, "fips.so", OPENSSL_INSTALL_DIR);
    }

    private void verifyProcessAccessesFile(String filesInUse, String filename, String expectedPath)
    {
        List<String> fileReads = Arrays.stream(filesInUse.split("\n"))
                .filter(t -> t.contains(filename))
                .toList();
        Assertions.assertFalse(fileReads.isEmpty(), "Neo4j did not use "+filename+" at all." +
                " Actual files read were:\n"+filesInUse);
        String regex = String.format(".*%s/.*%s.*", expectedPath, filename);
        for(String line : fileReads)
        {
            Assertions.assertTrue(line.matches(regex),
                    "Did not use "+filename+" under path "+expectedPath+". Actual file: "+line);
        }
    }

    @Test
    void shouldFailIfDebianAndFIPS() throws Exception
    {
        Assumptions.assumeTrue(TestSettings.BASE_OS == TestSettings.BaseOS.BULLSEYE,
                "Test only applies to debian based images");
        try(GenericContainer container = createContainer())
        {
            container.withEnv(FIPS_FLAG, "true");
            container.withEnv("NEO4J_AUTH", "bum/true");
            WaitStrategies.waitUntilContainerFinished(container, Duration.ofSeconds(30));
            Assertions.assertThrows(ContainerLaunchException.class, container::start);
            String logs = container.getLogs();
            Assertions.assertTrue(logs.contains("OpenSSL FIPS compatibility is only available in the Red Hat UBI9 Neo4j image"),
                    "Did not error about FIPS compatibility in Debian");
        }
    }

    @Test
    void testEndToEndSSLEncryption_withFIPS() throws Exception
    {
        assumeFIPSCompatible();
        try(GenericContainer container = createContainer())
        {
            container.withEnv(FIPS_FLAG, "true");
            verifyEndToEndSSLEncryption(container);
        }
    }

    @Test
    void testEndToEndSSLEncryption() throws Exception
    {
        try(GenericContainer container = createContainer())
        {
            container.withEnv(FIPS_FLAG, "false");
            Path pluginDir = getTCNativeBoringSSL();
            TemporaryFolderManager.mountHostFolderAsVolume(container, pluginDir.getParent(), "/plugins");
            verifyEndToEndSSLEncryption(container);
        }
    }

    private synchronized Path getTCNativeBoringSSL() throws Exception
    {
        if(tcnativeBoringSSLJar == null)
        {
            String boringSSLJarName = "netty-tcnative-boringssl.jar";
            try (GenericContainer container = new GenericContainer(DockerImageName.parse("nginx:latest"))) {
                Path boringjar = temporaryFolderManager.createFolderAndMountAsVolume(container, "/boringssljar");
                container.waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofSeconds(20)));
                container.start();
                String arch = container.execInContainer("arch").getStdout().trim();
                container.execInContainer("curl", "-sL", "-o", "/boringssljar/"+boringSSLJarName,
                        "https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/" + NETTY_TCNATIVE_VERSION +
                                "/netty-tcnative-boringssl-static-"+NETTY_TCNATIVE_VERSION+"-linux-"+arch+".jar");
                tcnativeBoringSSLJar = boringjar.resolve(boringSSLJarName);
                Assertions.assertTrue(tcnativeBoringSSLJar.toFile().exists(), "Could not download TCNative BoringSSL jar");
            }
        }
        return tcnativeBoringSSLJar;
    }

    void verifyEndToEndSSLEncryption(GenericContainer container) throws Exception
    {
        Path certificates = configureContainerForSSL(container);
        temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
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

        // NMap doesn't work in bullseye because the old openssl installed from apt confuses it
        if(TestSettings.BASE_OS != TestSettings.BaseOS.BULLSEYE)
        {
            container.execInContainer("microdnf", "install", "-y", "nmap");
            String nmapOut = container.execInContainer("nmap", "--script", "ssl-enum-ciphers", "-p", "7687", "localhost").getStdout();
            log.info("nmap scan returned:\n"+nmapOut);

            List<String> nmap = Arrays.stream(nmapOut.split("\n"))
                    .filter(line -> line.contains("least strength: A"))
                    .toList();
            Assertions.assertEquals(1, nmap.size(),
                    "NMap scan shows port 7687 is not secure:\n"+nmapOut);
        }
    }
}