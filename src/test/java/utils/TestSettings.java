package utils;

import java.nio.file.Path;
import java.nio.file.Paths;

// TODO find way to pass these as test settings on command line or as environment (or both?)
// TODO fail test immediately and error if these values aren't set.
public class TestSettings
{
//    public static final Neo4jVersion NEO4J_VERSION = Neo4jVersion.fromVersionString( System.getenv( "NEO4J_VERSION" ) );
    public static final Neo4jVersion NEO4J_VERSION = Neo4jVersion.fromVersionString( "4.0.0-alpha07" );
    public static final String IMAGE_ID = "test/27608:latest";
    public static final Path TEST_TMP_FOLDER = Paths.get("tmp", "local-mounts");
//    public static final Path TEST_TMP_FOLDER = Paths.get("/","tmp");
    public static final Edition EDITION = Edition.ENTERPRISE;

    public enum Edition
    {
        COMMUNITY,
        ENTERPRISE;
    }
}
