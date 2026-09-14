package com.heihu.plugins.inspection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "plugin.gateway.secret=test-plugin-gateway-secret")
@AutoConfigureMockMvc
class EquipmentInspectionApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void directApiAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/plugin/equipment-inspection/ping"))
                .andExpect(status().isForbidden());
    }

    @Test
    void matrixParametersCannotBypassGatewayAuthentication() throws Exception {
        mockMvc.perform(get("/api/plugin;ignored/equipment-inspection/ping")
                        .header("X-Plugin-Factory-Id", "99")
                        .header("X-Plugin-User-Id", "999")
                        .header("X-Plugin-Role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void gatewayContextAndFactoryIsolationWork() throws Exception {
        String config = Base64.getEncoder().encodeToString(
                "{\"inspectionIntervalDays\":7}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/plugin/equipment-inspection/ping")
                        .headers(gatewayHeaders("GET", "/api/plugin/equipment-inspection/ping", "1", "11", "", config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factoryId").value(1))
                .andExpect(jsonPath("$.userId").value(11))
                .andExpect(jsonPath("$.config.inspectionIntervalDays").value(7));

        mockMvc.perform(post("/api/plugin/equipment-inspection/inspections")
                        .headers(gatewayHeaders("POST", "/api/plugin/equipment-inspection/inspections", "1", "11",
                                "{\"equipmentCode\":\"EQ-01\",\"result\":\"正常\",\"remark\":\"首轮\"}"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentCode\":\"EQ-01\",\"result\":\"正常\",\"remark\":\"首轮\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipmentCode").value("EQ-01"));

        mockMvc.perform(get("/api/plugin/equipment-inspection/inspections")
                        .headers(gatewayHeaders("GET", "/api/plugin/equipment-inspection/inspections", "1", "11", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentCode").value("EQ-01"));

        mockMvc.perform(get("/api/plugin/equipment-inspection/inspections")
                        .headers(gatewayHeaders("GET", "/api/plugin/equipment-inspection/inspections", "2", "21", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void forgedGatewayHeadersAreRejected() throws Exception {
        mockMvc.perform(get("/api/plugin/equipment-inspection/ping")
                        .header("X-Plugin-Key", "equipment-inspection")
                        .header("X-Plugin-Factory-Id", "99")
                        .header("X-Plugin-User-Id", "999")
                        .header("X-Plugin-Role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedRequestWithChangedBodyIsRejected() throws Exception {
        String signedBody = "{\"equipmentCode\":\"EQ-01\",\"result\":\"正常\"}";
        String changedBody = "{\"equipmentCode\":\"EQ-99\",\"result\":\"正常\"}";
        mockMvc.perform(post("/api/plugin/equipment-inspection/inspections")
                        .headers(gatewayHeaders("POST", "/api/plugin/equipment-inspection/inspections", "1", "11", signedBody))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changedBody))
                .andExpect(status().isForbidden());
    }

    private org.springframework.http.HttpHeaders gatewayHeaders(
            String method, String path, String factoryId, String userId, String body) throws Exception {
        return gatewayHeaders(method, path, factoryId, userId, body, "");
    }

    @Test
    void tamperedOrRemovedSignedConfigurationIsRejected() throws Exception {
        String path = "/api/plugin/equipment-inspection/ping";
        org.springframework.http.HttpHeaders changed = gatewayHeaders("GET", path, "1", "11", "", "e30=");
        changed.set("X-Plugin-Config-Base64", "eyJieXBhc3MiOnRydWV9");
        mockMvc.perform(get(path).headers(changed)).andExpect(status().isForbidden());
        org.springframework.http.HttpHeaders removed = gatewayHeaders("GET", path, "1", "11", "", "e30=");
        removed.remove("X-Plugin-Config-Base64");
        mockMvc.perform(get(path).headers(removed)).andExpect(status().isForbidden());
    }

    @Test
    void outOfRangeSignedFactoryIdIsRejectedBeforeControllerParsing() throws Exception {
        String path = "/api/plugin/equipment-inspection/ping";
        mockMvc.perform(get(path).headers(gatewayHeaders("GET", path, "9999999999999999999", "11", "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicHealthCheckRemainsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private org.springframework.http.HttpHeaders gatewayHeaders(
            String method, String path, String factoryId, String userId, String body, String configBase64) throws Exception {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Plugin-Key", "equipment-inspection");
        headers.set("X-Plugin-Factory-Id", factoryId);
        headers.set("X-Plugin-User-Id", userId);
        headers.set("X-Plugin-Role", "ADMIN");
        headers.set("X-Plugin-Version", "1.0.0");
        if (!configBase64.isEmpty()) headers.set("X-Plugin-Config-Base64", configBase64);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String bodyHash = hex(MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(StandardCharsets.UTF_8)));
        String canonical = String.join("\n", method, path, "equipment-inspection", "1.0.0",
                factoryId, userId, "ADMIN", timestamp, bodyHash, configBase64);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-plugin-gateway-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
