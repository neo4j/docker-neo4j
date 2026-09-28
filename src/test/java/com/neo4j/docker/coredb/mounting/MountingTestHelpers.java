package com.neo4j.docker.coredb.mounting;

import com.neo4j.docker.utils.SetUserHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;

public class MountingTestHelpers {
    public enum KnownFileOwners {
        ROOT("0:0"),
        NEO4J(SetUserHelper.getNeo4jUserString()),
        CURRENT(SetUserHelper.getNonRootUserString());

        final int userId;
        final int groupId;

        KnownFileOwners(String userGroupId) {
            String[] split = userGroupId.split(":");
            this.userId = Integer.parseInt(split[0]);
            this.groupId = Integer.parseInt(split[1]);
        }
    }

    public static void verifyOwnerIs(Path folderToCheck, KnownFileOwners expectedOwner) throws IOException {
        String folderForDiagnostics = folderToCheck.toAbsolutePath().toString();
        Assertions.assertTrue(
                folderToCheck.toFile().exists(), "did not create " + folderForDiagnostics + " folder on host");

        int fileUid = (Integer) Files.getAttribute(folderToCheck, "unix:uid");
        int fileGid = (Integer) Files.getAttribute(folderToCheck, "unix:gid");
        Assertions.assertEquals(
                expectedOwner.userId,
                fileUid,
                "file %s not owned by %d. Actual owner %d."
                        .formatted(folderForDiagnostics, expectedOwner.userId, fileUid));
        Assertions.assertEquals(
                expectedOwner.groupId,
                fileGid,
                "file %s not owned by group %d. Actual group %d."
                        .formatted(folderForDiagnostics, expectedOwner.groupId, fileGid));
    }

    public static void verifyDataFolderContentsArePresentOnHost(Path dataMount, KnownFileOwners expectedOwner)
            throws IOException {
        verifyOwnerIs(dataMount.resolve("databases"), expectedOwner);
        verifyOwnerIs(dataMount.resolve("transactions"), expectedOwner);
    }

    public static void verifyLogsFolderContentsArePresentOnHost(Path logsMount, KnownFileOwners expectedOwner)
            throws IOException {
        verifyOwnerIs(logsMount, expectedOwner);
        verifyOwnerIs(logsMount.resolve("debug.log"), expectedOwner);
    }
}
