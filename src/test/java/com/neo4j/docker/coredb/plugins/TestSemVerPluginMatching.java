package com.neo4j.docker.coredb.plugins;

import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HttpServerTestExtension;
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
//        SetContainerUser.nonRootUser( container );
        return container;
    }

    @Test
    void testSemanticVersioningLogic() throws Exception
    {
        String major = Integer.toString( NEO4J_VERSION.major );
        String minor = Integer.toString( NEO4J_VERSION.minor );

        // testing common neo4j name variants
        List<String> neo4jVersions = new ArrayList<String>()
        {{
            add( NEO4J_VERSION.toString() );
            add( NEO4J_VERSION.toString() + "-drop01.1" );
            add( NEO4J_VERSION.toString() + "-drop01" );
            add( NEO4J_VERSION.toString() + "-beta04" );
        }};

        List<String> matchingCases = new ArrayList<String>()
        {{
            add( NEO4J_VERSION.toString() );
            add( major + '.' + minor + ".x" );
            add( major + '.' + minor + ".*" );
        }};

        List<String> nonMatchingCases = new ArrayList<String>()
        {{
            add( (NEO4J_VERSION.major + 1) + '.' + minor + ".x" );
            add( (NEO4J_VERSION.major - 1) + '.' + minor + ".x" );
            add( major + '.' + (NEO4J_VERSION.minor + 1) + ".x" );
            add( major + '.' + (NEO4J_VERSION.minor - 1) + ".x" );
            add( (NEO4J_VERSION.major + 1) + '.' + minor + ".*" );
            add( (NEO4J_VERSION.major - 1) + '.' + minor + ".*" );
            add( major + '.' + (NEO4J_VERSION.minor + 1) + ".*" );
            add( major + '.' + (NEO4J_VERSION.minor - 1) + ".*" );
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
            for ( String neoVer : neo4jVersions )
            {
                for ( String ver : matchingCases )
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, ver, neoVer ) );
                    if ( !out.getStdout().trim().equals( "true" ) )
                    {
                        failedTests.add( String.format( "%s should match %s but did not", ver, neoVer ) );
                    }
                }
                for ( String ver : nonMatchingCases )
                {
                    Container.ExecResult out = container.execInContainer( "sh", "-c", String.format( semverQuery, ver, neoVer ) );
                    if ( !out.getStdout().trim().equals( "false" ) )
                    {
                        failedTests.add( String.format( "%s should NOT match %s but did", ver, neoVer ) );
                    }
                }
            }
            if ( failedTests.size() > 0 )
            {
                Assertions.fail( failedTests.stream().collect( Collectors.joining( "\n" ) ) );
            }
        }
    }

    @Test
    void testSemanticVersioningPlugin_catchesMatchWithX() throws Exception
    {
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        stubPluginHelper.createStubPluginForVersion(pluginsDir, NEO4J_VERSION.getBranch() + ".x");
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
        stubPluginHelper.createStubPluginForVersion(pluginsDir, NEO4J_VERSION.getBranch() + ".*");
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
        Path pluginsDir = temporaryFolderManager.createFolder("plugins");
        File versionsJson = stubPluginHelper.createStubPluginsForVersionMapping(pluginsDir,
            new HashMap<String,String>()
            {{
                put( NEO4J_VERSION.toString(), StubPluginHelper.PLUGIN_FILENAME);
                put( NEO4J_VERSION.getBranch() + ".x", "notareal.jar" );
                put( NEO4J_VERSION.major + ".x.x", "notareal.jar" );
            }} );
        try ( GenericContainer container = createContainerWithTestPlugin() )
        {
            container.start();
            DatabaseIO db = new DatabaseIO( container );
            // if semver did not pick exact version match then it will load a non-existent plugin instead and fail.
            stubPluginHelper.verifyStubPluginLoaded( db, DB_USER, DB_PASSWORD );
        }
    }
}
