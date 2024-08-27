package com.neo4j.docker.utils;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class SSLCertificateFactory
{
    public static final String CERTIFICATE_FILENAME="selfsigned.crt";
    // Certificates need to be owned by the user inside the container, which means that tests using drivers
    // cannot authenticate because they do not have permission to read the certificate file.
    // The factory creates a copy of the certificate that can always be used by the test's client side.
    public static final String CLIENT_CERTIFICATE_FILENAME ="localselfsigned.crt";
    public static final String PRIVATE_KEY_FILENAME="private.key";
    public static final String ENCRYPTED_PASSPHRASE_FILENAME="password.enc";
    private static final Path CERT_CONF = Paths.get("src", "test", "resources", "ssl", "server.conf");
    private final Path outputFolder;
    private String passphrase = null;
    private boolean isPassphraseEncrypted = false;
    private String owner = null;

    public SSLCertificateFactory(Path outputFolder)
    {
        this.outputFolder = outputFolder;
    }

    public SSLCertificateFactory withSSLKeyPassphrase(String passphrase, boolean isPassphraseEncrypted)
    {
        this.passphrase = passphrase;
        this.isPassphraseEncrypted = isPassphraseEncrypted;
        return this;
    }
    public SSLCertificateFactory withoutSSLKeyPassphrase()
    {
        this.passphrase = null;
        this.isPassphraseEncrypted = false;
        return this;
    }

    public SSLCertificateFactory withOwnerNeo4j()
    {
        this.owner = SetContainerUser.getNeo4jUserString();
        return this;
    }

    public SSLCertificateFactory withOwnerNonRootUser()
    {
        this.owner = SetContainerUser.getNonRootUserString();
        return this;
    }

    public void build() throws Exception
    {
        if(this.owner == null)
        {
            throw new IllegalArgumentException("File owner has not been set for SSL certificates. This is a test error.");
        }
        // using nginx image because it's easy to verify startup and it has openssl already installed
        try (GenericContainer container = new GenericContainer(DockerImageName.parse("nginx:latest")))
        {
            String mountpoint = "/certgen";
            TemporaryFolderManager.mountHostFolderAsVolume(container, this.outputFolder, mountpoint);
            Files.copy(CERT_CONF, this.outputFolder.resolve("cert.conf"));
            container.withExposedPorts(80)
                    .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofSeconds(20)));
            container.start();
            container.execInContainer("openssl", "req", "-x509", "-sha1", "-nodes",
                    "-newkey", "rsa:2048",
                    "-keyout", mountpoint+"/private.key1",
                    "-config", mountpoint+"/cert.conf",
                    "-out", mountpoint+"/selfsigned.crt",
                    "-subj", "/C=SE/O=Example/OU=ExampleCluster/CN=localhost",
                    "-days", "1");
            if(this.passphrase == null)
            {
                container.execInContainer("openssl", "pkcs8", "-topk8", "-nocrypt",
                        "-in", mountpoint + "/private.key1",
                        "-out", mountpoint + "/private.key",
                        "-passout", "pass:" + this.passphrase);
            }
            else
            {
                container.execInContainer("openssl", "pkcs8", "-topk8",
                        "-in", mountpoint+"/private.key1",
                        "-out", mountpoint+"/private.key",
                        "-passout", "pass:" + this.passphrase);
            }
            container.execInContainer("rm", mountpoint+"/private.key1");

            if(this.isPassphraseEncrypted)
            {
                container.execInContainer("sh", "-c", "echo " + this.passphrase + " > /tmp/passfile");
                container.execInContainer("sh", "-c", "base64 -w 0 " + mountpoint+"/selfsigned.crt | " +
                        "openssl aes-256-cbc -a -salt -pass stdin -in /tmp/passfile -out " + mountpoint + "/password.enc");
                container.execInContainer("rm", "/tmp/passfile");
            }

            container.execInContainer("chown", "-R", this.owner, mountpoint);
            // copy the certificate and make it readable by the user running the unit tests
            // otherwise we can't validate commands on the client side
            container.execInContainer("cp", mountpoint+"/selfsigned.crt", mountpoint+"/localselfsigned.crt");
            container.execInContainer("chown", SetContainerUser.getNonRootUserString(), mountpoint+"/localselfsigned.crt");
        }
    }

    public static String getPassphraseDecryptCommand(String certificatesMountPoint)
    {
        return String.format("base64 -w 0 %s/selfsigned.crt | openssl aes-256-cbc -a -d " +
                "-in %s/password.enc -pass stdin", certificatesMountPoint, certificatesMountPoint);
    }
}
