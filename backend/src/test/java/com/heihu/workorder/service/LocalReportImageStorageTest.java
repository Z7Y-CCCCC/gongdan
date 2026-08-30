package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalReportImageStorageTest {

    @TempDir
    Path uploadDir;

    @Test
    void storeRejectsPathTraversal() {
        LocalReportImageStorage storage = new LocalReportImageStorage(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.png",
                "image/png",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> storage.store("../escape.png", file))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void storeLoadAndDeleteUseConfiguredRoot() throws Exception {
        LocalReportImageStorage storage = new LocalReportImageStorage(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.png",
                "image/png",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        storage.store("stored.png", file);

        assertThat(Files.exists(uploadDir.resolve("stored.png"))).isTrue();
        assertThat(storage.load("stored.png").exists()).isTrue();
        assertThat(storage.delete("stored.png")).isTrue();
        assertThat(Files.exists(uploadDir.resolve("stored.png"))).isFalse();
    }
}
