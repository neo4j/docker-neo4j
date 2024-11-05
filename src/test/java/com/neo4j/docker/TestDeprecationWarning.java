package com.neo4j.docker;

import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.time.Duration;

public class TestDeprecationWarning
{
    private final Logger log = LoggerFactory.getLogger( TestDeprecationWarning.class );
    private static final String DEPRECATION_WARN_STRING = "Neo4j Red Hat UBI8 images are deprecated in favour of Red Hat UBI9";
    private static final String DEPRECATION_WARN_SUPPRESS_FLAG = "NEO4J_DEPRECATION_WARNING";

    // The opposite test to make sure there are no deprecation warnings in non-ubi8 images already exists at
    // com.neo4j.docker.coredb.TestBasic.testNoUnexpectedErrors
    @Test
    void shouldWarnIfUsingDeprecatedBaseOS_coreDB() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.BASE_OS == TestSettings.BaseOS.UBI8,
                                "Deprecation warning should only exist in UBI8 images");
        try(GenericContainer container = new GenericContainer(TestSettings.IMAGE_ID))
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withExposedPorts( 7474, 7687 )
                     .withLogConsumer( new Slf4jLogConsumer( log ))
                     .waitingFor( WaitStrategies.waitForBoltReady() );
            container.start();
            // container should successfully start
            String logs = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue( logs.contains( DEPRECATION_WARN_STRING ),
                                   "Container did not warn about ubi8 deprecation. Actual error logs:\n"+logs);
        }
    }

    @Test
    void shouldWarnIfUsingDeprecatedBaseOS_admin()
    {
        Assumptions.assumeTrue( TestSettings.BASE_OS == TestSettings.BaseOS.UBI8,
                                "Deprecation warning should only exist in UBI8 images");
        try(GenericContainer container = new GenericContainer(TestSettings.ADMIN_IMAGE_ID))
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withExposedPorts( 7474, 7687 )
                     .withLogConsumer( new Slf4jLogConsumer( log ))
                     .withCommand( "neo4j-admin", "--help" );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 30 ));
            container.start();
            // container should successfully start
            String logs = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue( logs.contains( DEPRECATION_WARN_STRING ),
                                   "Container did not warn about ubi8 deprecation. Actual error logs:\n"+logs);
        }
    }

    // this is a requirement for docker official images, so doesn't need testing for neo4j-admin
    @Test
    void shouldOnlyWarnWhenRunningNeo4jCommands() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.BASE_OS == TestSettings.BaseOS.UBI8,
                                "Deprecation warning should only exist in UBI8 images");
        try(GenericContainer container = new GenericContainer(TestSettings.IMAGE_ID))
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withExposedPorts( 7474, 7687 )
                     .withLogConsumer( new Slf4jLogConsumer( log ))
                     .withCommand( "cat", "/etc/os-release" );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 30 ) );
            container.start();
            // container should successfully start
            String logs = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertFalse( logs.contains( DEPRECATION_WARN_STRING ),
                                   "Container should not have warned about ubi8 deprecation. Actual error logs:\n"+logs);
        }
    }

    @Test
    void shouldIgnoreDeprecationSuppression_coreDB() throws Exception
    {
        Assumptions.assumeTrue( TestSettings.BASE_OS == TestSettings.BaseOS.UBI8,
                                "Deprecation warning should only exist in UBI8 images");
        try(GenericContainer container = new GenericContainer(TestSettings.IMAGE_ID))
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withEnv( DEPRECATION_WARN_SUPPRESS_FLAG, "suppress" )
                     .withExposedPorts( 7474, 7687 )
                     .withLogConsumer( new Slf4jLogConsumer( log ))
                     .waitingFor( WaitStrategies.waitForBoltReady() );
            container.start();
            // container should successfully start
            String logs = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue( logs.contains( DEPRECATION_WARN_STRING ),
                    "Container did not warn about ubi8 deprecation. Actual error logs:\n"+logs);
        }
    }

    @Test
    void shouldIgnoreDeprecationSuppressed_admin()
    {
        Assumptions.assumeTrue( TestSettings.BASE_OS == TestSettings.BaseOS.UBI8,
                                "Deprecation warning should only exist in UBI8 images");
        try(GenericContainer container = new GenericContainer(TestSettings.ADMIN_IMAGE_ID))
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withEnv( DEPRECATION_WARN_SUPPRESS_FLAG, "suppress" )
                     .withExposedPorts( 7474, 7687 )
                     .withLogConsumer( new Slf4jLogConsumer( log ))
                     .withCommand( "neo4j-admin", "--help" );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 30 ));
            container.start();
            // container should successfully start
            String logs = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertTrue( logs.contains( DEPRECATION_WARN_STRING ),
                    "Container did not warn about ubi8 deprecation. Actual error logs:\n"+logs);
        }
    }
}
