package com.heihu.workorder.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationScriptTest {

    @Test
    void migrationScriptsUseUniqueVersions() throws IOException {
        List<String> scriptNames = migrationScriptNames();

        assertThat(scriptNames).allMatch(name -> name.matches("V\\d+__.+\\.sql"));

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

        assertThat(versions).isNotEmpty();
        assertThat(versions.get(0)).isEqualTo(1);
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void flywayMigrationsRunOnEmptyDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        "jdbc:h2:mem:flyway_migration_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                        "sa",
                        ""
                )
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(2);
    }

    private List<String> migrationScriptNames() throws IOException {
        Path migrationDir = Paths.get("src", "main", "resources", "db", "migration");

        try (Stream<Path> stream = Files.list(migrationDir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private String versionOf(String name) {
        return name.substring(1, name.indexOf("__"));
    }
}
