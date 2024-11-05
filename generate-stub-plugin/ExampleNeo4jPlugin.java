package com.neo4j.docker.test.myplugin;

import java.util.stream.Stream;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

/*
This class is a basic Neo4J plugin that defines a procedure which can be called via Cypher.
 */
public class ExampleNeo4jPlugin
{
    // Output data class containing primitive types
    public static class PrimitiveOutput
    {
        public String string;
        public long integer;
        public double aFloat;
        public boolean aBoolean;

        public PrimitiveOutput( String string, long integer, double aFloat, boolean aBoolean )
        {
            this.string = string;
            this.integer = integer;
            this.aFloat = aFloat;
            this.aBoolean = aBoolean;
        }
    }
//    @ServiceProvider
//    public static class ExampleConfigurationSetting implements SettingsDeclaration
//    {
//        public static final String CONF_NAME = "com.neo4j.docker.neo4jserver.plugins.loaded_verison";
//
//        @Description("Unique setting to identify which semver field was matched")
//        public static final Setting<String> loadedVersionValue = SettingImpl.newBuilder(
//                CONF_NAME,
//                SettingValueParsers.STRING,
//                "unset"
//        ).build();
//    }

    @Context
    public GraphDatabaseService db;

    @Context
    public Log log;

    // A Neo4j procedure that always returns fixed values
    @Procedure
    public Stream<PrimitiveOutput> defaultValues( @Name( value = "string", defaultValue = "a string" ) String string,
                                                  @Name( value = "integer", defaultValue = "42" ) long integer,
                                                  @Name( value = "float", defaultValue = "3.14" ) double aFloat,
                                                  @Name( value = "boolean", defaultValue = "true" ) boolean aBoolean )
    {
        return Stream.of( new PrimitiveOutput( string, integer, aFloat, aBoolean ) );
    }
}