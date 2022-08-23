package com.neo4j.docker.neo4jserver.plugins;

import com.neo4j.docker.utils.HostFileSystemOperations;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestBundledPluginInstallation
{
    private static final int DEFAULT_BROWSER_PORT = 7474;
    private static final int DEFAULT_BOLT_PORT = 7687;
    private static final Logger log = LoggerFactory.getLogger( TestBundledPluginInstallation.class );


    static Stream<Arguments> bundledPluginsArgs() {
        return Stream.of(
                // plugin name key, version it's bundled since, is enterprise only
               Arguments.arguments( "apoc-core", new Neo4jVersion(4, 3, 15), new Neo4jVersion(5, 0, 0), false ),
               Arguments.arguments( "apoc", new Neo4jVersion(5, 0, 0), null, false ),
               Arguments.arguments( "graph-data-science", new Neo4jVersion( 4,4,0 ), null, true ),
               Arguments.arguments( "bloom", new Neo4jVersion( 4,4,0 ), null, true )
        );
    }

    private GenericContainer createContainerWithBundledPlugin(String pluginName)
    {
        GenericContainer container = new GenericContainer( TestSettings.IMAGE_ID );

        container.withEnv( "NEO4J_AUTH", "none" )
                 .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                 .withEnv( Neo4jPluginEnv.get(), "[\"" +pluginName+ "\"]" )
                 .withExposedPorts( DEFAULT_BROWSER_PORT, DEFAULT_BOLT_PORT )
                 .withLogConsumer( new Slf4jLogConsumer( log ) )
                 .waitingFor( Wait.forHttp( "/" )
                                  .forPort( DEFAULT_BROWSER_PORT )
                                  .forStatusCode( 200 )
                                  .withStartupTimeout( Duration.ofSeconds( 30 ) )  );
        return container;
    }

    @ParameterizedTest(name = "testBundledPlugin_{0}")
    @MethodSource("bundledPluginsArgs")
    public void testBundledPlugin(String pluginName, Neo4jVersion bundledSince, Neo4jVersion bundledUntil, boolean isEnterpriseOnly) throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( bundledSince ),
                                String.format("plugin %s was not bundled in Neo4j %s", pluginName, bundledSince));
        if(bundledUntil != null) {
            Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( bundledUntil ),
                                    String.format("plugin %s was not bundled after Neo4j %s", pluginName, bundledUntil));
        }
        if(isEnterpriseOnly)
        {
            Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.ENTERPRISE,
                                    String.format("plugin %s is enterprise only", pluginName));
        }

        GenericContainer container = null;
        Path pluginsMount = null;
        try
        {
            container = createContainerWithBundledPlugin( pluginName );
            pluginsMount = HostFileSystemOperations
                    .createTempFolderAndMountAsVolume( container,
                                                       "bundled-"+pluginName+"-plugin-",
                                                       "/plugins" );
            container.start();
        }
        catch(ContainerLaunchException e)
        {
            // we don't want this test to depend on the plugins actually working (that's outside the scope of
            // the docker tests), so we have to be robust to the container failing to start.
            log.error( String.format("The bundled %s plugin caused Neo4j to fail to start.", pluginName) );
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
                Assertions.assertTrue( plugins.get( 0 ).contains( pluginName ) );
                // Verify from container logs, that the plugins were loaded locally rather than downloaded.
                String logs = container.getLogs( OutputFrame.OutputType.STDOUT);
                String errlogs = container.getLogs( OutputFrame.OutputType.STDERR);
                Assertions.assertTrue(
                        Stream.of(logs.split( "\n" ))
                              .anyMatch( line -> line.matches( "Installing Plugin '" + pluginName + "' from /var/lib/neo4j/.*" ) ),
                        "Plugin was not installed from neo4j home");
//                Assertions.assertFalse(
//                        Stream.of(errlogs.split( "\n" ))
//                              .anyMatch( line -> line.matches( "Failed to read config .+: Unrecognized setting\\..*" ) ),
//                        "An invalid configuration setting was set");
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

    @ParameterizedTest(name = "testBundledPlugin_downloadsIfNotAvailableLocally_{0}")
    @MethodSource("bundledPluginsArgs")
    public void testBundledPlugin_downloadsIfNotAvailableLocally
            (String pluginName, Neo4jVersion bundledSince, Neo4jVersion bundledUntil, boolean isEnterpriseOnly) throws Exception
    {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( bundledSince ),
                                String.format("plugin %s was not bundled in Neo4j %s", pluginName, bundledSince.toString()));
        if(bundledUntil != null) {
            Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( bundledUntil ),
                                    String.format("plugin %s was not bundled after Neo4j %s", pluginName, bundledUntil));
        }
        Assumptions.assumeTrue( isEnterpriseOnly, "Test only applies to enterprise only bundled plugins tested against community edition" );
        Assumptions.assumeTrue( TestSettings.EDITION == TestSettings.Edition.COMMUNITY,
                                "Test only applies to enterprise only bundled plugins tested against community edition" );


        GenericContainer container = null;
        Path pluginsMount = null;
        try
        {
            container = createContainerWithBundledPlugin( pluginName );
            pluginsMount = HostFileSystemOperations
                    .createTempFolderAndMountAsVolume( container,
                                                       "bundled-"+pluginName+"-plugin-unavailable-",
                                                       "/plugins" );
            container.start();
        }
        catch(ContainerLaunchException e)
        {
            // we don't want this test to depend on the plugins actually working (that's outside the scope of
            // the docker tests), so we have to be robust to the container failing to start.
            log.error( String.format("The %s plugin caused Neo4j to fail to start.", pluginName) );
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
                Assertions.assertTrue( plugins.get( 0 ).contains( pluginName ) );
                // Verify from container logs, that the plugins were loaded locally rather than downloaded.
                String logs = container.getLogs( OutputFrame.OutputType.STDOUT);
                String errlogs = container.getLogs( OutputFrame.OutputType.STDERR);
                Assertions.assertTrue(
                        Stream.of(logs.split( "\n" ))
                              .anyMatch( line -> line.matches( "Fetching versions.json for Plugin '" + pluginName + "' from http[s]?://.*" ) ),
                        "Plugin was not installed from cloud");
//                Assertions.assertFalse(
//                        Stream.of(errlogs.split( "\n" ))
//                              .anyMatch( line -> line.matches( "Failed to read config .+: Unrecognized setting\\..*" ) ),
//                        "An invalid configuration setting was set");
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
}
