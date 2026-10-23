package com.neo4j.docker.neo4jadmin;

import com.neo4j.docker.utils.BaseOS;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.TrustStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestAdminTrustStore {

    @Test
    void testCompromisedEmudhraRootsAreNotTrusted() {
        Assumptions.assumeTrue(
                TestSettings.BASE_OS == BaseOS.TRIXIE || TestSettings.BASE_OS == BaseOS.BULLSEYE,
                "UBI images use the Red Hat system trust store, which is fixed through ca-certificates updates");
        TrustStore.assertNoCompromisedEmudhraRoots(TestSettings.ADMIN_IMAGE_ID);
    }

    @Test
    void testTrustStoreIsPopulated() {
        TrustStore.assertTrustStoreIsPopulated(TestSettings.ADMIN_IMAGE_ID);
    }
}
