package com.novapetzlife.order;

import com.novapetzlife.config.MySQLTestDatabaseSafetyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Dedicated staging integration test that explicitly activates the 'mysql-test' profile.
 * Triggers MySQLTestDatabaseSafetyConfig to enforce strict test-database URL safety assertions,
 * prevent production data access, and fail fast if H2 is detected.
 *
 * Opt-in via: mvn test -Dtest=MySQLCheckoutConcurrencyIntegrationTest -Drun.mysql.tests=true
 */
@SpringBootTest
@ActiveProfiles(profiles = "mysql-test", inheritProfiles = false)
@EnabledIfSystemProperty(named = "run.mysql.tests", matches = "true")
public class MySQLCheckoutConcurrencyIntegrationTest extends CheckoutConcurrencyIntegrationTest {

    @Autowired(required = false)
    private MySQLTestDatabaseSafetyConfig safetyConfig;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("MySQL Staging Harness: Genuine activation of mysql-test profile and safety guard")
    void testMySQLTestProfileAndSafetyGuardActive() {
        assertNotNull(safetyConfig, "MySQLTestDatabaseSafetyConfig must be active under mysql-test profile");
        assertNotNull(dataSource, "Datasource must be configured");
    }
}
