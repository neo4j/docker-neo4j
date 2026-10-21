package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;

/**
 * Reads the JDK trust store (cacerts) of an image by running keytool in a one-off container.
 */
public class TrustStore {
    private static final Logger log = LoggerFactory.getLogger(TrustStore.class);

    /**
     * eMudhra emSign roots removed by Mozilla in NSS 3.131.1 after eight eMudhra intermediate CA keys were
     * found to be compromised (October 2026).
     */
    public static final List<String> COMPROMISED_EMUDHRA_ROOTS =
            List.of("emSign ECC Root CA - G3", "emSign ECC Root CA - C3", "emSign Root CA - C1");

    /** Common names (CN) of the certificates in the image's JDK trust store. */
    public static List<String> trustedCertificateNames(DockerImageName image) {
        String output;
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            container
                    .withCreateContainerCmdModifier((Consumer<CreateContainerCmd>) cmd -> cmd.withEntrypoint("keytool"))
                    .withCommand("-list", "-v", "-cacerts", "-storepass", "changeit");
            WaitStrategies.waitUntilContainerFinished(container, Duration.ofSeconds(60));
            container.start();
            output = container.getLogs(OutputFrame.OutputType.STDOUT);
        }
        // keytool prints one "Owner: CN=<name>, O=..., C=..." line per certificate
        List<String> names = output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("Owner: CN="))
                .map(line -> line.substring("Owner: CN=".length()).split(",")[0])
                .toList();
        log.info("{} certificates in the JDK trust store of {}", names.size(), image);
        return names;
    }

    public static void assertNoCompromisedEmudhraRoots(DockerImageName image) {
        List<String> stillTrusted = trustedCertificateNames(image).stream()
                .filter(COMPROMISED_EMUDHRA_ROOTS::contains)
                .toList();
        Assertions.assertTrue(
                stillTrusted.isEmpty(),
                "The JDK trust store of " + image + " still trusts compromised eMudhra roots: " + stillTrusted);
    }

    public static void assertTrustStoreIsPopulated(DockerImageName image) {
        // Guards against a broken or emptied cacerts; the Temurin trust store holds well over 100 roots.
        int count = trustedCertificateNames(image).size();
        Assertions.assertTrue(
                count > 100, "The JDK trust store of " + image + " only holds " + count + " certificates");
    }
}
