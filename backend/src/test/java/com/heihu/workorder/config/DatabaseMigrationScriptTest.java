package com.heihu.workorder.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.MigrationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationScriptTest {

    @Test
    void migrationScriptsUseUniqueVersions() throws IOException {
        List<String> scriptNames = migrationScriptNames();

        assertThat(scriptNames).allMatch(name -> name.matches("V\\d+__.+\\.(sql|java)"));
        assertThat(scriptNames.stream().filter(name -> name.endsWith(".sql"))).hasSize(20);
        assertThat(scriptNames.stream().filter(name -> name.endsWith(".java")))
                .containsExactly("V20__add_product_version.java", "V21__scope_receipt_codes_to_factory.java");

        Map<String, List<String>> byVersion = scriptNames.stream()
                .collect(Collectors.groupingBy(this::versionOf));
        List<String> duplicateVersions = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.toList());

        assertThat(duplicateVersions).isEmpty();
    }

    @Test
    void migrationScriptsAreSequentialStartingAtV1() throws IOException {
        List<Integer> versions = migrationScriptNames().stream()
                .map(this::versionOf)
                .map(Integer::parseInt)
                .sorted()
                .collect(Collectors.toList());

        assertThat(versions).hasSize(22);
        assertThat(versions.get(0)).isEqualTo(1);
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void flywayMigrationsRunOnEmptyDatabase() {
        int migrationCount;
        try {
            migrationCount = migrationScriptNames().size();
        } catch (IOException ex) {
            throw new AssertionError("Unable to list migration scripts", ex);
        }
        Flyway flyway = Flyway.configure()
                .dataSource(
                        "jdbc:h2:mem:flyway_migration_" + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                        "sa",
                        ""
                )
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(migrationCount);
        assertThat(Arrays.stream(flyway.info().applied()).filter(info -> info.getType() == MigrationType.JDBC)
                .map(info -> info.getVersion().toString())).containsExactly("20", "21");
        assertThat(Arrays.stream(flyway.info().applied()).filter(info -> info.getType() == MigrationType.JDBC)
                .map(info -> info.getChecksum())).doesNotContainNull();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private List<String> migrationScriptNames() throws IOException {
        Path migrationDir = Paths.get("src", "main", "resources", "db", "migration");
        Path javaMigrationDir = Paths.get("src", "main", "java", "db", "migration");

        try (Stream<Path> sql = Files.list(migrationDir); Stream<Path> java = Files.list(javaMigrationDir)) {
            return Stream.concat(sql, java)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql") || name.matches("V\\d+__.+\\.java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private String versionOf(String name) {
        return name.substring(1, name.indexOf("__"));
    }
}
