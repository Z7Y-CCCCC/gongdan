package com.heihu.workorder.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.PluginCircuitBreaker;
import com.heihu.workorder.service.PluginControlService;
import com.heihu.workorder.service.PluginControlService.PluginTarget;
import com.heihu.workorder.service.TokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class PluginProxyControllerTest {

    private PluginControlService pluginControlService;
    private PluginProxyController controller;
    private HttpServer server;
    private String backendUrl;

    @BeforeEach
    void setUp() throws IOException {
        pluginControlService = mock(PluginControlService.class);
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.requireUser("Bearer core-secret")).thenReturn(
                new AuthenticatedUser(9L, 7L, "HHGD", Role.ADMIN, "Admin"));
        controller = new PluginProxyController(
                pluginControlService,
                tokenService,
                new PluginCircuitBreaker(5, 30000, 30000),
                new ObjectMapper(),
                1000,
                1000,
                256,
                "test-plugin-gateway-secret"
        );
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backendUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void proxyStripsCoreCredentialsAndAddsBoundedPluginContext() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> factoryId = new AtomicReference<>();
        AtomicReference<String> config = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        AtomicReference<String> bodyHash = new AtomicReference<>();
        server.createContext("/api/plugin/equipment-inspection/ping", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            factoryId.set(exchange.getRequestHeaders().getFirst("X-Plugin-Factory-Id"));
            String encoded = exchange.getRequestHeaders().getFirst("X-Plugin-Config-Base64");
            config.set(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
            signature.set(exchange.getRequestHeaders().getFirst("X-Plugin-Gateway-Signature"));
            timestamp.set(exchange.getRequestHeaders().getFirst("X-Plugin-Gateway-Timestamp"));
            bodyHash.set(exchange.getRequestHeaders().getFirst("X-Plugin-Body-SHA256"));
            respond(exchange, 200, "{\"ok\":true}");
        });
        server.start();
        stubTarget("{\"inspectionIntervalDays\":7}");

        ResponseEntity<byte[]> response = controller.proxy(
                "Bearer core-secret",
                "equipment-inspection",
                request("GET", "/api/plugins/equipment-inspection/api/plugin/equipment-inspection/ping"),
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(authorization.get());
        assertEquals("7", factoryId.get());
        assertEquals("{\"inspectionIntervalDays\":7}", config.get());
        assertTrue(signature.get() != null && signature.get().matches("[0-9a-f]{64}"));
        String canonical = String.join("\n", "GET", "/api/plugin/equipment-inspection/ping",
                "equipment-inspection", "1.0.0", "7", "9", "ADMIN", timestamp.get(), bodyHash.get());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-plugin-gateway-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertEquals(hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))), signature.get());
    }

    @Test
    void proxyRejectsRedirectsArbitraryPathsAndOversizedBodies() throws Exception {
        server.createContext("/api/plugin/equipment-inspection/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1/private");
            respond(exchange, 302, "");
        });
        server.start();
        stubTarget("{}");

        ResponseEntity<byte[]> redirect = controller.proxy(
                "Bearer core-secret",
                "equipment-inspection",
                request("GET", "/api/plugins/equipment-inspection/api/plugin/equipment-inspection/redirect"),
                null
        );
        assertEquals(HttpStatus.BAD_GATEWAY, redirect.getStatusCode());

        ApiException wrongPath = assertThrows(ApiException.class, () -> controller.proxy(
                "Bearer core-secret",
                "equipment-inspection",
                request("GET", "/api/plugins/equipment-inspection/actuator/health"),
                null
        ));
        assertEquals(HttpStatus.NOT_FOUND, wrongPath.getStatus());

        byte[] oversized = new byte[257];
        ResponseEntity<byte[]> tooLarge = controller.proxy(
                "Bearer core-secret",
                "equipment-inspection",
                request("POST", "/api/plugins/equipment-inspection/api/plugin/equipment-inspection/inspections"),
                oversized
        );
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, tooLarge.getStatusCode());
        assertTrue(new String(tooLarge.getBody(), StandardCharsets.UTF_8).contains("请求内容过大"));
    }

    private void stubTarget(String configJson) {
        when(pluginControlService.resolveBackendTarget(7L, 9L, "ADMIN", "equipment-inspection"))
                .thenReturn(new PluginTarget("equipment-inspection", "1.0.0", backendUrl, configJson));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer core-secret");
        request.addHeader("Cookie", "SESSION=private");
        return request;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }
}
