package com.neo4j.docker.utils;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class SSLCertificateFactoryTest
{
    private static final int NEO4J_USER_ID = Integer.parseInt(SetContainerUser.getNeo4jUserString().split(":")[0]);
    private static final int CURRENT_USER_ID = Integer.parseInt(SetContainerUser.getNonRootUserString().split(":")[0]);

    @RegisterExtension
    static TemporaryFolderManager folderManager = new TemporaryFolderManager();

    // Certificate generator needs to follow requirements from
    // https://neo4j.com/docs/operations-manual/current/security/ssl-framework/#ssl-certificates
    // Of all the artifacts produces by SSLCertificateFactory only the client certificate MUST be readable by the test user.
    // The rest of the artifacts should be owned by neo4j (default) or the test user if specified.

    @Test
    void generatesMatchingCertificateAndKey() throws Exception
    {
        Path outdir = folderManager.createFolder("certificates");
        new SSLCertificateFactory(outdir)
                .withOwnerNeo4j()
                .build();
        File cert = outdir.resolve(SSLCertificateFactory.CERTIFICATE_FILENAME).toFile();
        File clientCert = outdir.resolve(SSLCertificateFactory.CLIENT_CERTIFICATE_FILENAME).toFile();
        File key = outdir.resolve(SSLCertificateFactory.PRIVATE_KEY_FILENAME).toFile();

        // verify the files exist
        Assertions.assertTrue(cert.exists(), "Certificate was not created");
        Assertions.assertTrue(key.exists(), "Private key was not created");
        Assertions.assertTrue(clientCert.exists(), "Client side certificate was not created");
        Assertions.assertTrue(clientCert.canRead(), "Client certificate is not readable by test user");

        verifyFileOwnership(outdir, NEO4J_USER_ID);
        verifyCertificatesAndKeysMatch(outdir, null);
    }

    @Test
    void shouldPassphraseProtectKey() throws Exception
    {
        String passphrase = "a123long456passphrase";
        Path outdir = folderManager.createFolder("certificates");
        new SSLCertificateFactory(outdir)
                .withOwnerNeo4j()
                .withSSLKeyPassphrase(passphrase, false)
                .build();
        verifyFileOwnership(outdir, NEO4J_USER_ID);
        verifyCertificatesAndKeysMatch(outdir, passphrase);
    }

    @Test
    void shouldEncryptKeyPassphrase() throws Exception
    {
        String passphrase = "a123long456passphrase";
        Path outdir = folderManager.createFolder("certificates");
        new SSLCertificateFactory(outdir)
                .withOwnerNeo4j()
                .withSSLKeyPassphrase(passphrase, true)
                .build();
        Path encryptedPassphrase = outdir.resolve(SSLCertificateFactory.ENCRYPTED_PASSPHRASE_FILENAME);
        Assertions.assertTrue(encryptedPassphrase.toFile().exists(), "Encrypted passphrase file was not created");
        verifyFileOwnership(outdir, NEO4J_USER_ID);
        verifyCertificatesAndKeysMatch(outdir, passphrase);

        // verify the decrypt command works
        try(GenericContainer container = makeOpenSSLContainer(outdir))
        {
            String decryptCommand = SSLCertificateFactory.getPassphraseDecryptCommand("/certificates");
            container.start();
            Container.ExecResult decryptResult = container.execInContainer("sh", "-c", decryptCommand);
            Assertions.assertEquals(0, decryptResult.getExitCode(), "decrypt command unsuccessful");
            Assertions.assertEquals(passphrase, decryptResult.getStdout().trim(),
                    "The decrypt command does not successfully decrypt the passphrase.\n"+decryptResult);
        }
    }

    @Test
    void shouldSetOwnerCurrentUser() throws Exception
    {
        Path outdir = folderManager.createFolder("certificates");

        new SSLCertificateFactory(outdir)
                .withOwnerNonRootUser()
                .withSSLKeyPassphrase("somepassphrasedoesntmatter", true)
                .build();
        verifyFileOwnership(outdir, CURRENT_USER_ID);
    }

    @Test
    void shouldErrorIfNoOwnerSet() throws Exception
    {
        Path outdir = folderManager.createFolder("certificates");
        SSLCertificateFactory factory = new SSLCertificateFactory(outdir);
        Assertions.assertThrows(IllegalArgumentException.class, ()->factory.build(),
                "Should have thrown error that file owner has not been set");
    }

    private void verifyFileOwnership(Path certificateDir, int expectedUID) throws Exception
    {
        Path cert = certificateDir.resolve(SSLCertificateFactory.CERTIFICATE_FILENAME);
        Path clientCert = certificateDir.resolve(SSLCertificateFactory.CLIENT_CERTIFICATE_FILENAME);
        Path key = certificateDir.resolve(SSLCertificateFactory.PRIVATE_KEY_FILENAME);
        Path passphrase = certificateDir.resolve(SSLCertificateFactory.ENCRYPTED_PASSPHRASE_FILENAME);

        Assertions.assertEquals(expectedUID, Files.getAttribute(cert, "unix:uid"),
                "Owner of certificate was not set to "+expectedUID);
        Assertions.assertEquals(CURRENT_USER_ID, Files.getAttribute(clientCert, "unix:uid"),
                "Owner of client certificate was not set to "+CURRENT_USER_ID);
        Assertions.assertEquals(expectedUID, Files.getAttribute(key, "unix:uid"),
                "Owner of private key was not set to "+expectedUID);
        if(passphrase.toFile().exists())
        {
            Assertions.assertEquals(expectedUID, Files.getAttribute(passphrase, "unix:uid"),
                "Owner of encrypted passphrase was not set to "+expectedUID);
        }
    }

    private GenericContainer makeOpenSSLContainer(Path certificateDir)
    {
        GenericContainer container = new GenericContainer(DockerImageName.parse("nginx:latest"));
        TemporaryFolderManager.mountHostFolderAsVolume(container, certificateDir, "/certificates");
        container.withExposedPorts(80)
                .waitingFor(Wait.forHttp("/")
                        .withStartupTimeout(Duration.ofSeconds(20)));
        return container;
    }

    private void verifyCertificatesAndKeysMatch(Path certificateDir, @Nullable String keyPassphrase) throws Exception
    {
        try(GenericContainer container = makeOpenSSLContainer(certificateDir))
        {
            container.start();
            // verify certificates and key are pem format and match
            // the `-inform pem` means that the commands will fail if certs/keys are not in PEM format.
            Container.ExecResult certModulus = container.execInContainer("openssl", "x509",
                    "-in", "/certificates/"+SSLCertificateFactory.CERTIFICATE_FILENAME,
                    "-inform", "pem",
                    "-noout", "-modulus");
            Container.ExecResult clientCertModulus = container.execInContainer("openssl", "x509",
                    "-in", "/certificates/"+SSLCertificateFactory.CLIENT_CERTIFICATE_FILENAME,
                    "-inform", "pem",
                    "-noout", "-modulus");
            Container.ExecResult keyModulus;
            if(keyPassphrase == null)
            {
                keyModulus = container.execInContainer("openssl", "rsa",
                        "-in", "/certificates/" + SSLCertificateFactory.PRIVATE_KEY_FILENAME,
                        "-inform", "pem",
                        "-noout", "-modulus");
            }
            else
            {
                keyModulus = container.execInContainer("openssl", "rsa",
                        "-in", "/certificates/" + SSLCertificateFactory.PRIVATE_KEY_FILENAME,
                        "-inform", "pem",
                        "-noout", "-modulus",
                        "-passin", "pass:"+keyPassphrase);
            }
            Assertions.assertEquals(0, certModulus.getExitCode(),
                    "Certificate was not created in x509 PEM format.\n"+certModulus);
            Assertions.assertEquals(0, clientCertModulus.getExitCode(),
                    "Client certificate was not created in x509 PEM format.\n"+clientCertModulus);
            Assertions.assertEquals(0, keyModulus.getExitCode(),
                    "Private key was not created in PEM format.\n"+keyModulus);
            Assertions.assertEquals(certModulus.getStdout(), keyModulus.getStdout(), "Certificate and private key do not match");
            Assertions.assertEquals(clientCertModulus.getStdout(), keyModulus.getStdout(), "Client certificate and private key do not match");
        }
    }
}
