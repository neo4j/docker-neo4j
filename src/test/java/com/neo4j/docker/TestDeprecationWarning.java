package com.neo4j.docker;

import com.neo4j.docker.utils.Neo4jVersion;
import com.neo4j.docker.utils.TestSettings;
import com.neo4j.docker.utils.WaitStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**Tests to make sure that deprecated base OS images give suitable warnings.
 * The opposite test to make sure there are no deprecation warnings in un-deprecated images
 * already exists at {@code TestBasic.testNoUnexpectedErrors()}
 *
 * Expected behaviour is:
 * - While an OS is being deprecated, the container should give a warning message. This can be suppressed with an env var.
 * - When we reach the final version to be released with the OS, the container should show a final unsupressable warning.
 * - Any image created after the final deprecated version using the deprecated OS should be a failure.
 * */
public class TestDeprecationWarning {
    private final Logger log = LoggerFactory.getLogger( TestDeprecationWarning.class );
    /**Map of OS to final 5x version we will release with it.
     * Defined in build-scripts/deprecation-warnings.sh
     * */
    private static final Map<TestSettings.BaseOS, Neo4jVersion> DEPRECATED_OS_526 = new HashMap<>() {
        {
            put(TestSettings.BaseOS.UBI8, new Neo4jVersion(5, 20, 0));
            put(TestSettings.BaseOS.UBI9, new Neo4jVersion(5, 26, 21));
            put(TestSettings.BaseOS.BULLSEYE, new Neo4jVersion(5, 26, 21));
        }
    };
    /**Map of OS to final calver version we will release with it.
     * Defined in build-scripts/deprecation-warnings.sh
     * */
    private static final Map<TestSettings.BaseOS,Neo4jVersion> DEPRECATED_OS_CALVER = new HashMap<>() {{
        put( TestSettings.BaseOS.UBI8, new Neo4jVersion( 2024, 1, 0 ) );    // calver was never released with ubi8
        put( TestSettings.BaseOS.UBI9, new Neo4jVersion( 2026, 3, 0 ) );
        put( TestSettings.BaseOS.BULLSEYE, new Neo4jVersion( 2026, 3, 0 ) );
    }};

    private static final String DEPRECATION_WARN_SUPPRESS_FLAG = "NEO4J_DEPRECATION_WARNING";
    private final Pattern earlyWarningRegex = Pattern.compile( "Neo4j (Red Hat|Debian) %s images are deprecated in favour of "
                                                                       .formatted( TestSettings.BASE_OS.toString().toUpperCase()) );
    private final Pattern finalWarningRegex = Pattern.compile( "This is the last Neo4j image available on (Red Hat|Debian) %s"
                                                                       .formatted( TestSettings.BASE_OS.toString().toUpperCase() ));
    private Neo4jVersion deprecatedIn;

    @BeforeAll
    static void assume5xOrLater() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isAtLeastVersion( Neo4jVersion.NEO4J_VERSION_500 ) );
    }

    @BeforeEach
    void findDeprecatedVersion() {
        if (TestSettings.NEO4J_VERSION.isCalver()) {
            Assumptions.assumeTrue(DEPRECATED_OS_CALVER.containsKey(TestSettings.BASE_OS),
                                   "Base OS " + TestSettings.BASE_OS + " is not deprecated so doesn't need checking.");
            deprecatedIn =  DEPRECATED_OS_CALVER.get( TestSettings.BASE_OS );
        }
        else {
            Assumptions.assumeTrue(DEPRECATED_OS_526.containsKey(TestSettings.BASE_OS),
                                   "Base OS " + TestSettings.BASE_OS + " is not deprecated so doesn't need checking.");
            deprecatedIn = DEPRECATED_OS_526.get( TestSettings.BASE_OS );
        }
    }

    @Test
    void testEarlyDeprecationWarning_coreDB() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( deprecatedIn ),
                                "%s does not have early deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runCoreDBGetErrorLogs( false );
        Assertions.assertTrue( earlyWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
    }
    @Test
    void testEarlyDeprecationWarning_neo4jAdmin() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( deprecatedIn ),
                                "%s does not have early deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runNeo4jAdminGetErrorLogs( false );
        Assertions.assertTrue( earlyWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
    }
    @Test
    void testEarlyDeprecationWarningSuppressed_coreDB() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( deprecatedIn ),
                                "%s does not have early deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runCoreDBGetErrorLogs(true);
        Assertions.assertFalse( earlyWarningRegex.matcher( logs ).find(),
                                "Container incorrectly warned about "+TestSettings.BASE_OS+" deprecation. " +
                                "Actual error logs:\n"+logs);
    }
    @Test
    void testEarlyDeprecationWarningSuppressed_neo4jAdmin() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.isOlderThan( deprecatedIn ),
                                "%s does not have early deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runNeo4jAdminGetErrorLogs(true);
        Assertions.assertFalse( earlyWarningRegex.matcher( logs ).find(),
                                "Container incorrectly warned about "+TestSettings.BASE_OS+" deprecation. " +
                                "Actual error logs:\n"+logs);
    }

    @Test
    void testFinalWarning_coreDB() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.equals( deprecatedIn ),
                                "%s does not need final deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runCoreDBGetErrorLogs( false );
        Assertions.assertTrue( earlyWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
        Assertions.assertTrue( finalWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
    }
    @Test
    void testFinalWarning_neo4jAdmin() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.equals( deprecatedIn ),
                                "%s does not need final deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runNeo4jAdminGetErrorLogs( false );
        Assertions.assertTrue( earlyWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
        Assertions.assertTrue( finalWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
    }
    @Test
    void testFinalWarningSuppressed_coreDB() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.equals( deprecatedIn ),
                                "%s does not need final deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runCoreDBGetErrorLogs( true );
        Assertions.assertTrue( earlyWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
        Assertions.assertTrue( finalWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
    }
    @Test
    void testFinalWarningSuppressed_neo4jAdmin() {
        Assumptions.assumeTrue( TestSettings.NEO4J_VERSION.equals( deprecatedIn ),
                                "%s does not need final deprecation warning".formatted( TestSettings.NEO4J_VERSION ) );
        String logs = runNeo4jAdminGetErrorLogs( true );
        Assertions.assertTrue( earlyWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
        Assertions.assertTrue( finalWarningRegex.matcher( logs ).find(),
                               "Container did not warn about "+TestSettings.BASE_OS+" deprecation. " +
                               "Actual error logs:\n"+logs);
    }

    @Test
    void shouldNotCreateImageAfterDeprecation() {
        // To get here we must be testing an image flagged for deprecation.
        // This test makes sure we don't make a deprecated image newer than we expect.
        Assertions.assertFalse( TestSettings.NEO4J_VERSION.isNewerThan( deprecatedIn ),
                                "Should not be releasing %s newer than %s".formatted( TestSettings.BASE_OS, deprecatedIn ) );
    }

    String runCoreDBGetErrorLogs(boolean suppressWarning) {
        try (GenericContainer<?> container = new GenericContainer<>(TestSettings.IMAGE_ID)) {
            container
                    .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                    .withExposedPorts(7474, 7687)
                    .withLogConsumer(new Slf4jLogConsumer(log))
                    .waitingFor(WaitStrategies.waitForBoltReady());
            if (suppressWarning) {
                container.withEnv(DEPRECATION_WARN_SUPPRESS_FLAG, "suppress");
            }
            container.start();
            return container.getLogs(OutputFrame.OutputType.STDERR);
        }
    }

    String runNeo4jAdminGetErrorLogs(boolean suppressWarning) {
        try (GenericContainer<?> container = new GenericContainer<>(TestSettings.ADMIN_IMAGE_ID)) {
            container
                    .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                    .withExposedPorts(7474, 7687)
                    .withLogConsumer(new Slf4jLogConsumer(log))
                    .withCommand("neo4j-admin", "--help");
            if (suppressWarning) {
                container.withEnv(DEPRECATION_WARN_SUPPRESS_FLAG, "suppress");
            }
            WaitStrategies.waitUntilContainerFinished(container, Duration.ofSeconds(30));
            container.start();
            return container.getLogs(OutputFrame.OutputType.STDERR);
        }
    }

    // this is a requirement for docker official images, so doesn't need testing for neo4j-admin
    @Test
    void shouldOnlyWarnWhenRunningNeo4jCommands() throws Exception
    {
        try(GenericContainer<?> container = new GenericContainer<>(TestSettings.IMAGE_ID))
        {
            container.withEnv( "NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes" )
                     .withExposedPorts( 7474, 7687 )
                     .withLogConsumer( new Slf4jLogConsumer( log ))
                     .withCommand( "cat", "/etc/os-release" );
            WaitStrategies.waitUntilContainerFinished( container, Duration.ofSeconds( 30 ) );
            container.start();
            String logs = container.getLogs( OutputFrame.OutputType.STDERR );
            Assertions.assertFalse(earlyWarningRegex.matcher( logs ).find(),
                                   "Container should not have warned about deprecation. Actual error logs:\n"+logs);
            Assertions.assertFalse(finalWarningRegex.matcher( logs ).find(),
                                   "Container should not have warned about deprecation. Actual error logs:\n"+logs);
        }
    }
}
