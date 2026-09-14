package com.heihu.plugins.quality;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "plugin.gateway.secret=test-plugin-gateway-secret")
@AutoConfigureMockMvc
class QualityManagementApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void directApiAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/plugin/quality-management/ping"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedGatewayRequestIsAccepted() throws Exception {
        String path = "/api/plugin/quality-management/ping";
        mockMvc.perform(get(path).headers(gatewayHeaders("GET", path, "1", "11")))
                .andExpect(status().isOk());
    }

    @Test
    void matrixParametersCannotBypassGatewayAuthentication() throws Exception {
        mockMvc.perform(get("/api/plugin;ignored/quality-management/ping"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordsAreIsolatedBetweenFactories() throws Exception {
        String path = "/api/plugin/quality-management/records";
        String body = "{\"issue\":\"private factory issue\",\"status\":\"OPEN\"}";
        mockMvc.perform(post(path).headers(gatewayHeaders("POST", path, "101", "11", body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(get(path).headers(gatewayHeaders("GET", path, "101", "11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].issue").value("private factory issue"));
        mockMvc.perform(get(path).headers(gatewayHeaders("GET", path, "202", "22")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private HttpHeaders gatewayHeaders(String method, String path, String factoryId, String userId) throws Exception {
        return gatewayHeaders(method, path, factoryId, userId, "");
    }

    @Test
    void addingUnsignedConfigurationIsRejected() throws Exception {
        String path = "/api/plugin/quality-management/ping";
        mockMvc.perform(get(path).headers(gatewayHeaders("GET", path, "1", "11"))
                        .header("X-Plugin-Config-Base64", "e30="))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicHealthCheckRemainsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private HttpHeaders gatewayHeaders(String method, String path, String factoryId, String userId, String body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String bodyHash = hex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        String canonical = String.join("\n", method, path, "quality-management", "1.0.0",
                factoryId, userId, "ADMIN", timestamp, bodyHash, "");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-plugin-gateway-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        headers.set("X-Plugin-Key", "quality-management");
        headers.set("X-Plugin-Version", "1.0.0");
        headers.set("X-Plugin-Factory-Id", factoryId);
        headers.set("X-Plugin-User-Id", userId);
        headers.set("X-Plugin-Role", "ADMIN");
        headers.set("X-Plugin-Body-SHA256", bodyHash);
        headers.set("X-Plugin-Gateway-Timestamp", timestamp);
        headers.set("X-Plugin-Gateway-Signature", hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        return headers;
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }
}
