package com.neo4j.docker.coredb;

import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;


public class TestFIPS
{
    private static Logger log = LoggerFactory.getLogger(TestFIPS.class);
    public static final String FIPS_FLAG = "NEO4J_OPENSSL_FIPS_ENABLE";
    
    @Test
    void testOpensslIsConfigured() throws Exception
    {
        try(GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID ))
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                    .withEnv( "NEO4J_AUTH", "none" )
                    .withExposedPorts( 7474, 7687 )
                    .withLogConsumer( new Slf4jLogConsumer( log ) )
                    .waitingFor(WaitStrategies.waitForBoltReady(Duration.ofSeconds(60)));
            container.start();
            Container.ExecResult versionOut = container.execInContainer("openssl", "version", "-a");
            Container.ExecResult providersOut = container.execInContainer("openssl", "list", "-providers");

            // verify openssl version
            Assertions.assertEquals(0, versionOut.getExitCode(), "OpenSSL command failed. Full output:\n"+versionOut);
            List<String> openssl = Arrays.stream(versionOut.getStdout().split("\n")).toList();
            Assertions.assertTrue(openssl.get(0).contains("OpenSSL 3.0.9"), "OpenSSL 3.0.9 is not installed.\n"+versionOut);
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
                if(line.startsWith("name"))
                {
                    Assertions.assertTrue(line.matches("name:\\s+OpenSSL FIPS Provider"),
                            "FIPS provider has an unexpected name\n" + providersOut);
                }
                else if(line.startsWith("version"))
                {
                    Assertions.assertTrue(line.matches("version:\\s+3\\.0\\.9"),
                            "FIPS version is not 3.0.9:\n" + providersOut);
                }
                else if(line.startsWith("status"))
                {
                    Assertions.assertTrue(line.matches("status:\\s+active"),
                            "FIPS is not the active provider:\n" + providersOut);
                }
            }
        }
    }
}
