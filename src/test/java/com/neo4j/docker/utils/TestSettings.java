package com.neo4j.docker.utils;

import org.junit.jupiter.api.Assertions;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestSettings
{
    public static final Neo4jVersion NEO4J_VERSION = getVersion();
    public static final DockerImageName IMAGE_ID = getImage();
    public static final DockerImageName ADMIN_IMAGE_ID = getNeo4jAdminImage();
    public static final Path TEST_TMP_FOLDER = Paths.get("local-mounts" );
    public static final Edition EDITION = getEdition();
    public static final BaseOS BASE_OS = getBaseOS();
    public static final boolean SKIP_MOUNTED_FOLDER_TARBALLING = getSkipTarballingFromEnv();

    public enum Edition
    {
        COMMUNITY,
        ENTERPRISE;
    }
    public enum BaseOS
    {
        BULLSEYE,
        UBI9,
        UBI8;
    }

    private static String getValueFromPropertyOrEnv(String propertyName, String envName)
    {
        String verStr = System.getProperty( propertyName );
        if(verStr == null)
        {
            verStr = System.getenv( envName );
        }
        Assertions.assertNotNull( String.format( "Neo4j %s has not been specified. " +
                                                 "Either use mvn argument -D%s or set env %s",
                                                 propertyName, propertyName, envName),
                                  verStr);
        return verStr;
    }

    private static Neo4jVersion getVersion()
    {
        return Neo4jVersion.fromVersionString( getValueFromPropertyOrEnv( "version", "NEO4JVERSION" ));
    }

    private static DockerImageName getImage()
    {
        return DockerImageName.parse(getValueFromPropertyOrEnv("image", "NEO4J_IMAGE"));
    }

    private static DockerImageName getNeo4jAdminImage()
    {
        return DockerImageName.parse(getValueFromPropertyOrEnv("adminimage", "NEO4JADMIN_IMAGE"));
    }

    private static Edition getEdition()
    {
        String edition = getValueFromPropertyOrEnv("edition", "NEO4J_EDITION");
        switch ( edition.toLowerCase() )
        {
        case "community":
            return Edition.COMMUNITY;
        case "enterprise":
            return Edition.ENTERPRISE;
        default:
            Assertions.fail( edition + " is not a valid Neo4j edition. Options are \"community\" or \"enterprise\"." );
        }
        return null;
    }

    private static BaseOS getBaseOS()
    {
        String os = getValueFromPropertyOrEnv("baseos", "BASE_OS");
        switch ( os.toLowerCase() )
        {
        case "debian":
            return BaseOS.BULLSEYE;
        case "ubi9":
            return BaseOS.UBI9;
        case "ubi8":
            return BaseOS.UBI8;
        default:
            Assertions.fail( os + " is not a valid Neo4j base operating system. Options are \"debian\", \"ubi9\" or \"ubi8\"." );
        }
        return null;
    }

    private static boolean getSkipTarballingFromEnv()
    {
        // defaults to false. Tarballing test artifacts must be opt-out not opt-in.
        String skipTar = System.getenv( "NEO4J_SKIP_MOUNTED_FOLDER_TARBALLING" );
        if(skipTar == null)  return false;
        else return (skipTar.equals( "true" ));
    }
}
