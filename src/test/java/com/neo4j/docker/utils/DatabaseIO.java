package com.neo4j.docker.utils;

import com.neo4j.docker.coredb.configurations.Configuration;
import org.junit.jupiter.api.Assertions;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.stream.Collectors;

public class DatabaseIO
{
	private static final Config DEFAULT_DRIVER_CONFIG = Config.builder().withoutEncryption().build();
	private final Logger log = LoggerFactory.getLogger( DatabaseIO.class );

	private GenericContainer container;
	private String boltUri;

	public DatabaseIO( GenericContainer container )
	{
		this.container = container;
        this.boltUri = "bolt://"+container.getHost()+":"+container.getMappedPort( 7687 );
	}

    public DatabaseIO( String host, Integer boltPort )
    {
        this.boltUri = "bolt://" + host + ":" + boltPort;
    }

	public void putInitialDataIntoContainer( String user, String password )
	{
		log.info( "Writing data into database" );
        List<Record> result = runCypherQuery( user, password,"CREATE (arne:dog {name:'Arne'})-[:SNIFFS]->(bosse:dog {name:'Bosse'}) RETURN arne.name" );
        Assertions.assertEquals( "Arne", result.get( 0 ).get( "arne.name" ).asString(), "did not receive expected result from cypher CREATE query" );
	}

	public void verifyInitialDataInContainer( String user, String password )
	{
		log.info( "verifying data is present in the database" );		
		List<Record> result = runCypherQuery( user, password,"MATCH (a:dog)-[:SNIFFS]->(b:dog) RETURN a.name");
        Assertions.assertEquals( "Arne", result.get( 0 ).get("a.name").asString(), "did not receive expected result from cypher MATCH query" );
	}

	public void putMoreDataIntoContainer( String user, String password )
	{
		log.info( "Writing more data into database" );
        List<Record> result = runCypherQuery( user, password,
                      "MATCH (a:dog {name:'Arne'}) CREATE (armstrong:dog {name:'Armstrong'})-[:SNIFFS]->(a) return a.name, armstrong.name" );
        Assertions.assertEquals( "Arne", result.get( 0 ).get("a.name").asString(),
                                 "did not receive expected result from cypher MATCH query" );
        Assertions.assertEquals( "Armstrong", result.get( 0 ).get( "armstrong.name" ).asString(),
                                 "did not receive expected result from cypher CREATE query" );
	}

	public void verifyMoreDataIntoContainer( String user, String password, boolean extraDataShouldBeThere )
	{
		log.info( "Verifying extra data is {}in database", extraDataShouldBeThere? "":"not " );
		List<Record> result = runCypherQuery( user, password,"MATCH (a:dog)-[:SNIFFS]->(b:dog) RETURN a.name");
		String dogs = result.stream()
                            .map( record -> record.get( 0 ).asString() )
                            .sorted()
                            .collect( Collectors.joining(","));
		// dogs should now be a String which is a comma delimited list of dog names

		if(extraDataShouldBeThere)
        {
            Assertions.assertEquals( "Armstrong,Arne", dogs, "cypher query did not return correct data" );
        }
		else
        {
            Assertions.assertEquals( "Arne", dogs, "cypher query did not return correct data" );
        }
	}

    public String getConfigurationSettingAsString( String user, String password, Configuration conf)
    {
        List<Record> confRecord = runCypherQuery( user, password,
                                                  "CALL dbms.listConfig() YIELD name, value " +
                                                  "WHERE name='" + conf.name + "' " +
                                                  "RETURN value" );
        Assertions.assertEquals(1, confRecord.size(), "Configuration "+conf.name+" was not set." );
        return confRecord.get( 0 ).get( 0 ).asString();
    }

    public void verifyConfigurationSetting( String user, String password, Configuration conf, String expectedValue)
    {
        verifyConfigurationSetting(user, password, conf, expectedValue, "");
    }

    public void verifyConfigurationSetting( String user, String password, Configuration conf, String expectedValue, String extraFailureMsg)
    {
        String actualConf = getConfigurationSettingAsString( user, password, conf );
        Assertions.assertEquals(expectedValue, actualConf,
                                String.format("Expected %s to be %s but it was %s.%s",
                                              conf.name, expectedValue, actualConf, extraFailureMsg));
    }

	public void changePassword(String user, String oldPassword, String newPassword)
	{
		if(TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_400 ))
		{
		    String cypher = "ALTER CURRENT USER SET PASSWORD FROM '"+oldPassword+"' TO '"+newPassword+"'";
		    runCypherQuery( user, oldPassword, cypher, "system" );
		}
		else
		{
		    runCypherQuery( user, oldPassword, "CALL dbms.changePassword('"+newPassword+"')" );
		}
	}

	public List<Record> runCypherQuery( String user, String password, String cypher)
    {
        // we don't just do runCypherQuery( user, password, cypher, "neo4j")
        // because it breaks the upgrade tests from 3.5.x
        List<Record> records;
		Driver driver = GraphDatabase.driver( boltUri, getToken( user, password ), DEFAULT_DRIVER_CONFIG);
		try ( Session session = driver.session())
		{
			Result rs = session.run( cypher );
			records = rs.list();
		}
		driver.close();
		return records;
    }

	public List<Record> runCypherQuery( String user, String password, String cypher, String database)
    {
        List<Record> records;
		Driver driver = GraphDatabase.driver( boltUri, getToken( user, password ), DEFAULT_DRIVER_CONFIG);
		try ( Session session = driver.session(SessionConfig.forDatabase( database )))
		{
			Result rs = session.run( cypher );
			records = rs.list();
		}
		driver.close();
		return records;
    }

	public void verifyConnectivity( String user, String password )
	{
		GraphDatabase.driver( boltUri,
							  getToken( user, password ),
						DEFAULT_DRIVER_CONFIG)
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
