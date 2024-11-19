package com.neo4j.docker.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Neo4jVersionTest {

    @Test
    public void testIsOlderThan_majorDifferent()
    {
        Neo4jVersion newer = new Neo4jVersion( 3, 5, 1 );
        Neo4jVersion older = new Neo4jVersion( 2, 0, 0 );
        testOlderVsNewer( newer, older );
    }

    @Test
    public void testIsOlderThan_minorDifferent()
    {
        Neo4jVersion newer = new Neo4jVersion( 3, 5, 1 );
        Neo4jVersion older = new Neo4jVersion( 3, 0, 0 );
        testOlderVsNewer( newer, older );
    }

    @Test
    public void testIsOlderThan_patchDifferent()
    {
        Neo4jVersion newer = new Neo4jVersion( 3, 5, 1 );
        Neo4jVersion older = new Neo4jVersion( 3, 5, 0 );
        testOlderVsNewer( newer, older );
    }

    @Test
    public void testIsOlderThan_majorLessMinorMore()
    {
        Neo4jVersion newer = new Neo4jVersion( 3, 5, 1 );
        Neo4jVersion older = new Neo4jVersion( 2, 7, 0 );
        testOlderVsNewer( newer, older );
    }

    @Test
    public void testIsOlderThan_minorLessPatchMore()
    {
        Neo4jVersion newer = new Neo4jVersion( 3, 5, 0 );
        Neo4jVersion older = new Neo4jVersion( 3, 2, 12 );
        testOlderVsNewer( newer, older );
    }

    @Test
    public void testSamePatch_isEqualAndAtLeast()
    {
        Neo4jVersion newer = new Neo4jVersion( 3, 5, 0 );
        Neo4jVersion older = new Neo4jVersion( 3, 5, 0 );

        Assertions.assertFalse( newer.isOlderThan( older ) ,
                                String.format( "Didn't detect that %s is newer than %s using isOlderThan", newer, older ) );
        Assertions.assertFalse( older.isNewerThan( newer ) ,
                     String.format( "Didn't detect that %s is older than %s using isNewerThan", older, newer ) );
        Assertions.assertTrue( newer.isAtLeastVersion( older ),
                    String.format( "Didn't detect that %s is at least %s using isAtLeastVersion", newer, older ) );
        Assertions.assertTrue( newer.isEqual(older),
                    String.format( "Didn't detect that %s is equal to %s using isEqual", newer, older ) );
    }

    @Test
    public void testSamePatch_isEqualAndAtLeast_differentBuild()
    {
        Neo4jVersion newer = new Neo4jVersion( 3, 5, 0, "-11" );
        Neo4jVersion older = new Neo4jVersion( 3, 5, 0, "-10" );

        Assertions.assertFalse( newer.isOlderThan( older ) ,
                     String.format( "Didn't detect that %s is newer than %s using isOlderThan", newer, older ) );
        Assertions.assertFalse( older.isNewerThan( newer ) ,
                     String.format( "Didn't detect that %s is older than %s using isNewerThan", older, newer ) );
        Assertions.assertTrue( newer.isAtLeastVersion( older ),
                    String.format( "Didn't detect that %s is at least %s using isAtLeastVersion", newer, older ) );
        Assertions.assertTrue( newer.isEqual(older),
                    String.format( "Didn't detect that %s is equal to %s using isEqual", newer, older ) );
    }

    private void testOlderVsNewer( Neo4jVersion newer, Neo4jVersion older )
    {
        // isOlderThan
        Assertions.assertTrue( older.isOlderThan( newer ) ,
                    String.format( "Didn't detect that %s is older than %s using isOlderThan", older, newer ) );
        Assertions.assertFalse( newer.isOlderThan( older ) ,
                     String.format( "Didn't detect that %s is newer than %s using isOlderThan", newer, older ) );

        // isNewerThan
        Assertions.assertTrue( newer.isNewerThan( older ),
                    String.format( "Didn't detect that %s is newer than %s using isNewerThan", newer, older ) );
        Assertions.assertFalse( older.isNewerThan( newer ) ,
                     String.format( "Didn't detect that %s is older than %s using isNewerThan", older, newer ) );

        // isAtLeastVersion
        Assertions.assertTrue( newer.isAtLeastVersion( older ),
                    String.format( "Didn't detect that %s is newer than %s using isAtLeastVersion", newer, older ) );
        Assertions.assertFalse( older.isAtLeastVersion( newer ) ,
                     String.format( "Didn't detect that %s is older than %s using isAtLeastVersion", older, newer ) );

        Assertions.assertFalse( newer.isEqual(older),
                                String.format( "%s incorrectly is equal to %s", newer, older ) );
        Assertions.assertNotEquals( newer, older, String.format( "%s incorrectly is equal to %s", newer, older ) );
    }

    @Test
    public void testIsOlderThan_sameReleaseReturnsFalse()
    {
        Neo4jVersion version = new Neo4jVersion( 3, 5, 1 );

        Assertions.assertFalse( version.isOlderThan( version ) ,
                     "A release should not be older than itself" );
        Assertions.assertFalse( version.isNewerThan( version ) ,
                     "A release should not be newer than itself" );
    }

    @Test
    public void testFromVersionString_releaseFormat()
    {
        Neo4jVersion version = Neo4jVersion.fromVersionString( "4.4.7" );
        Assertions.assertEquals( 4, version.major, "Did not parse major number from " + version );
        Assertions.assertEquals( 4, version.minor, "Did not parse minor number from " + version );
        Assertions.assertEquals( 7, version.patch, "Did not parse patch number from " + version );
    }

    @Test
    public void testFromVersionString_BSPFormat()
    {
        Neo4jVersion version = Neo4jVersion.fromVersionString( "4.4.7-12345" );
        Assertions.assertEquals( 4, version.major, "Did not parse major number from " + version );
        Assertions.assertEquals( 4, version.minor, "Did not parse minor number from " + version );
        Assertions.assertEquals( 7, version.patch, "Did not parse patch number from " + version );
        Assertions.assertEquals( "-12345", version.label, "Did not build number from " + version );
    }

    @Test
    public void testFromVersionString_calver_releaseFormat()
    {
        Neo4jVersion version = Neo4jVersion.fromVersionString( "2024.10.0" );
        Assertions.assertEquals( 2024, version.major, "Did not parse major number from " + version );
        Assertions.assertEquals( 10, version.minor, "Did not parse minor number from " + version );
        Assertions.assertEquals( 0, version.patch, "Did not parse patch number from " + version );
    }

    @Test
    public void testFromVersionString_calver_BSPFormat()
    {
        Neo4jVersion version = Neo4jVersion.fromVersionString( "2024.10.0-1234" );
        Assertions.assertEquals( 2024, version.major, "Did not parse major number from " + version );
        Assertions.assertEquals( 10, version.minor, "Did not parse minor number from " + version );
        Assertions.assertEquals( 0, version.patch, "Did not parse patch number from " + version );
        Assertions.assertEquals( "-1234", version.label, "Did not build number from " + version );
    }

    @Test
    public void testFromVersionString_calver_OneMonthDigit()
    {
        // an incorrect format, but would be useful if it was handled correctly
        Neo4jVersion version = Neo4jVersion.fromVersionString( "2027.1.5-99" );
        Assertions.assertEquals( 2027, version.major, "Did not parse major number from " + version );
        Assertions.assertEquals( 1, version.minor, "Did not parse minor number from " + version );
        Assertions.assertEquals( 5, version.patch, "Did not parse patch number from " + version );
        Assertions.assertEquals( "-99", version.label, "Did not build number from " + version );
    }

    @Test
    void testToStringCalVer()
    {
        Neo4jVersion version = Neo4jVersion.fromVersionString( "2025.2.0-1234" );
        String outStr = version.toString();
        Assertions.assertEquals( "2025.02.0-1234", outStr );
    }
}
