package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.sun.security.auth.module.UnixSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Consumer;

public class SetContainerUser
{

    public static void currentlyRunningUser( GenericContainer container )
    {
        UnixSystem fs = new UnixSystem();
        String uidgid = fs.getUid() + ":" + fs.getGid() ;
        container.withCreateContainerCmdModifier( (Consumer<CreateContainerCmd>) cmd -> cmd.withUser( uidgid ) );
    }
}
