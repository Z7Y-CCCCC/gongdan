package com.heihu.workorder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.service.AuthService;
import com.heihu.workorder.repository.CustomFieldAttachmentRepository;
import com.heihu.workorder.model.CustomFieldAttachment;
import com.heihu.workorder.repository.WorkOrderReportImageRepository;
import com.heihu.workorder.model.WorkOrderReportImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.cache.CacheManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Tomcat and the production multipart limits, with all business data isolated in H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:functional_upload_http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.cache.type=simple", "logging.file.name=output/functional-upload-http-test.log"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FunctionalUploadHttpIntegrationTest {
    private static final Path UPLOAD_ROOT = temporaryRoot();
    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @Autowired CustomFieldAttachmentRepository attachments;
    @Autowired WorkOrderReportImageRepository reportImages;
    @Autowired CacheManager cache;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.upload.report-image-dir", () -> UPLOAD_ROOT.toString());
        // Apply the production HTTP limits without enabling production credentials or providers.
        registry.add("spring.servlet.multipart.max-file-size", () -> productionMultipart("spring.servlet.multipart.max-file-size"));
        registry.add("spring.servlet.multipart.max-request-size", () -> productionMultipart("spring.servlet.multipart.max-request-size"));
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

    @Test void malformedPartMimeTypeDoesNotMakeAPersistedFileUndownloadable() {
        String token = login();
        byte[] content = "persisted content".getBytes(StandardCharsets.UTF_8);
        JsonNode record = rawUpload(token, "doc.txt", "not-a-type", content, "file");
        assertThat(record.path("contentType").asText()).isEqualTo("application/octet-stream");
        ResponseEntity<byte[]> downloaded = download(token, record.path("id").asLong());
        assertThat(downloaded.getStatusCodeValue()).isEqualTo(200);
        assertThat(downloaded.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(downloaded.getBody()).containsExactly(content);
    }

    @Test void historicalInvalidMimeMetadataDoesNotBlockDownloadingTheFile() {
        String token = login();
        byte[] content = "historical content".getBytes(StandardCharsets.UTF_8);
        JsonNode record = rawUpload(token, "doc.txt", "text/plain", content, "file");
        CustomFieldAttachment saved = attachments.findById(record.path("id").asLong()).get();
        saved.setContentType("not-a-type");
        attachments.saveAndFlush(saved);
        ResponseEntity<byte[]> downloaded = download(token, saved.getId());
        assertThat(downloaded.getStatusCodeValue()).isEqualTo(200);
        assertThat(downloaded.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(downloaded.getBody()).containsExactly(content);
    }

    @Test void unicodeDownloadFilenameSurvivesActualTomcatHeaderEncoding() {
        String token = login();
        byte[] content = "unicode name".getBytes(StandardCharsets.UTF_8);
        JsonNode record = rawUpload(token, "report.txt", "text/plain", content, "file");
        CustomFieldAttachment saved = attachments.findById(record.path("id").asLong()).get();
        saved.setOriginalFileName("工单附件-验收📦.txt");
        attachments.saveAndFlush(saved);
        ResponseEntity<byte[]> downloaded = download(token, saved.getId());
        assertThat(downloaded.getStatusCodeValue()).isEqualTo(200);
        String header = downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).isNotNull();
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(saved.getOriginalFileName());
        assertThat(downloaded.getBody()).containsExactly(content);
    }

    @Test void imageWithoutPartMimeTypeDownloadsWithAValidatedConcreteImageType() {
        String token = login();
        byte[] content = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d};
        JsonNode record = rawUpload(token, "report.png", null, content, "image");
        ResponseEntity<byte[]> downloaded = download(token, record.path("id").asLong());
        assertThat(downloaded.getStatusCodeValue()).isEqualTo(200);
        assertThat(downloaded.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
        assertThat(downloaded.getBody()).containsExactly(content);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/mobile/reports/images/", "/api/reports/images/"})
    void bothReportImageEndpointsPreserveUnicodeFilenames(String endpoint) {
        String token = login();
        byte[] content = pngHeader();
        JsonNode uploaded = rawUpload("/api/mobile/reports/images", token, "photo.png", "image/png", content, "image");
        WorkOrderReportImage saved = reportImages.findById(uploaded.path("id").asLong()).get();
        saved.setOriginalFileName("报工现场-验收📷.png");
        reportImages.saveAndFlush(saved);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
        ResponseEntity<byte[]> downloaded = http.exchange(endpoint + saved.getId() + "/file",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(downloaded.getStatusCodeValue()).isEqualTo(200);
        assertThat(downloaded.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(downloaded.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(ContentDisposition.parse(downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).getFilename())
                .isEqualTo(saved.getOriginalFileName());
        assertThat(downloaded.getBody()).containsExactly(content);
    }

    @Test void reportImagesStillHaveTheirOwnTenMegabyteLimit() {
        String token = login();
        byte[] content = new byte[12 * 1024 * 1024];
        System.arraycopy(pngHeader(), 0, content, 0, pngHeader().length);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders part = new HttpHeaders(); part.setContentType(MediaType.IMAGE_PNG);
        body.add("file", new HttpEntity<>(new ByteArrayResource(content) {
            @Override public String getFilename() { return "qa-12mb.png"; }
        }, part));
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> uploaded = http.postForEntity("/api/mobile/reports/images", new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(uploaded.getStatusCodeValue()).isEqualTo(400);
        assertThat(uploaded.getBody().path("message").asText()).contains("10MB");
    }

    @Test void renamingAUnitRefreshesCachedSalesAndBomHeadersAndLines() {
        assertThat(cache.getClass().getName()).contains("ConcurrentMapCacheManager");
        String token = login(), unique = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
        long unit = jsonRequest("/api/units", token, HttpMethod.POST, "{\"name\":\"kg-" + unique + "\"}").path("id").asLong();
        long product = jsonRequest("/api/products", token, HttpMethod.POST,
                "{\"code\":\"F-" + unique + "\",\"name\":\"finished\",\"unitId\":" + unit + ",\"attribute\":\"自制\"}").path("id").asLong();
        long material = jsonRequest("/api/products", token, HttpMethod.POST,
                "{\"code\":\"M-" + unique + "\",\"name\":\"material\",\"unitId\":" + unit + ",\"attribute\":\"外购\"}").path("id").asLong();
        String salesCode = "S-" + unique, bomCode = "B-" + unique;
        jsonRequest("/api/sales-orders", token, HttpMethod.POST, "{\"code\":\"" + salesCode
                + "\",\"customerName\":\"fixture\",\"items\":[{\"productId\":" + product + ",\"quantity\":1}]}");
        jsonRequest("/api/boms", token, HttpMethod.POST, "{\"code\":\"" + bomCode
                + "\",\"productId\":" + product + ",\"outputQuantity\":1,\"items\":[{\"productId\":" + material + ",\"quantity\":1}]}");
        String salesRoute = "/api/sales-orders?code=" + salesCode, bomRoute = "/api/boms?code=" + bomCode;
        assertThat(jsonRequest(salesRoute, token, HttpMethod.GET, null).path("items").get(0).path("items").get(0).path("unitName").asText())
                .isEqualTo("kg-" + unique);
        assertThat(jsonRequest(bomRoute, token, HttpMethod.GET, null).path("items").get(0).path("unitName").asText())
                .isEqualTo("kg-" + unique);
        assertThat(cache.getCache("sales.orders.list")).isNotNull();
        assertThat(cache.getCache("boms.list")).isNotNull();
        assertThat((java.util.Map<?, ?>) cache.getCache("sales.orders.list").getNativeCache()).isNotEmpty();
        assertThat((java.util.Map<?, ?>) cache.getCache("boms.list").getNativeCache()).isNotEmpty();
        jsonRequest("/api/units/" + unit, token, HttpMethod.PUT, "{\"name\":\"千克-" + unique + "\"}");
        JsonNode sales = jsonRequest(salesRoute, token, HttpMethod.GET, null).path("items").get(0);
        JsonNode bom = jsonRequest(bomRoute, token, HttpMethod.GET, null).path("items").get(0);
        assertThat(sales.path("items").get(0).path("unitName").asText()).isEqualTo("千克-" + unique);
        assertThat(bom.path("unitName").asText()).isEqualTo("千克-" + unique);
        assertThat(bom.path("items").get(0).path("unitName").asText()).isEqualTo("千克-" + unique);
    }

    private JsonNode jsonRequest(String path, String token, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCodeValue()).as(path + ": " + response).isEqualTo(200);
        return response.getBody();
    }

    private static String productionMultipart(String key) {
        try {
            String configured = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties")).getProperty(key);
            return new MockEnvironment().resolveRequiredPlaceholders(configured);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] pngHeader() {
        return new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d};
    }

    private String login() {
        LoginRequest login = new LoginRequest(); login.setFactoryCode("TEST"); login.setUsername("admin"); login.setPassword("123456");
        return auth.login(login).getToken();
    }

    private JsonNode rawUpload(String token, String name, String contentType, byte[] content, String fieldType) {
        return rawUpload("/api/custom-field-attachments", token, name, contentType, content, fieldType);
    }

    private JsonNode rawUpload(String path, String token, String name, String contentType, byte[] content, String fieldType) {
        String boundary = "FunctionalUploadBoundary";
        String prefix = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\n"
                + (contentType == null ? "" : "Content-Type: " + contentType + "\r\n") + "\r\n";
        String suffix = "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"fieldType\"\r\n\r\n" + fieldType
                + "\r\n--" + boundary + "--\r\n";
        byte[] before = prefix.getBytes(StandardCharsets.UTF_8), after = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] multipart = new byte[before.length + content.length + after.length];
        System.arraycopy(before, 0, multipart, 0, before.length);
        System.arraycopy(content, 0, multipart, before.length, content.length);
        System.arraycopy(after, 0, multipart, before.length + content.length, after.length);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
        headers.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary));
        ResponseEntity<JsonNode> uploaded = http.postForEntity(path, new HttpEntity<>(multipart, headers), JsonNode.class);
        assertThat(uploaded.getStatusCodeValue()).as(uploaded.toString()).isEqualTo(200);
        return uploaded.getBody();
    }

    private ResponseEntity<byte[]> download(String token, long id) {
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
        return http.exchange("/api/custom-field-attachments/" + id + "/file", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }
    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("gongdan-functional-upload-"); }
        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
