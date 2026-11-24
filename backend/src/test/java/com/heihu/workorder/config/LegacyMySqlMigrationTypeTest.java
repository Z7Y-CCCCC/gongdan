package com.heihu.workorder.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyMySqlMigrationTypeTest {
    @Test
    void flywaySelectsTheShimForMySqlButNotH2() {
        assertThat(DatabaseTypeRegister.getDatabaseTypeForUrl("jdbc:mysql://localhost/audit"))
                .isInstanceOf(LegacyMySqlMigrationType.class);
        assertThat(DatabaseTypeRegister.getDatabaseTypeForUrl("jdbc:h2:mem:audit"))
                .isNotInstanceOf(LegacyMySqlMigrationType.class);
    }

    @Test
    void translatesOnlyLegacyColumnClausesAndPreservesTheOriginalResource() throws IOException {
        checkScript(LegacyMySqlMigrationType.V15, 4);
        String v18 = checkScript(LegacyMySqlMigrationType.V18, 1);
        assertThat(v18)
                .contains("SET `last_active_at` = `created_at`", "CREATE INDEX `idx_user_sessions_last_active_at`")
                .contains("WHERE `last_active_at` IS NULL;");
        LoadableResource later = resource("V24__index_inventory_receipt_times.sql", "SELECT 1;");
        assertThat(LegacyMySqlMigrationType.forExecution(later)).isSameAs(later);
    }

    @Test
    void rejectsAnEditedHistoricalResourceRatherThanSilentlyApplyingAGuess() {
        assertThatThrownBy(() -> LegacyMySqlMigrationType.forExecution(resource(LegacyMySqlMigrationType.V15, "SELECT 1;")))
                .isInstanceOf(FlywayException.class).hasMessageContaining("pinned source");
    }

    private String checkScript(String name, int additions) throws IOException {
        String sql = String.join("\n", Files.readAllLines(Paths.get("src/main/resources/db/migration", name)));
        LoadableResource original = resource(name, sql);
        int checksum = name.equals(LegacyMySqlMigrationType.V15) ? 1090301005 : -1652635468;
        LoadableResource translated = LegacyMySqlMigrationType.forExecution(original);
        assertThat(new LegacyMySqlMigrationType().createSqlScriptFactory(Flyway.configure(), new ParsingContext())
                .createSqlScript(original, false, null).getSqlStatementCount())
                .isEqualTo(additions * 4 + 1 + (name.equals(LegacyMySqlMigrationType.V18) ? 2 : 0));
        assertThat(translated.getFilename()).isEqualTo(original.getFilename());
        String result = read(translated);
        assertThat(result.split("PREPARE gongdan_session_stmt FROM", -1)).hasSize(additions + 1);
        assertThat(result).doesNotContain("ADD COLUMN IF NOT EXISTS").contains("table_schema = DATABASE()");
        assertThat(ChecksumCalculator.calculate(original)).isEqualTo(checksum);
        assertThat(read(original)).isEqualTo(sql);
        return result;
    }

    private static String read(LoadableResource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(resource.read())) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static LoadableResource resource(String filename, String text) {
        return new LoadableResource() {
            @Override public Reader read() { return new StringReader(text); }
            @Override public String getAbsolutePath() { return "db/migration/" + filename; }
            @Override public String getAbsolutePathOnDisk() { return getAbsolutePath(); }
            @Override public String getFilename() { return filename; }
            @Override public String getRelativePath() { return getAbsolutePath(); }
        };
    }
}
