package com.neo4j.docker.coredb;

import com.neo4j.docker.utils.BaseOS;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.TrustStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestTrustStore {

    @Test
    void testCompromisedEmudhraRootsAreNotTrusted() {
        Assumptions.assumeTrue(
                TestSettings.BASE_OS == BaseOS.TRIXIE || TestSettings.BASE_OS == BaseOS.BULLSEYE,
                "UBI images use the Red Hat system trust store, which is fixed through ca-certificates updates");
        TrustStore.assertNoCompromisedEmudhraRoots(TestSettings.IMAGE_ID);
    }

    @Test
    void testTrustStoreIsPopulated() {
        TrustStore.assertTrustStoreIsPopulated(TestSettings.IMAGE_ID);
    }
}
