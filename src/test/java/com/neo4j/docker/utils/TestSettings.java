package com.neo4j.docker.utils;

import org.junit.Assert;
import org.junit.runners.Parameterized;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestSettings
{
    public static final Neo4jVersion NEO4J_VERSION = Neo4jVersion.fromVersionString( getVersionFromPropertyOrEnv() );
    public static final DockerImageName IMAGE_ID = getImageFromPropertyOrEnv();
    public static final DockerImageName ADMIN_IMAGE_ID = getNeo4jAdminImageFromPropertyOrEnv();
    public static final Path TEST_TMP_FOLDER = Paths.get("local-mounts" );
    public static final Edition EDITION = getEditionFromPropertyOrEnv();

    public enum Edition
    {
        COMMUNITY,
        ENTERPRISE;
    }

    private static String getVersionFromPropertyOrEnv()
    {
        String verStr = System.getProperty( "version" );
        if(verStr == null)
        {
            verStr = System.getenv( "NEO4JVERSION" );
        }
        Assert.assertNotNull("Neo4j version has not been specified, either use mvn argument -Dversion or set env NEO4JVERSION", verStr);
        return verStr;
    }

    private static DockerImageName getImageFromPropertyOrEnv()
    {
        String image = System.getProperty( "image" );
        if(image == null)
        {
            image = System.getenv( "NEO4J_IMAGE" );
        }
        Assert.assertNotNull("Neo4j image has not been specified, either use mvn argument -Dimage or set env NEO4J_IMAGE", image);
        return DockerImageName.parse(image);
    }

    private static DockerImageName getNeo4jAdminImageFromPropertyOrEnv()
    {
        String image = System.getProperty( "adminimage" );
        if(image == null)
        {
            image = System.getenv( "NEO4JADMIN_IMAGE" );
        }
        Assert.assertNotNull("Neo4j image has not been specified, either use mvn argument -Dadminimage or set env NEO4JADMIN_IMAGE", image);
        return DockerImageName.parse(image);
    }

    private static Edition getEditionFromPropertyOrEnv()
    {
        String edition = System.getProperty( "edition" );
        if(edition == null)
        {
            edition = System.getenv( "NEO4J_EDITION" );
        }
        switch ( edition.toLowerCase() )
        {
        case "community":
            return Edition.COMMUNITY;
        case "enterprise":
            return Edition.ENTERPRISE;
        default:
            Assert.fail( "Neo4j edition has not been specified, either use mvn argument -Dedition or set env NEO4J_EDITION" );
        }
        return null;
    }
}
