package com.neo4j.docker.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BaseOSTest {
    @ParameterizedTest
    @ValueSource(strings = {"trixie", "bullseye", "ubi10", "ubi9", "ubi8"})
    void testFromString(String name) {
        BaseOS os = BaseOS.fromString(name);
        Assertions.assertNotNull( os );
        Assertions.assertEquals( name, os.osName, "Did not get the correct BaseOS from name " + name );
    }

    @ParameterizedTest
    @EnumSource(BaseOS.class)
    void testDeprecatedInVersionsSet(BaseOS os) {
        if(os.isDeprecated()) {
            Assertions.assertNotNull( os.lastAppearsIn5x, "5x last appearance unset for "+os.name() );
            Assertions.assertNotNull( os.lastAppearsInCalver, "Calver last appearance unset for "+os.name() );
        }
        else {
            Assertions.assertNull( os.lastAppearsIn5x, "5x last appearance set for "+os.name() );
            Assertions.assertNull( os.lastAppearsInCalver, "Calver last appearance set for "+os.name() );
        }
    }

    @ParameterizedTest(name = "{0}, {1}.{2}.{3}")
    @CsvSource({"ubi8,2023,12,0", "ubi8,5,19,0",
                "ubi9,2026,2,0", "ubi9,5,26,20",
                "bullseye,2026,2,0", "bullseye,5,26,20"})
    void testHasDeprecationWarning_before(String name, int major, int minor, int patch) {
        BaseOS os = BaseOS.fromString(name);
        Assertions.assertNotNull( os );
        Neo4jVersion compareVersion = new Neo4jVersion(major, minor, patch);
        Assertions.assertTrue( os.hasDeprecationWarningIn( compareVersion ),
                               "Should have flagged version %s as having a deprecation warning for  %s"
                                       .formatted( compareVersion, name ));
    }

    @ParameterizedTest(name = "{0}, {1}.{2}.{3}")
    @CsvSource({"ubi8,2024,1,0", "ubi8,5,20,0",
                "ubi9,2026,3,0", "ubi9,5,26,21",
                "bullseye,2026,3,0", "bullseye,5,26,21"})
    void testHasDeprecationWarning_equal(String name, int major, int minor, int patch) {
        BaseOS os = BaseOS.fromString(name);
        Assertions.assertNotNull( os );
        Neo4jVersion compareVersion = new Neo4jVersion(major, minor, patch);
        Assertions.assertTrue( os.hasDeprecationWarningIn( compareVersion ),
                               "Should have flagged version %s as having a deprecation warning for %s"
                                       .formatted( compareVersion, name ));
    }

    @ParameterizedTest(name = "{0}, {1}.{2}.{3}")
    @CsvSource({"ubi8,2027,1,0", "ubi8,5,26,0",
                "ubi9,2026,4,0", "ubi9,5,26,50",
                "bullseye,2026,4,0", "bullseye,5,26,50"})
    void testHasDeprecationWarning_after(String name, int major, int minor, int patch) {
        BaseOS os = BaseOS.fromString(name);
        Assertions.assertNotNull( os );
        Neo4jVersion compareVersion = new Neo4jVersion(major, minor, patch);
        Assertions.assertFalse( os.hasDeprecationWarningIn( compareVersion ),
                               "Should not have flagged version %s as having a deprecation warning for %s"
                                       .formatted( compareVersion, name ));
    }

    @ParameterizedTest(name = "{0}, {1}.{2}.{3}")
    @CsvSource({"ubi10,2027,1,0", "ubi10,5,26,0",
                "trixie,2024,4,0", "trixie,5,26,50"})
    void testHasDeprecationWarning_undeprecated(String name, int major, int minor, int patch) {
        BaseOS os = BaseOS.fromString(name);
        Assertions.assertNotNull( os );
        Neo4jVersion compareVersion = new Neo4jVersion(major, minor, patch);
        Assertions.assertFalse( os.hasDeprecationWarningIn( compareVersion ),
                               "Should not have flagged version %s as having a deprecation warning for %s"
                                       .formatted( compareVersion, name ));
    }

    @ParameterizedTest(name = "{0}, {1}.{2}.{3}")
    @CsvSource({"ubi8,4,1,0", "ubi8,3,2,0", "ubi8,1,10,0", "ubi8,4,4,50",
                "ubi9,4,1,0", "ubi9,3,2,0", "ubi9,1,10,0", "ubi9,4,4,50",
                "ubi10,4,1,0", "ubi10,3,2,0", "ubi10,1,10,0", "ubi10,4,4,50"})
    void testHasDeprecationWarning_oldVersionsNoWarning(String name, int major, int minor, int patch) {
        BaseOS os = BaseOS.fromString(name);
        Assertions.assertNotNull( os );
        Neo4jVersion compareVersion = new Neo4jVersion(major, minor, patch);
        Assertions.assertFalse( os.hasDeprecationWarningIn( compareVersion ),
                               "Should not have flagged version %s as having a deprecation warning for %s"
                                       .formatted( compareVersion, name ));
    }
}
