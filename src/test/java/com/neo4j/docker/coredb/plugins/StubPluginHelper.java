package com.neo4j.docker.coredb.plugins;

import com.google.gson.Gson;
import com.neo4j.docker.utils.DatabaseIO;
import com.neo4j.docker.utils.HostFileHttpHandler;
import com.neo4j.docker.utils.HttpServerTestExtension;
import com.neo4j.docker.utils.Neo4jVersion;
import org.junit.jupiter.api.Assertions;
import org.neo4j.driver.Record;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.neo4j.docker.utils.TestSettings.NEO4J_VERSION;

public class StubPluginHelper
{
    public static final String PLUGIN_FILENAME = "myPlugin.jar";
    public static final String PLUGIN_ENV_NAME = "_testing";
    private final HttpServerTestExtension httpServer;

    /**Data class for each <code>versions.json</code> entry so that the GSON tool can convert it to json.
     * */
    private class VersionsJsonEntry
    {
        String neo4j;
        String jar;
        String _testing;

        VersionsJsonEntry( String neo4j, String jar )
        {
            this.neo4j = neo4j;
            this._testing = "SNAPSHOT";
            this.jar = "http://host.testcontainers.internal:3000/" + jar;
        }
    }

    /**Does (most of) the complicated setup required to create a fake neo4j plugin and make it accessible inside the container.
     * @param httpServer a {@link HttpServerTestExtension} object.
     *                  It must have ALREADY been registered as a JUnit5 extension with {@link org.junit.jupiter.api.extension.RegisterExtension}
     * */
    public StubPluginHelper(HttpServerTestExtension httpServer)
    {
        this.httpServer = httpServer;
    }

    /**Creates a versions.json in the destination folder mapping between the given neo4j versions and jar filenames.
     * This is currently used to map between neo4j versions and jars that don't exist (for semver match testing).
     * @param destinationFolder folder to save versions.json to.
     * @param version a neo4j version, to map to the real testing jar.
     * @return File object of the versions.json file created.
     * */
    public File createStubPluginForVersion(Path destinationFolder, Neo4jVersion version) throws IOException
    {
        return createStubPluginsForVersionMapping(destinationFolder, Collections.singletonMap(version.toString(), PLUGIN_FILENAME));
    }

    /**Creates a versions.json in the destination folder mapping between the given neo4j versions and jar filenames.
     * This is currently used to map between neo4j versions and jars that don't exist (for semver match testing).
     * @param destinationFolder folder to save versions.json to.
     * @param version a neo4j version, as a string, to map to the real testing jar.
     * @return File object of the versions.json file created.
     * */
    public File createStubPluginForVersion(Path destinationFolder, String version) throws IOException
    {
        return createStubPluginsForVersionMapping(destinationFolder, Collections.singletonMap(version, PLUGIN_FILENAME));
    }

    /**Creates a versions.json in the destination folder mapping between the given neo4j versions and jar filenames.
     * This is currently used to map between neo4j versions and jars that don't exist (for semver match testing).
     * @param destinationFolder folder to save versions.json to.
     * @param versionAndJar map of neo4j version, as a string, to jar name. For example:
     *                      4.4.10 -> myPlugin.jar
     *                      4.4.*  -> pluginThatDoesNotExist.jar
     *                      5.0.x  -> anotherNonexistantPlugin.jar
     * @return File object of the versions.json file created.
     * */
    public File createStubPluginsForVersionMapping(Path destinationFolder, Map<String,String> versionAndJar ) throws IOException
    {
        File versionsJson = createVersionsJson(destinationFolder, versionAndJar);
        try {
            File myPluginJar = new File(getClass().getClassLoader().getResource("stubplugin/" + PLUGIN_FILENAME).toURI());

            httpServer.registerHandler(versionsJson.getName(), new HostFileHttpHandler(versionsJson, "application/json"));
            httpServer.registerHandler(PLUGIN_FILENAME, new HostFileHttpHandler(myPluginJar, "application/java-archive"));
        }
        catch (URISyntaxException e)
        {
            throw new IOException("Could not load test plugin from test resources file", e);
        }
        return versionsJson;
    }

    private File createVersionsJson(Path destinationFolder, Map<String, String> versionAndJar) throws IOException
    {
        List<VersionsJsonEntry> jsonEntries = versionAndJar.keySet()
                .stream()
                .map(key -> new VersionsJsonEntry(key, versionAndJar.get(key)))
                .collect(Collectors.toList());
        Gson jsonBuilder = new Gson();
        String jsonStr = jsonBuilder.toJson(jsonEntries);

        File outputJsonFile = destinationFolder.resolve("versions.json").toFile();
        java.nio.file.Files.writeString(outputJsonFile.toPath(), jsonStr);
        return outputJsonFile;
    }

    public void verifyStubPluginLoaded(DatabaseIO db, String user, String password )
    {
        // when we check the list of installed procedures...
        String listProceduresCypherQuery = NEO4J_VERSION.isAtLeastVersion( new Neo4jVersion( 4, 3, 0 ) ) ?
                                           "SHOW PROCEDURES YIELD name, signature RETURN name, signature" :
                                           "CALL dbms.procedures() YIELD name, signature RETURN name, signature";
        List<Record> procedures = db.runCypherQuery( user, password, listProceduresCypherQuery );
        // Then the procedure from the test plugin should be listed
        Assertions.assertTrue( procedures.stream()
                                         .anyMatch( x -> x.get( "name" ).asString()
                                                          .equals( "com.neo4j.docker.test.myplugin.defaultValues" ) ),
                               "Missing procedure provided by our plugin" );

        // When we call the procedure from the plugin
        List<Record> pluginResponse = db.runCypherQuery( user, password,
                                                         "CALL com.neo4j.docker.test.myplugin.defaultValues" );

        // Then we get the response we expect
        Assertions.assertEquals( 1, pluginResponse.size(), "Our procedure should only return a single result" );
        Record record = pluginResponse.get( 0 );

        String message = "Result from calling our procedure doesnt match our expectations";
        Assertions.assertEquals( "a string", record.get( "string" ).asString(), message );
        Assertions.assertEquals( 42L, record.get( "integer" ).asInt(), message );
        Assertions.assertEquals( 3.14d, record.get( "aFloat" ).asDouble(), 0.000001, message );
        Assertions.assertTrue(record.get("aBoolean").asBoolean(), message);
    }
}
