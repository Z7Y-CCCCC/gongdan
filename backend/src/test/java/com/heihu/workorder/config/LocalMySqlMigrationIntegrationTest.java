package com.heihu.workorder.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in only. Creates and drops a randomly named loopback test schema per test. */
@EnabledIfEnvironmentVariable(named = "GONGDAN_MIGRATION_MYSQL_TEST", matches = "1")
class LocalMySqlMigrationIntegrationTest {
    @Test
    void emptySchemaMigratesThroughV25AndKeepsHistoricalSqlIdentity() throws Exception {
        withTemporarySchema((url, user, password) -> {
            Flyway flyway = flyway(url, user, password, "25");
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(25);
            verifyHistory(flyway);
        });
    }

    @Test
    void upgradingV14WithExistingSessionColumnsPreservesValuesAndBackfillsNulls() throws Exception {
        withTemporarySchema((url, user, password) -> {
            assertThat(flyway(url, user, password, "14").migrate().migrationsExecuted).isEqualTo(14);
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE user_sessions ADD COLUMN ip_address VARCHAR(64) NULL,"
                        + " ADD COLUMN last_active_at DATETIME(6) NULL");
                statement.execute("INSERT INTO user_sessions (token, user_id, factory_id, factory_code, role,"
                        + " display_name, kicked, expires_at, created_at, ip_address, last_active_at)"
                        + " VALUES ('test-one', 1, 1, 'TEST', 'ADMIN', 'Tester', 0, '2030-01-01',"
                        + " '2020-01-01', '127.0.0.1', '2020-01-03'),"
                        + " ('test-two', 1, 1, 'TEST', 'ADMIN', 'Tester', 0, '2030-01-01',"
                        + " '2020-01-02', NULL, NULL)");
            }
            Flyway flyway = flyway(url, user, password, "25");
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(11);
            verifyHistory(flyway);
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT ip_address, CAST(last_active_at AS DATE) AS active_date"
                         + " FROM user_sessions ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("ip_address")).isEqualTo("127.0.0.1");
                assertThat(rows.getString("active_date")).isEqualTo("2020-01-03");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("active_date")).isEqualTo("2020-01-02");
                assertThat(rows.next()).isFalse();
            }
        });
    }

    @Test
    void upgradingV24WidensLegacyTextColumnsWithoutLosingData() throws Exception {
        withTemporarySchema((url, user, password) -> {
            assertThat(flyway(url, user, password, "24").migrate().migrationsExecuted).isEqualTo(24);
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO factories (id, code, name, enabled, created_at)"
                        + " VALUES (1, 'TEST', 'Tester', 1, CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO external_data_sources (factory_id, code, name, kind,"
                        + " config_json, secret_cipher, created_at, updated_at)"
                        + " VALUES (1, 'TEST', 'Test', 'HTTP', '{}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),"
                        + " (1, 'TEST2', 'Test', 'HTTP', '{\"path\":\"/test\"}', 'test-only-cipher', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO plugin_catalog (id, plugin_key, name, plugin_type, runtime_mode,"
                        + " status, manifest_json, created_at, updated_at) VALUES (1, 'test', 'Test', 'TEST',"
                        + " 'TEST', 'TEST', '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO plugin_versions (plugin_id, version, status, release_notes, manifest_json,"
                        + " created_at) VALUES (1, '1', 'TEST', 'test-release', '{}', CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO factory_plugins (factory_id, plugin_id, status, config_json, updated_at)"
                        + " VALUES (1, 1, 'TEST', '{}', CURRENT_TIMESTAMP)");
            }
            assertThat(flyway(url, user, password, "25").migrate().migrationsExecuted).isEqualTo(1);
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT config_json, secret_cipher FROM external_data_sources ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("{}");
                assertThat(rows.getString(2)).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("{\"path\":\"/test\"}");
                assertThat(rows.getString(2)).isEqualTo("test-only-cipher");
                assertThat(rows.next()).isFalse();
            }
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns"
                         + " WHERE table_schema = DATABASE() AND data_type = 'longtext' AND ("
                         + " (table_name = 'external_data_sources' AND column_name IN ('config_json', 'secret_cipher'))"
                         + " OR (table_name = 'factory_plugins' AND column_name = 'config_json')"
                         + " OR (table_name = 'plugin_catalog' AND column_name = 'manifest_json')"
                         + " OR (table_name = 'plugin_versions' AND column_name IN ('manifest_json', 'release_notes')))")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(6);
            }
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT fp.config_json, pv.manifest_json, pv.release_notes"
                         + " FROM factory_plugins fp JOIN plugin_versions pv ON pv.plugin_id = fp.plugin_id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("{}");
                assertThat(rows.getString(2)).isEqualTo("{}");
                assertThat(rows.getString(3)).isEqualTo("test-release");
                assertThat(rows.next()).isFalse();
            }
        });
    }

    private static Flyway flyway(String url, String user, String password, String target) {
        return Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").target(target).cleanDisabled(true).load();
    }

    private static void verifyHistory(Flyway flyway) {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("25");
        check(flyway, "15", LegacyMySqlMigrationType.V15, 1090301005);
        check(flyway, "18", LegacyMySqlMigrationType.V18, -1652635468);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private static void check(Flyway flyway, String version, String script, int checksum) {
        for (MigrationInfo info : flyway.info().applied()) {
            if (info.getVersion() != null && version.equals(info.getVersion().getVersion())) {
                assertThat(info.getType()).isEqualTo(MigrationType.SQL);
                assertThat(info.getScript()).isEqualTo(script);
                assertThat(info.getChecksum()).isEqualTo(checksum);
                return;
            }
        }
        throw new AssertionError("Migration missing from schema history: " + version);
    }

    private static void withTemporarySchema(Scenario scenario) throws Exception {
        String user = System.getenv("GONGDAN_MIGRATION_MYSQL_USER");
        String password = System.getenv("GONGDAN_MIGRATION_MYSQL_PASSWORD");
        assertThat(user).as("Local MySQL test account").isNotBlank();
        assertThat(password).as("Local MySQL test password must be explicitly configured").isNotNull();
        String rawPort = System.getenv("GONGDAN_MIGRATION_MYSQL_PORT");
        int port = rawPort == null ? 3308 : Integer.parseInt(rawPort);
        String server = "jdbc:mysql://127.0.0.1:" + port + "/";
        String options = "?allowPublicKeyRetrieval=true&sslMode=DISABLED&connectTimeout=4000";
        String schema = "gongdan_migration_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(server + options, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            try {
                scenario.run(server + schema + options, user, password);
            } finally {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @FunctionalInterface
    interface Scenario {
        void run(String url, String user, String password) throws Exception;
    }
}
