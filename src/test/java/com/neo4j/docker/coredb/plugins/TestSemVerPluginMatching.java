package com.neo4j.docker.coredb.plugins;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HttpServerTestExtension;
import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TemporaryFolderManager;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.neo4j.docker.utils.TestSettings.NEO4J_VERSION;

public class TestSemVerPluginMatching
{
    private static final String DB_USER = "neo4j";
    private static final String DB_PASSWORD = "qualityPassword123";
    private final Logger log = LoggerFactory.getLogger(TestSemVerPluginMatching.class);

    @RegisterExtension
    public static TemporaryFolderManager temporaryFolderManager = new TemporaryFolderManager();
    @RegisterExtension
    public HttpServerTestExtension httpServer = new HttpServerTestExtension();
    StubPluginHelper stubPluginHelper = new StubPluginHelper(httpServer);


    private GenericContainer<?> createContainerWithTestPlugin()
    {
        Testcontainers.exposeHostPorts( httpServer.PORT );
        GenericContainer<?> container = new GenericContainer<>( TestSettings.IMAGE_ID );

        container.withEnv( "NEO4J_AUTH", DB_USER + "/" + DB_PASSWORD )
                .withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                .withEnv( "NEO4J_DEBUG", "yes" )
                .withEnv( Neo4jPluginEnv.get(), "[\"" + StubPluginHelper.PLUGIN_ENV_NAME + "\"]" )
                .withExposedPorts( 7474, 7687 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .waitingFor( WaitStrategies.waitForNeo4jReady(DB_PASSWORD));
        return container;
    }

    @Test
    void testSemanticVersioningLogic() throws Exception
    {
        // testing common neo4j name variants
        List<String> neo4jVersions = new ArrayList<String>()
        {{
            add( NEO4J_VERSION.toReleaseString() );
            add( NEO4J_VERSION.toReleaseString() + "-12345" );
        }};

        List<String> matchingCases = new ArrayList<String>()
        {{
            add( NEO4J_VERSION.toReleaseString() );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor)+".x" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor)+".*" );
            add( NEO4J_VERSION.major + ".x.x" );
            add( NEO4J_VERSION.major + ".*.*" );
            add( "x.x.x" );
            add( "*.*.*" );
        }};

        List<String> nonMatchingCases = new ArrayList<String>()
        {{
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major+1, NEO4J_VERSION.minor)+".x" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major-1, NEO4J_VERSION.minor)+".x" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor+1)+".x" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor-1)+".x" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major+1, NEO4J_VERSION.minor)+".*" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major-1, NEO4J_VERSION.minor)+".*" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor+1)+".*" );
            add( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor-1)+".*" );
        }};

        // Asserting every test case means that if there's a failure, all further tests won't run.
        // Instead we're running all tests and saving any failed cases for reporting at the end of the test.
        List<String> failedTests = new ArrayList<String>();

        try ( GenericContainer container = createContainerWithTestPlugin() )
        {
            container.withEnv( Neo4jPluginEnv.get(), "" ); // don't need the _testing plugin for this
            container.start();

            String semverQuery = "echo \"{\\\"neo4j\\\":\\\"%s\\\"}\" | " +
                    "jq -L/startup --raw-output \"import \\\"semver\\\" as lib; " +
                    ".neo4j | lib::semver(\\\"%s\\\")\"";
            for ( String verToBeMatched : neo4jVersions )
            {
                for ( String verRegex : matchingCases )
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, verRegex, verToBeMatched ) );
                    if ( !out.getStdout().trim().equals( "true" ) )
                    {
                        failedTests.add( String.format( "%s should match %s but did not", verRegex, verToBeMatched ) );
                    }
                }
                for ( String verRegex : nonMatchingCases )
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, verRegex, verToBeMatched ) );
                    if ( !out.getStdout().trim().equals( "false" ) )
                    {
                        failedTests.add( String.format( "%s should NOT match %s but did", verRegex, verToBeMatched ) );
                    }
                }
            }
            if ( !failedTests.isEmpty() )
            {
                Assertions.fail( failedTests.stream().collect( Collectors.joining( "\n" ) ) );
            }
        }
    }

    @Test
    void testSemanticVersioningPlugin_catchesMatchWithX() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginForVersion(pluginsDir,
            Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor)+".x");
        try ( GenericContainer container = createContainerWithTestPlugin() )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
        }
    }

    @Test
    void testSemanticVersioningPlugin_catchesMatchWithStar() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginForVersion(pluginsDir,
            Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor)+".*");
        try ( GenericContainer container = createContainerWithTestPlugin() )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
        }
    }

    @Test
    void testSemanticVersioningPlugin_prefersExactMatch() throws Exception
    {
        verifySemanticVersioningPrefersBetterMatches(new HashMap<String,String>()
            {{
                put( "x.x.x", "notareal.jar" );
                put( NEO4J_VERSION.major + ".x.x", "notareal.jar" );
                put( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor) + ".x", "notareal.jar" );
                put( NEO4J_VERSION.toString(), StubPluginHelper.PLUGIN_FILENAME);
            }} );
    }

    @Test
    void testSemanticVersioningPlugin_prefersMajorMinorMatch() throws Exception
    {
        verifySemanticVersioningPrefersBetterMatches(new HashMap<String,String>()
            {{
                put( "x.x.x", "notareal.jar" );
                put( NEO4J_VERSION.major + ".x.x", "notareal.jar" );
                put( Neo4jVersion.makeVersionString( NEO4J_VERSION.major, NEO4J_VERSION.minor) + ".x",
                     StubPluginHelper.PLUGIN_FILENAME);
            }} );
    }

    @Test
    void testSemanticVersioningPlugin_prefersMajorMatch() throws Exception
    {
        verifySemanticVersioningPrefersBetterMatches(new HashMap<String,String>()
            {{
                put( "x.x.x", "notareal.jar" );
                put( NEO4J_VERSION.major + ".x.x", StubPluginHelper.PLUGIN_FILENAME);
            }} );
    }

    void verifySemanticVersioningPrefersBetterMatches(Map<String, String> versionsInJson) throws Exception {
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginsForVersionMapping(pluginsDir, versionsInJson);
        try (GenericContainer container = createContainerWithTestPlugin()) {
            container.start();
            DatabaseIO db = new DatabaseIO(container);
            // if semver did not pick exact version match then it will load a non-existent plugin instead and fail.
            stubPluginHelper.verifyStubPluginLoaded(db, DB_USER, DB_PASSWORD);
        }
    }
}
