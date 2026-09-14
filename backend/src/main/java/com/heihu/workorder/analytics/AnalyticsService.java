package com.heihu.workorder.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.service.AuthenticatedUser;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

/**
 * Issues short-lived Superset guest tokens on behalf of the already authenticated
 * work-order user. Superset credentials never leave the backend.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final Pattern DASHBOARD_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Duration ACCESS_TOKEN_CACHE_TTL = Duration.ofMinutes(4);

    private final RestTemplate restTemplate;
    private final String supersetUrl;
    private final String username;
    private final String password;
    private final String dashboardId;

    private volatile CachedAccessToken cachedAccessToken;

    @Autowired
    public AnalyticsService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.analytics.superset-url:}") String configuredSupersetUrl,
            @Value("${app.analytics.superset-username:}") String username,
            @Value("${app.analytics.superset-password:}") String password,
            @Value("${app.analytics.dashboard-id:}") String dashboardId,
            @Value("${app.analytics.timeout-seconds:5}") int timeoutSeconds
    ) {
        this.supersetUrl = normalizeBaseUrl(configuredSupersetUrl);
        this.username = normalize(username);
        this.password = password == null ? "" : password;
        this.dashboardId = normalize(dashboardId);
        int boundedTimeout = Math.max(1, Math.min(timeoutSeconds, 30));
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(boundedTimeout))
                .setReadTimeout(Duration.ofSeconds(boundedTimeout))
                .build();
    }

    /** Package-private constructor for focused unit tests. */
    AnalyticsService(
            RestTemplate restTemplate,
            String supersetUrl,
            String username,
            String password,
            String dashboardId
    ) {
        this.restTemplate = restTemplate;
        this.supersetUrl = normalizeBaseUrl(supersetUrl);
        this.username = normalize(username);
        this.password = password == null ? "" : password;
        this.dashboardId = normalize(dashboardId);
    }

    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", isConfigured());
        result.put("provider", "apache-superset");
        if (isConfigured()) {
            result.put("supersetDomain", supersetUrl);
            result.put("dashboardId", dashboardId);
        }
        return result;
    }

    public Map<String, Object> guestToken(AuthenticatedUser user) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "分析报表尚未配置，请联系系统管理员");
        }

        String accessToken = accessToken();
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> guestUser = new LinkedHashMap<>();
        guestUser.put("username", user.getFactoryCode() + ":" + user.getUserId());
        guestUser.put("first_name", StringUtils.hasText(user.getName()) ? user.getName() : "用户");
        guestUser.put("last_name", user.getFactoryCode());
        payload.put("user", guestUser);

        List<Map<String, String>> resources = new ArrayList<>();
        Map<String, String> resource = new LinkedHashMap<>();
        resource.put("type", "dashboard");
        resource.put("id", dashboardId);
        resources.add(resource);
        payload.put("resources", resources);

        // All datasets in the embedded dashboard must expose factory_id. Keep the
        // clause numeric so a factory code or user-provided value cannot become SQL.
        List<Map<String, String>> rls = new ArrayList<>();
        Map<String, String> rule = new LinkedHashMap<>();
        rule.put("clause", "factory_id = " + user.getFactoryId());
        rls.add(rule);
        payload.put("rls", rls);

        try {
            HttpHeaders headers = jsonHeaders();
            headers.setBearerAuth(accessToken);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    supersetUrl + "/api/v1/security/guest_token/",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    JsonNode.class
            );
            String token = text(response.getBody(), "token");
            if (!StringUtils.hasText(token)) {
                throw upstreamFailure("Superset 未返回嵌入 token");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("expiresInSeconds", 300);
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Superset guest token request failed: {}", ex.getMessage());
            throw upstreamFailure("分析报表服务暂时不可用");
        }
    }

    private boolean isConfigured() {
        return StringUtils.hasText(supersetUrl)
                && StringUtils.hasText(username)
                && StringUtils.hasText(password)
                && StringUtils.hasText(dashboardId)
                && DASHBOARD_ID.matcher(dashboardId).matches();
    }

    private String accessToken() {
        CachedAccessToken current = cachedAccessToken;
        if (current != null && Instant.now().isBefore(current.expiresAt)) {
            return current.value;
        }

        synchronized (this) {
            current = cachedAccessToken;
            if (current != null && Instant.now().isBefore(current.expiresAt)) {
                return current.value;
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("username", username);
            payload.put("password", password);
            payload.put("provider", "db");
            payload.put("refresh", "true");
            try {
                ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                        supersetUrl + "/api/v1/security/login",
                        new HttpEntity<>(payload, jsonHeaders()),
                        JsonNode.class
                );
                String token = text(response.getBody(), "access_token");
                if (!StringUtils.hasText(token)) {
                    throw upstreamFailure("Superset 登录未返回 access token");
                }
                CachedAccessToken fresh = new CachedAccessToken(
                        token,
                        Instant.now().plus(ACCESS_TOKEN_CACHE_TTL)
                );
                cachedAccessToken = fresh;
                return fresh.value;
            } catch (ApiException ex) {
                throw ex;
            } catch (RestClientException ex) {
                log.warn("Superset service login failed: {}", ex.getMessage());
                throw upstreamFailure("分析报表服务暂时不可用");
            }
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String text(JsonNode body, String field) {
        return body == null ? "" : body.path(field).asText("");
    }

    private ApiException upstreamFailure(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, message);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) return "";
        try {
            URI uri = URI.create(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                return "";
            }
            return normalized.replaceAll("/+$", "");
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CachedAccessToken {
        private final String value;
        private final Instant expiresAt;

        private CachedAccessToken(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
