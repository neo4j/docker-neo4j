package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.sun.security.auth.module.UnixSystem;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

public class SetUserHelper {

    public static void containerAsNonRootUser(GenericContainer container) {
        container.withCreateContainerCmdModifier(
                (Consumer<CreateContainerCmd>) cmd -> cmd.withUser(getNonRootUserString()));
    }

    public static String getNonRootUserString() {
        // check if the non root user environment variable is set, if so use that. Otherwise use current user.
        String user = System.getenv("NON_ROOT_USER_ID");
        if (user == null) {
            return getCurrentlyRunningUser();
        } else {
            return user;
        }
    }

    public static String getNeo4jUserString() {
        return "7474:7474";
    }

    private static String getCurrentlyRunningUser() {
        UnixSystem fs = new UnixSystem();
        return fs.getUid() + ":" + fs.getGid();
    }

    public static void setFolderOwnerToCurrentUser(Path... files) throws Exception {
        setFolderOwnerTo(SetUserHelper.getNonRootUserString(), files);
    }

    public static void setFolderOwnerToNeo4j(Path... files) throws Exception {
        setFolderOwnerTo(SetUserHelper.getNeo4jUserString(), files);
    }

    private static void setFolderOwnerTo(String userAndGroup, Path... files) throws Exception {
        // uses docker privileges to set file owner, since probably the current user is not a sudoer.

        // Using nginx because it's easy to verify that the image started.
        try (GenericContainer container = HelperContainers.nginx()) {
            for (Path p : files) {
                TemporaryFolderManager.mountHostFolderAsVolume(
                        container, p, p.toAbsolutePath().toString());
            }
            container.start();
            for (Path p : files) {
                Container.ExecResult x = container.execInContainer(
                        "chown", "-R", userAndGroup, p.toAbsolutePath().toString());
            }
            container.stop();
        }
    }
}
