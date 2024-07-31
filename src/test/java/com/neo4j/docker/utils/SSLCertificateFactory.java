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
    private String owner = SetContainerUser.getNeo4jUserString();

    public SSLCertificateFactory(Path outputFolder)
    {
        this.outputFolder = outputFolder;
    }

    public SSLCertificateFactory withSSLKeyPassphrase(String passphrase)
    {
        this.passphrase = passphrase;
        return this;
    }
    public SSLCertificateFactory withoutSSLKeyPassphrase()
    {
        this.passphrase = null;
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
            TemporaryFolderManager.mountHostFolderAsVolume(container, this.outputFolder, "/certificates");
            container.withExposedPorts(80)
                    .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofSeconds(20)));
            container.start();
            container.execInContainer("openssl", "req", "-x509", "-sha1", "-nodes",
                    "-newkey", "rsa:2048", "-keyout", "/certificates/private.key1",
                    "-out", "/certificates/selfsigned.crt",
                    "-subj", "/C=SE/O=Example/OU=ExampleCluster/CN=localhost",
                    "-days", "1");
            if(this.passphrase == null)
            {
                container.execInContainer("openssl", "pkcs8", "-topk8",
                        "-in", "/certificates/private.key1",
                        "-out", "/certificates/private.key");
            }
            else
            {
                container.execInContainer("openssl", "pkcs8", "-topk8",
                        "-in", "/certificates/private.key1",
                        "-out", "/certificates/private.key",
                        "-passout", "pass:" + this.passphrase);
            }
            container.execInContainer("rm", "/certificates/private.key1");
            container.execInContainer("chown", "-R", this.owner, "/certificates");
            // copy the certificate and make it readable by the user running the unit tests
            // otherwise we can't validate commands on the client side
            container.execInContainer("cp", "/certificates/selfsigned.crt", "/certificates/localselfsigned.crt");
            container.execInContainer("chown", SetContainerUser.getNonRootUserString(), "/certificates/localselfsigned.crt");
        }
    }
}
