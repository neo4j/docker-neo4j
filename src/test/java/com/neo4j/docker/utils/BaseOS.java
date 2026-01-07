package com.neo4j.docker.utils;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

public enum BaseOS {
    // debian
    TRIXIE("trixie", null, null),
    BULLSEYE("bullseye", null, new Neo4jVersion(5, 26, 21)),
    // redhat
    UBI10("ubi10", null, null),
    UBI9("ubi9", null, new Neo4jVersion(5, 26, 21)),
    UBI8("ubi8", new Neo4jVersion(2024, 1, 0), new Neo4jVersion(5, 20, 0)),
    ;

    public final String osName;
    public final Neo4jVersion lastAppearsInCalver;
    public final Neo4jVersion lastAppearsIn5x;

    BaseOS(String name, @Nullable Neo4jVersion lastAppearsInCalver, @Nullable Neo4jVersion lastAppearsIn5x) {
        this.osName = name;
        this.lastAppearsInCalver = lastAppearsInCalver;
        this.lastAppearsIn5x = lastAppearsIn5x;
    }

    public boolean hasDeprecationWarningIn(Neo4jVersion other) {
        Neo4jVersion deprecatedIn;
        switch(other.major) {
            case 4,3,2,1 -> deprecatedIn = null;
            case 5 -> deprecatedIn = this.lastAppearsIn5x;
            default -> deprecatedIn = this.lastAppearsInCalver;
        }
        if(deprecatedIn == null) return false;
        return !other.isNewerThan( deprecatedIn );
    }

    public static BaseOS fromString(String name) {
        switch (name.toLowerCase()) {
            case "debian", "trixie":
                return BaseOS.TRIXIE;
            case "bullseye":
                return BaseOS.BULLSEYE;
            case "ubi10":
                return BaseOS.UBI10;
            case "ubi9":
                return BaseOS.UBI9;
            case "ubi8":
                return BaseOS.UBI8;
            default:
                Assertions.fail(name + " is not a valid Neo4j base operating system.");
        }
        return null;
    }
}
