package com.heihu.workorder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.service.AuthService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises actual Tomcat multipart parsing, which MockMvc bypasses. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:functional_upload_http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.cache.type=none", "logging.file.name=output/functional-upload-http-test.log"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FunctionalUploadHttpIntegrationTest {
    private static final Path UPLOAD_ROOT = temporaryRoot();
    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.upload.report-image-dir", () -> UPLOAD_ROOT.toString());
    }

    @AfterAll static void cleanupOwnedFixture() throws Exception {
        Path directory = UPLOAD_ROOT.toAbsolutePath().normalize();
        Path temp = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!directory.startsWith(temp) || !directory.getFileName().toString().startsWith("gongdan-functional-upload-")) throw new IllegalStateException("Unexpected fixture directory");
        try (java.util.stream.Stream<Path> files = Files.walk(directory)) {
            for (Path file : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator) Files.deleteIfExists(file);
        }
    }

    @Test void validTwelveMegabyteAttachmentUploadsAndDownloadsThroughRealServlet() {
        LoginRequest login = new LoginRequest(); login.setFactoryCode("TEST"); login.setUsername("admin"); login.setPassword("123456");
        String token = auth.login(login).getToken();
        byte[] content = new byte[12 * 1024 * 1024]; Arrays.fill(content, (byte) 37);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) { @Override public String getFilename() { return "qa-12mb.bin"; } });
        body.add("fieldType", "file"); body.add("scope", "workorder");
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> upload = http.postForEntity("/api/custom-field-attachments", new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(upload.getStatusCodeValue()).as(upload.toString()).isEqualTo(200);
        JsonNode record = upload.getBody();
        assertThat(record.path("fileSize").asLong()).isEqualTo(content.length);
        HttpHeaders downloadHeaders = new HttpHeaders(); downloadHeaders.setBearerAuth(token);
        ResponseEntity<byte[]> downloaded = http.exchange("/api/custom-field-attachments/" + record.path("id").asLong() + "/file",
                HttpMethod.GET, new HttpEntity<>(downloadHeaders), byte[].class);
        assertThat(downloaded.getStatusCodeValue()).isEqualTo(200);
        assertThat(downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(downloaded.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(downloaded.getBody()).containsExactly(content);
    }
    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("gongdan-functional-upload-"); }
        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
