package com.neo4j.docker.utils;

import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.GenericContainer;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Result;

public class DatabaseIO
{
	private static Config TEST_DRIVER_CONFIG = Config.builder().withoutEncryption().build();

	private GenericContainer container;
	private String boltUri;

	public DatabaseIO( GenericContainer container )
	{
		this.container = container;
		this.boltUri = getBoltURIFromContainer( container );
	}

	public static String getBoltURIFromContainer( GenericContainer container )
	{
		return "bolt://"+container.getContainerIpAddress()+":"+container.getMappedPort( 7687 );
	}

	public void putInitialDataIntoContainer( String user, String password )
	{
		Driver driver = GraphDatabase.driver( boltUri, getToken( user, password ), TEST_DRIVER_CONFIG );
		try ( Session session = driver.session())
		{
			Result rs = session.run( "CREATE (arne:dog {name:'Arne'})-[:SNIFFS]->(bosse:dog {name:'Bosse'}) RETURN arne.name");
			Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
		}
		driver.close();
	}

	public void verifyDataInContainer( String user, String password )
	{
		Driver driver = GraphDatabase.driver( boltUri, getToken( user, password ), TEST_DRIVER_CONFIG );
		try ( Session session = driver.session())
		{
			Result rs = session.run( "MATCH (a:dog)-[:SNIFFS]->(b:dog) RETURN a.name");
			Assertions.assertEquals( "Arne", rs.single().get( 0 ).asString(), "did not receive expected result from cypher CREATE query" );
		}
		driver.close();
	}

	public void changePassword(String user, String oldPassword, String newPassword)
	{
		if(TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ))
		{
			Driver driver = GraphDatabase.driver( boltUri, AuthTokens.basic( user, oldPassword ), TEST_DRIVER_CONFIG );
			try ( Session session = driver.session( SessionConfig.forDatabase( "system" )))
			{
				Result rs = session.run( "ALTER CURRENT USER SET PASSWORD FROM '"+oldPassword+"' TO '"+newPassword+"'" );
			}
			driver.close();
		}
		else
		{
			runCypherProcedure( user, oldPassword, "CALL dbms.changePassword('"+newPassword+"')" );
		}
	}

	public void runCypherProcedure( String user, String password, String cypher )
	{
		Driver driver = GraphDatabase.driver( boltUri, getToken( user, password ), TEST_DRIVER_CONFIG );
		try ( Session session = driver.session())
		{
			Result rs = session.run( cypher );
		}
		driver.close();
	}

	public void verifyConnectivity( String user, String password )
	{
		GraphDatabase.driver( getBoltURIFromContainer(container),
							  getToken( user, password ),
							  TEST_DRIVER_CONFIG )
					 .verifyConnectivity();
	}

	private AuthToken getToken(String user, String password)
	{
		if(password.equals( "none" ))
		{
			return AuthTokens.none();
		}
		else
		{
			return AuthTokens.basic( user, password );
		}
	}
}
