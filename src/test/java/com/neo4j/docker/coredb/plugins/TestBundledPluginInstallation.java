package com.neo4j.docker.coredb.plugins;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Tag("BundleTest")
public class TestBundledPluginInstallation
{
    private final Logger log = LoggerFactory.getLogger( TestBundledPluginInstallation.class );
    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();

    static class BundledPlugin
    {
        private final String name;
        private final Neo4jVersion bundledSince;
        private final Neo4jVersion bundledUntil;
        private final boolean isEnterpriseOnly;

        public BundledPlugin(String name, Neo4jVersion bundledSince, @Nullable Neo4jVersion bundledUntil, boolean isEnterpriseOnly)
        {
            this.name = name;
            this.bundledSince = bundledSince;
            this.bundledUntil = bundledUntil;
            this.isEnterpriseOnly = isEnterpriseOnly;
        }

        public boolean shouldBePresentInImage()
        {
            boolean shouldBeBundled = TestSettings.NEO4J_VERSION.isAtLeastVersion( bundledSince );
            if(isEnterpriseOnly)
            {
                shouldBeBundled = shouldBeBundled && (TestSettings.EDITION == TestSettings.Edition.ENTERPRISE);
            }
            if(bundledUntil != null)
            {
                shouldBeBundled = shouldBeBundled && TestSettings.NEO4J_VERSION.isOlderThan( bundledUntil );
            }
            return shouldBeBundled;
        }

        @Override
        public String toString()
        {
            return "BundledPlugin " + name;
        }
    }

    private static final BundledPlugin APOC = new BundledPlugin("apoc",
            new Neo4jVersion(5, 0, 0), null,false);
    private static final BundledPlugin APOC_CORE = new BundledPlugin("apoc-core",
            new Neo4jVersion(4, 3, 15),
            new Neo4jVersion(5, 0, 0), false);
    private static final BundledPlugin BLOOM = new BundledPlugin("bloom",
            Neo4jVersion.NEO4J_VERSION_440, null, true);
    private static final BundledPlugin GDS = new BundledPlugin("graph-data-science",
            Neo4jVersion.NEO4J_VERSION_440, null, true );

    static Stream<Arguments> bundledPluginsArgs() {
        return Stream.of(
                Arguments.arguments(APOC_CORE),
                Arguments.arguments(APOC),
                 Arguments.arguments(GDS),
                Arguments.arguments(BLOOM)
        );
    }

    private GenericContainer createContainer()
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );
        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv("NEO4J_DEBUG", "yes")
                 .withExposedPorts( 7474, 7687 )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( WaitStrategies.waitForBoltReady() );
        return container;
    }

    private GenericContainer createContainerWithBundledPlugin(BundledPlugin plugin)
    {
        return createContainer().withEnv( Neo4jPluginEnv.get(), "[\"" +plugin.name+ "\"]" );
    }

    @ParameterizedTest(name = "testBundledPlugin_{0}")
    @MethodSource("bundledPluginsArgs")
    public void testBundledPlugin(BundledPlugin plugin) throws Exception
    {
        Assumptions.assumeTrue(plugin.shouldBePresentInImage(),
                "test only applies when the plugin "+plugin.name+"is present");

        GenericContainer container = null;
        Path pluginsMount = null;
        try
        {
            container = createContainerWithBundledPlugin(plugin);
            pluginsMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/plugins");
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", "none" );
        }
        catch(ContainerLaunchException e)
        {
            // we don't want this test to depend on the plugins actually working (that's outside the scope of
            // the docker tests), so we have to be robust to the container failing to start.
            log.error( String.format("The bundled %s plugin caused Neo4j to fail to start.", plugin.name) );
        }
        finally
        {
            // verify the plugins were loaded.
            // This is done in the finally block because after stopping the container, the stdout cannot be retrieved.
            if (pluginsMount != null)
            {
                List<String> plugins = Files.list(pluginsMount).map( fname -> fname.getFileName().toString() )
                                            .filter( fname -> fname.endsWith( ".jar" ) )
                                            .collect(Collectors.toList());
                Assertions.assertTrue(plugins.size() == 1, "more than one plugin was loaded" );
                Assertions.assertTrue( plugins.get( 0 ).contains( plugin.name ) );
                // Verify from container logs, that the plugins were loaded locally rather than downloaded.
                String logs = container.getLogs( OutputFrame.OutputType.STDOUT);
                String errlogs = container.getLogs( OutputFrame.OutputType.STDERR);
                Assertions.assertTrue(
                        Stream.of(logs.split( "\n" ))
                              .anyMatch( line -> line.matches( "Installing Plugin '" + plugin.name + "' from /var/lib/neo4j/.*" ) ),
                        "Plugin was not installed from neo4j home");
            }
            if(container !=null)
            {
                container.stop();
            }
            else
            {
                Assertions.fail("Test failed before container could even be initialised");
            }
        }
    }

    @Test
    public void testBundledPlugin_downloadsIfNotAvailableLocally() throws Exception
    {
        // Test that in community edition we try to download the plugins that would otherwise be bundled
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.COMMUNITY,
                                "Test only applies to enterprise only bundled plugins tested against community edition" );

        BundledPlugin plugin = GDS; // testing only GDS since
        GenericContainer container = null;
        Path pluginsMount = null;
        try
        {
            container = createContainerWithBundledPlugin(plugin);
            pluginsMount = temporaryFolderManager.createFolderAndMountAsVolume(container, "/plugins");
            container.start();
        }
        catch(ContainerLaunchException e)
        {
            // we don't want this test to depend on the plugins actually working (that's outside the scope of
            // the docker tests), so we have to be robust to the container failing to start.
            log.error( String.format("The %s plugin caused Neo4j to fail to start.", plugin.name) );
        }
        finally
        {
            // verify the plugins were loaded.
            // This is done in the finally block because after stopping the container, the stdout cannot be retrieved.
            if (pluginsMount != null)
            {
                // Verify from container logs, that the plugins were loaded locally rather than downloaded.
                String logs = container.getLogs( OutputFrame.OutputType.STDOUT);
                String errlogs = container.getLogs( OutputFrame.OutputType.STDERR);
                Assertions.assertTrue(
                        Stream.of(logs.split( "\n" ))
                                .anyMatch( line -> line.matches( "Fetching versions.json for Plugin '" + plugin.name + "' from http[s]?://.*" ) ),
                        "Plugin was not installed from cloud");

                // If we are testing an unreleased version of neo4j, then the plugin may fail to download.
                // This is expected, so we have to check that either the plugin is in the plugins mount,
                // or the logs report the download failed.

                List<String> plugins = Files.list(pluginsMount)
                        .map( path -> path.getFileName().toString() )
                        .filter( fileName -> fileName.endsWith( ".jar" ) )
                        .toList();
                if(plugins.isEmpty())
                {
                    // no plugins were downloaded, which is correct if we are testing an unreleased neo4j
                    String expectedError = String.format(".*No compatible \"%s\" plugin found for Neo4j %s(-[\\d]+)? community\\.",
                            plugin.name, TestSettings.NEO4J_VERSION);
                    Assertions.assertTrue(
                        Stream.of(errlogs.split( "\n" ))
                                .anyMatch( line -> line.matches( expectedError ) ),
                        "Should have errored that unreleased plugin could not be downloaded. " +
                                "Expected error: " + expectedError +
                                "\nActual errors:\n"+errlogs);
                }
                else
                {
                    // at least one plugin was downloaded, so check it is the correct one
                    Assertions.assertTrue(plugins.size() == 1, "more than one plugin was loaded" );
                    Assertions.assertTrue( plugins.get( 0 ).contains( plugin.name ) );
                }
            }
            container.stop();
        }
    }

    @Test
    void testPluginLoadsWithAuthentication() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ) );

        final String PASSWORD = "12345678";

        try( GenericContainer container = createContainerWithBundledPlugin(BLOOM))
        {
            container.withEnv( "NEO4J_AUTH", "neo4j/"+PASSWORD )
                     .withEnv( "NEO4J_dbms_bloom_license__file", "/licenses/bloom.license" );
            // mounting logs because it's useful for debugging
            temporaryFolderManager.createFolderAndMountAsVolume(container, "/logs");
            Path licenseFolder = temporaryFolderManager.createFolderAndMountAsVolume(container, "/licenses");
            Files.writeString( licenseFolder.resolve("bloom.license"), "notareallicense" );
            // make sure the container successfully starts and we can write to it without getting authentication errors
            container.start();
            DatabaseIO dbio = new DatabaseIO( container );
            dbio.putInitialDataIntoContainer( "neo4j", PASSWORD );
        }
    }

    @Test
    void testBrowserListensOn7474() throws Exception
    {
        try(GenericContainer container = createContainer())
        {
            container.waitingFor( new HttpWaitStrategy()
                                          .forPort(7474)
                                          .forStatusCode(200)
                                          .withStartupTimeout(Duration.ofSeconds(60)) );
            container.start();
            Assertions.assertTrue( container.isRunning() );
            Container.ExecResult r = container.execInContainer( "wget", "-q", "-O", "-", "http://localhost:7474/browser/" );
            Assertions.assertEquals( 0, r.getExitCode(), "Did not get http response from browser");
            Assertions.assertFalse( r.getStdout().isEmpty(), "HTTP response from browser was empty." );
            Assertions.assertTrue( r.getStdout().contains( "Neo4j Browser" ),
                                   "HTTP response from browser did not contain expected information.\n"+r.getStdout());
        }
    }
}
