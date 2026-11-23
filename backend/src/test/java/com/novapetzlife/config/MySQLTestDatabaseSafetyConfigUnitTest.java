package com.novapetzlife.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MySQLTestDatabaseSafetyConfigUnitTest {

    @Test
    @DisplayName("Safety Guard: Rejects external hosts even when database name contains 'test'")
    void testRejectsExternalHostsEvenWithTestInDbName() {
        DataSource mockDs = Mockito.mock(DataSource.class);
        MySQLTestDatabaseSafetyConfig config = new MySQLTestDatabaseSafetyConfig(mockDs);
        ReflectionTestUtils.setField(config, "datasourceUrl", "jdbc:mysql://external-server.company.com:3306/nova_petzlife_test");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateTestDatabaseSafety);
        assertTrue(ex.getMessage().contains("is not permitted under the mysql-test profile"));
        assertTrue(ex.getMessage().contains("external-server.company.com"));
    }

    @Test
    @DisplayName("Safety Guard: Rejects prohibited production cloud domains")
    void testRejectsProductionCloudDomains() {
        DataSource mockDs = Mockito.mock(DataSource.class);
        MySQLTestDatabaseSafetyConfig config = new MySQLTestDatabaseSafetyConfig(mockDs);
        ReflectionTestUtils.setField(config, "datasourceUrl", "jdbc:mysql://render.com/nova_petzlife_test");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateTestDatabaseSafety);
        assertTrue(ex.getMessage().contains("Datasource URL appears to point to an external or production environment"));
    }

    @Test
    @DisplayName("Safety Guard: Rejects non-test database names on localhost")
    void testRejectsNonTestDatabaseNamesOnLocalhost() {
        DataSource mockDs = Mockito.mock(DataSource.class);
        MySQLTestDatabaseSafetyConfig config = new MySQLTestDatabaseSafetyConfig(mockDs);
        ReflectionTestUtils.setField(config, "datasourceUrl", "jdbc:mysql://localhost:3306/nova_petzlife_main");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateTestDatabaseSafety);
        assertTrue(ex.getMessage().contains("does not identify a dedicated test database"));
    }

    @Test
    @DisplayName("Safety Guard: Rejects non-MySQL database product (e.g. H2 or PostgreSQL)")
    void testRejectsNonMySQLProduct() throws Exception {
        DataSource mockDs = Mockito.mock(DataSource.class);
        Connection mockConn = Mockito.mock(Connection.class);
        DatabaseMetaData mockMeta = Mockito.mock(DatabaseMetaData.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.getMetaData()).thenReturn(mockMeta);
        when(mockMeta.getDatabaseProductName()).thenReturn("H2");

        MySQLTestDatabaseSafetyConfig config = new MySQLTestDatabaseSafetyConfig(mockDs);
        ReflectionTestUtils.setField(config, "datasourceUrl", "jdbc:mysql://localhost:3306/nova_petzlife_test");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateTestDatabaseSafety);
        assertTrue(ex.getMessage().contains("Expected MySQL database product"));
    }

    @Test
    @DisplayName("Safety Guard: Rejects MySQL versions other than 8.0.x (e.g. 5.7 or 9.0)")
    void testRejectsNon80Versions() throws Exception {
        DataSource mockDs = Mockito.mock(DataSource.class);
        Connection mockConn = Mockito.mock(Connection.class);
        DatabaseMetaData mockMeta = Mockito.mock(DatabaseMetaData.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.getMetaData()).thenReturn(mockMeta);
        when(mockMeta.getDatabaseProductName()).thenReturn("MySQL");
        when(mockMeta.getDatabaseProductVersion()).thenReturn("5.7.35");
        when(mockMeta.getDatabaseMajorVersion()).thenReturn(5);
        when(mockMeta.getDatabaseMinorVersion()).thenReturn(7);

        MySQLTestDatabaseSafetyConfig config = new MySQLTestDatabaseSafetyConfig(mockDs);
        ReflectionTestUtils.setField(config, "datasourceUrl", "jdbc:mysql://localhost:3306/nova_petzlife_test");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateTestDatabaseSafety);
        assertTrue(ex.getMessage().contains("Expected MySQL 8.0.x"));
    }

    @Test
    @DisplayName("Safety Guard: Rejects non-InnoDB default storage engine")
    void testRejectsNonInnoDBDefaultEngine() throws Exception {
        DataSource mockDs = Mockito.mock(DataSource.class);
        Connection mockConn = Mockito.mock(Connection.class);
        DatabaseMetaData mockMeta = Mockito.mock(DatabaseMetaData.class);
        Statement mockStmt = Mockito.mock(Statement.class);
        ResultSet mockRs = Mockito.mock(ResultSet.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.getMetaData()).thenReturn(mockMeta);
        when(mockMeta.getDatabaseProductName()).thenReturn("MySQL");
        when(mockMeta.getDatabaseProductVersion()).thenReturn("8.0.33");
        when(mockMeta.getDatabaseMajorVersion()).thenReturn(8);
        when(mockMeta.getDatabaseMinorVersion()).thenReturn(0);

        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("SELECT @@default_storage_engine")).thenReturn(mockRs);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getString(1)).thenReturn("MyISAM");

        MySQLTestDatabaseSafetyConfig config = new MySQLTestDatabaseSafetyConfig(mockDs);
        ReflectionTestUtils.setField(config, "datasourceUrl", "jdbc:mysql://127.0.0.1:3306/nova_petzlife_test");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateTestDatabaseSafety);
        assertTrue(ex.getMessage().contains("Default storage engine is 'MyISAM', expected InnoDB"));
    }
}
