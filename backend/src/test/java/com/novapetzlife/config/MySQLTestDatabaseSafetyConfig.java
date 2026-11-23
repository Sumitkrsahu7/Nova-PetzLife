package com.novapetzlife.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict fail-fast safety validator for the opt-in 'mysql-test' profile.
 * Ensures tests never accidentally execute against persistent production or staging databases.
 */
@Configuration
@Profile("mysql-test")
public class MySQLTestDatabaseSafetyConfig {

    private static final Logger log = LoggerFactory.getLogger(MySQLTestDatabaseSafetyConfig.class);

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "[::1]"
    );

    private static final Pattern JDBC_MYSQL_PATTERN = Pattern.compile(
            "(?i)^jdbc:mysql://(?:[^/@]+@)?(\\[[a-f0-9:]+\\]|[^:/?#]+)(?::\\d+)?(?:/([^?#]+))?"
    );

    private final DataSource dataSource;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    public MySQLTestDatabaseSafetyConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void validateTestDatabaseSafety() {
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            throw new IllegalStateException("SAFETY ABORT: spring.datasource.url is empty under mysql-test profile.");
        }

        String lowerUrl = datasourceUrl.toLowerCase(Locale.ROOT);

        // 1. Prohibit external/production hosts and substrings
        if (lowerUrl.contains("render.com") || lowerUrl.contains("amazonaws.com") || lowerUrl.contains("prod")) {
            throw new IllegalStateException(
                    "SAFETY ABORT: Datasource URL appears to point to an external or production environment. " +
                    "Configured URL: " + sanitizeUrl(datasourceUrl)
            );
        }

        // 2. Restrict allowed database hosts to an explicit safe local/test allowlist (localhost, 127.0.0.1)
        Matcher matcher = JDBC_MYSQL_PATTERN.matcher(datasourceUrl);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "SAFETY ABORT: Could not parse database host from URL. Must be a valid MySQL JDBC URL: " +
                    sanitizeUrl(datasourceUrl)
            );
        }

        String host = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    "SAFETY ABORT: Host '" + host + "' is not permitted under the mysql-test profile. " +
                    "Only local test hosts " + ALLOWED_HOSTS + " are permitted. External hosts are strictly prohibited. " +
                    "Configured URL: " + sanitizeUrl(datasourceUrl)
            );
        }

        // 3. Must target a dedicated test database name
        String dbPath = matcher.group(2) != null ? matcher.group(2).toLowerCase(Locale.ROOT) : "";
        if (!dbPath.contains("_test") && !dbPath.contains("test")) {
            throw new IllegalStateException(
                    "SAFETY ABORT: Database name '" + dbPath + "' does not identify a dedicated test database (must contain '_test' or 'test'). " +
                    "Configured URL: " + sanitizeUrl(datasourceUrl)
            );
        }

        // 4. Verify actual JDBC connection, MySQL product, version 8.0.x, and InnoDB storage engine
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName();
            String productVersion = metaData.getDatabaseProductVersion();
            int majorVersion = metaData.getDatabaseMajorVersion();
            int minorVersion = metaData.getDatabaseMinorVersion();

            // Fail fast if fell back to H2 or other database product
            if (productName == null || !productName.toLowerCase(Locale.ROOT).contains("mysql")) {
                throw new IllegalStateException(
                        "SAFETY ABORT: Expected MySQL database product, but detected: " + productName + ". " +
                        "Silent fallback to H2 or other engines is prohibited under the mysql-test profile."
                );
            }

            if (productName.toLowerCase(Locale.ROOT).contains("h2")) {
                throw new IllegalStateException(
                        "SAFETY ABORT: Expected MySQL 8.0/InnoDB datasource, but detected H2."
                );
            }

            // Verify database version is MySQL 8.0.x
            boolean isVersion80 = (majorVersion == 8 && minorVersion == 0)
                    || (productVersion != null && productVersion.startsWith("8.0."));
            if (!isVersion80) {
                throw new IllegalStateException(
                        "SAFETY ABORT: Expected MySQL 8.0.x, but detected version: " + productVersion +
                        " (major: " + majorVersion + ", minor: " + minorVersion + ")"
                );
            }

            // 5. Verify relevant application tables use InnoDB
            try (Statement stmt = conn.createStatement()) {
                // Verify default storage engine
                try (ResultSet rs = stmt.executeQuery("SELECT @@default_storage_engine")) {
                    if (rs.next()) {
                        String defaultEngine = rs.getString(1);
                        if (defaultEngine != null && !"innodb".equalsIgnoreCase(defaultEngine.trim())) {
                            throw new IllegalStateException(
                                    "SAFETY ABORT: Default storage engine is '" + defaultEngine + "', expected InnoDB."
                            );
                        }
                    }
                }

                // Verify specific existing application tables
                String tableCheckSql = "SELECT table_name, engine FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name IN ('products', 'orders', 'inventory', 'customers', 'support_tickets')";
                try (ResultSet rs = stmt.executeQuery(tableCheckSql)) {
                    while (rs.next()) {
                        String tableName = rs.getString("table_name");
                        String engine = rs.getString("engine");
                        if (engine == null || !"innodb".equalsIgnoreCase(engine.trim())) {
                            throw new IllegalStateException(
                                    "SAFETY ABORT: Table '" + tableName + "' uses storage engine '" + engine + "', expected InnoDB."
                            );
                        }
                    }
                }
            }

            log.info("SAFETY CHECK PASSED: Verified MySQL {}.{} ({}) on allowed host '{}' with InnoDB engine. URL: {}",
                    majorVersion, minorVersion, productVersion, host, sanitizeUrl(datasourceUrl));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SAFETY ABORT: Could not verify connection to test MySQL database: " + e.getMessage(), e);
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("(?i)(password|pwd|user|username)=[^&;]*", "$1=***");
    }
}
