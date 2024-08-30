package com.neo4j.docker.utils;

import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SSLCertificateFactory
{
    public static final String CERTIFICATE_FILENAME = "casigned.crt";
    // Certificates need to be owned by the user inside the container, which means that tests using drivers
    // cannot authenticate because they do not have permission to read the certificate file.
    // The factory creates a copy of the certificate that can always be used by the test's client side.
    public static final String CLIENT_CERTIFICATE_FILENAME = "local"+CERTIFICATE_FILENAME;
    public static final String PRIVATE_KEY_FILENAME = "private.key";
    public static final String ENCRYPTED_PASSPHRASE_FILENAME = "passphrase.enc";
    private static final Path SSL_RESOURCES = Paths.get("src", "test", "resources", "ssl");
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

            // copy ssl certificate generating script to mounted folder
            Path scriptPath = this.outputFolder.resolve("gen-scripts");
            Files.createDirectory(scriptPath);
            Files.copy(SSL_RESOURCES.resolve("ca.conf"), scriptPath.resolve("ca.conf"));
            Files.copy(SSL_RESOURCES.resolve("server.conf"), scriptPath.resolve("server.conf"));
            Files.copy(SSL_RESOURCES.resolve("gen-ssl-cert.sh"), scriptPath.resolve("gen-ssl-cert.sh"));

            container.withExposedPorts(80)
                    .withWorkingDirectory(mountpoint + "/gen-scripts")
                    .waitingFor(Wait.forHttp("/")
                            .withStartupTimeout(Duration.ofSeconds(20)));
            container.start();

            List<String> genCertCommand = new ArrayList<>();
            genCertCommand.add("./gen-ssl-cert.sh");
            genCertCommand.add("--folder");
            genCertCommand.add(mountpoint);
            if(this.passphrase != null)
            {
                genCertCommand.add("--passphrase");
                genCertCommand.add(this.passphrase);
            }
            if(this.isPassphraseEncrypted)
            {
                genCertCommand.add("--encrypt");
            }

            Container.ExecResult cmd = container.execInContainer(genCertCommand.toArray(String[]::new));
            if(cmd.getExitCode() != 0)
            {
                throw new IllegalArgumentException("Could not generate SSL keys. Error:\n" + cmd);
            }

            container.execInContainer("chown", "-R", this.owner, mountpoint);
            // copy the certificate and make it readable by the user running the unit tests
            // otherwise we can't validate commands on the client side
            container.execInContainer("cp", mountpoint+"/"+CERTIFICATE_FILENAME,
                    mountpoint+"/"+CLIENT_CERTIFICATE_FILENAME);
            container.execInContainer("chown", SetContainerUser.getNonRootUserString(), mountpoint+"/"+CLIENT_CERTIFICATE_FILENAME);
            container.execInContainer("sh", "-c",
                    String.format("cd %s; rm -rf %s/%s", mountpoint, mountpoint, scriptPath.getFileName()));
        }
    }

    public static String getPassphraseDecryptCommand(String certificatesMountPoint)
    {
        return String.format("base64 -w 0 %s/selfsigned.crt | openssl aes-256-cbc -a -d " +
                "-in %s/%s -pass stdin", certificatesMountPoint, certificatesMountPoint, ENCRYPTED_PASSPHRASE_FILENAME);
    }
}
