package com.neo4j.docker.coredb.plugins;

import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;

public class Neo4jPluginEnv
{
    public static final String PLUGIN_ENV_4X = "NEO4JLABS_PLUGINS";
    public static final String PLUGIN_ENV_5X = "NEO4J_PLUGINS";

    public static String get( )
    {
        return get(TestSettings.NEO4J_VERSION);
    }

    public static String get( Neo4jVersion version )
    {
        if( version.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ) )
        {
            return PLUGIN_ENV_5X;
        }
        else return PLUGIN_ENV_4X;
    }
}
