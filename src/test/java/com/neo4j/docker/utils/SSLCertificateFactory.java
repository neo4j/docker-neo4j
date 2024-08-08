package com.neo4j.docker.utils;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;

public class SSLCertificateFactory
{
    private final Path outputFolder;
    private String passphrase = null;
    private boolean isPassphraseEncrypted = false;
    private String owner = SetContainerUser.getNeo4jUserString();

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
        // using nginx image because it's easy to verify startup and it has openssl already installed
        try (GenericContainer container = new GenericContainer(DockerImageName.parse("nginx:latest")))
        {
            String mountpoint = "/certgen";
            TemporaryFolderManager.mountHostFolderAsVolume(container, this.outputFolder, mountpoint);
            container.withExposedPorts(80)
                    .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofSeconds(20)));
            container.start();
            container.execInContainer("openssl", "req", "-x509", "-sha1", "-nodes",
                    "-newkey", "rsa:2048", "-keyout", mountpoint+"/private.key1",
                    "-out", mountpoint+"/selfsigned.crt",
                    "-subj", "/C=SE/O=Example/OU=ExampleCluster/CN=localhost",
                    "-days", "1");
            if(this.passphrase == null)
            {
                container.execInContainer("openssl", "pkcs8", "-topk8",
                        "-in", mountpoint+"/private.key1",
                        "-out", mountpoint+"/private.key");
            }
            else
            {
                container.execInContainer("openssl", "pkcs8", "-topk8",
                        "-in", mountpoint+"/private.key1",
                        "-out", mountpoint+"/private.key",
                        "-passout", "pass:" + this.passphrase);
                if(this.isPassphraseEncrypted)
                {
                    container.execInContainer("sh", "-c", "echo " + this.passphrase + " > /tmp/passfile");
                    container.execInContainer("sh", "-c", "base64 -w 0 " + mountpoint+"/selfsigned.crt | " +
                            "openssl aes-256-cbc -a -salt -pass stdin -in /tmp/passfile -out " + mountpoint + "/password.enc");
                    container.execInContainer("rm", "/tmp/passfile");
                }
            }
            container.execInContainer("rm", mountpoint+"/private.key1");
            container.execInContainer("chown", "-R", this.owner, mountpoint);
            // copy the certificate and make it readable by the user running the unit tests
            // otherwise we can't validate commands on the client side
            container.execInContainer("cp", mountpoint+"/selfsigned.crt", mountpoint+"/localselfsigned.crt");
            container.execInContainer("chown", SetContainerUser.getNonRootUserString(), mountpoint+"/localselfsigned.crt");
        }
    }

    public static String getPassphraseDecryptCommand(String certificatesMountPoint)
    {
        return String.format("sh -c \"base64 -w 0 %s/selfsigned.crt | openssl aes-256-cbc -a -d " +
                "-in %s/password.enc -pass stdin\"", certificatesMountPoint, certificatesMountPoint);
    }
}
