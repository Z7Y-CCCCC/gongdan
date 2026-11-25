package com.heihu.workorder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.heihu.workorder.dto.AuthResponse;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.service.AuthService;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiRouteSmokeTest {

    // Includes the explicitly added, idempotent POST /api/auth/logout.
    private static final int EXPECTED_ROUTE_METHODS = 192;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private AuthService authService;

    private String adminToken;
    private String superAdminToken;

    @BeforeEach
    void authenticateIsolatedUsers() {
        adminToken = login("TEST", "admin", "123456");
        superAdminToken = login("ADMIN", "admin", "test-super-admin-pass");
    }

    @Test
    void everyApiRouteIsMappedAndDoesNotReturnServerError() throws Exception {
        List<Route> routes = discoverRoutes();
        Map<Integer, Integer> statusCounts = new LinkedHashMap<>();
        List<String> serverErrors = new ArrayList<>();

        for (Route route : routes) {
            MockHttpServletRequestBuilder builder = request(
                            HttpMethod.valueOf(route.method), concretePath(route.pattern))
                            .contentType("application/json")
                            .accept("application/json")
                            .content("{}")
                            .param("moduleName", "SOP")
                            .param("module", "SMOKE")
                            .param("status", "ALL")
                            .param("cacheName", "smoke-missing")
                            .param("key", "smoke-missing")
                            .param("range", "TODAY")
                            .param("page", "1")
                            .param("size", "20");
            String token = tokenFor(route.pattern);
            if (token != null) builder.header("Authorization", "Bearer " + token);
            MvcResult result = mockMvc.perform(builder).andReturn();
            int status = result.getResponse().getStatus();
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
            System.out.printf("API_SMOKE %s %-80s -> %d%n", route.method, route.pattern, status);
            if (status >= 500) {
                serverErrors.add(route.method + " " + route.pattern + " -> " + status);
            }
        }

        System.out.println("API_SMOKE_SUMMARY routes=" + routes.size() + " statuses=" + statusCounts);
        assertThat(serverErrors)
                .as("Every API route must survive a minimal smoke request without a 5xx response")
                .isEmpty();
        assertThat(routes.size())
                .as("Controller route count changed. Review and update the smoke inventory intentionally.")
                .isEqualTo(EXPECTED_ROUTE_METHODS);
    }

    private List<Route> discoverRoutes() {
        List<Route> routes = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Package owner = handler.getBeanType().getPackage();
            if (owner == null || !owner.getName().startsWith("com.heihu.workorder")) continue;

            Set<String> patterns = entry.getKey().getPatternsCondition() == null
                    ? entry.getKey().getPathPatternsCondition().getPatternValues()
                    : entry.getKey().getPatternsCondition().getPatterns();
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/")) continue;
                if (methods.isEmpty()) {
                    routes.add(new Route("GET", pattern));
                } else {
                    for (RequestMethod method : methods) {
                        routes.add(new Route(method.name(), pattern));
                    }
                }
            }
        }
        Collections.sort(routes, Comparator.comparing((Route item) -> item.pattern).thenComparing(item -> item.method));
        return routes;
    }

    private String concretePath(String pattern) {
        String path = pattern
                .replace("{pluginKey}", "equipment-inspection")
                .replace("{version}", "1.0.0")
                .replace("{code}", "SMOKE")
                .replace("{key}", "smoke")
                .replace("{id}", "999999999")
                .replace("{userId}", "999999999")
                .replace("{peerId}", "999999999")
                .replace("{imageId}", "999999999")
                .replace("{attachmentId}", "999999999");
        path = path.replace("{action:enable|disable|archive|restore}", "enable");
        path = path.replaceAll("\\{[^/]+}", "999999999");
        return path.replace("**", "api/plugin/equipment-inspection/ping");
    }

    private String tokenFor(String pattern) {
        if (pattern.startsWith("/api/auth/") || pattern.startsWith("/api/mobile/auth/")) return null;
        if (pattern.startsWith("/api/super-admin/") || pattern.startsWith("/api/ureport/")) return superAdminToken;
        return adminToken;
    }

    private String login(String factoryCode, String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setFactoryCode(factoryCode);
        request.setUsername(username);
        request.setPassword(password);
        AuthResponse response = authService.login(request);
        return response.getToken();
    }

    private static final class Route {
        private final String method;
        private final String pattern;

        private Route(String method, String pattern) {
            this.method = method;
            this.pattern = pattern;
        }
    }
}
