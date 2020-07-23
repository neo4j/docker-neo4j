package com.neo4j.docker.utils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.Random;
import java.util.function.Consumer;

public class NamedVolumes
{
	private static final Logger log = LoggerFactory.getLogger( NamedVolumes.class );
	private static Random rng = new Random(  );

	public static String createAndMountNamedVolume( GenericContainer container, String namePrefix, String containerMountPoint )
	{
		String namedVolume = String.format( namePrefix+"%04d", rng.nextInt( 10000 ));
		log.info( "creating named volume "+namedVolume );
		container.withCreateContainerCmdModifier( (Consumer<CreateContainerCmd>)
			  cmd -> cmd.getHostConfig().withBinds( Bind.parse ( namedVolume + ":" + containerMountPoint ) ) );
		return namedVolume;
	}

	public static void mountExistingNamedVolume(GenericContainer container, String namedVolume, String containerMountPoint)
	{
		container.withCreateContainerCmdModifier( (Consumer<CreateContainerCmd>)
			  cmd -> cmd.getHostConfig().withBinds( Bind.parse ( namedVolume + ":" + containerMountPoint ) ) );
	}
}
