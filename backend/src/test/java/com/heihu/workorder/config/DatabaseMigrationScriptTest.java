package com.heihu.workorder.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationScriptTest {

    @Test
    void migrationScriptsUseUniqueVersions() throws IOException {
        Path migrationDir = Paths.get("src", "main", "resources", "db", "migration");

        List<String> scriptNames;
        try (Stream<Path> stream = Files.list(migrationDir)) {
            scriptNames = stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        assertThat(scriptNames).allMatch(name -> name.matches("V\\d+__.+\\.sql"));

        Map<String, List<String>> byVersion = scriptNames.stream()
                .collect(Collectors.groupingBy(this::versionOf));
        List<String> duplicateVersions = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.toList());

        assertThat(duplicateVersions).isEmpty();
    }

    private String versionOf(String name) {
        return name.substring(1, name.indexOf("__"));
    }
}
