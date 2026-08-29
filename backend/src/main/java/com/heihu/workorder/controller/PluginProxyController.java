package com.heihu.workorder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.PluginCircuitBreaker;
import com.heihu.workorder.service.PluginControlService;
import com.heihu.workorder.service.PluginControlService.PluginTarget;
import com.heihu.workorder.service.TokenService;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Same-origin gateway for remote plugins. The target URL is resolved from the active
 * version manifest after tenant, role and rollout checks; callers cannot supply it.
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginProxyController {

    private final PluginControlService pluginControlService;
    private final TokenService tokenService;
    private final PluginCircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public PluginProxyController(
            PluginControlService pluginControlService,
            TokenService tokenService,
            PluginCircuitBreaker circuitBreaker,
            ObjectMapper objectMapper,
            @Value("${app.plugin.proxy.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.plugin.proxy.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        this.pluginControlService = pluginControlService;
        this.tokenService = tokenService;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @RequestMapping(value = "/{pluginKey}/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
    })
    public ResponseEntity<byte[]> proxy(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return ResponseEntity.noContent().build();
        }
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        PluginTarget target = pluginControlService.resolveBackendTarget(
                currentUser.getFactoryId(), currentUser.getUserId(), currentUser.getRole().name(), pluginKey);
        String breakerKey = currentUser.getFactoryId() + ":" + pluginKey;
        circuitBreaker.beforeCall(breakerKey);

        URI targetUri = buildTargetUri(target.getBackendUrl(), pluginKey, request);
        HttpHeaders headers = copyRequestHeaders(request);
        headers.set("X-Plugin-Key", target.getPluginKey());
        headers.set("X-Plugin-Version", target.getVersion());
        headers.set("X-Plugin-Factory-Id", String.valueOf(currentUser.getFactoryId()));
        headers.set("X-Plugin-User-Id", String.valueOf(currentUser.getUserId()));
        headers.set("X-Plugin-Role", currentUser.getRole().name());
        HttpEntity<byte[]> entity = new HttpEntity<>(body == null ? new byte[0] : body, headers);

        try {
            ResponseEntity<byte[]> upstream = createRestTemplate().exchange(
                    targetUri, HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
            if (upstream.getStatusCode().is5xxServerError()) {
                circuitBreaker.recordFailure(breakerKey);
            } else {
                circuitBreaker.recordSuccess(breakerKey);
            }
            HttpHeaders responseHeaders = copyResponseHeaders(upstream.getHeaders());
            return new ResponseEntity<>(upstream.getBody(), responseHeaders, upstream.getStatusCode());
        } catch (RestClientException ex) {
            circuitBreaker.recordFailure(breakerKey);
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "插件服务暂时不可用");
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        return restTemplate;
    }

    private URI buildTargetUri(String backendUrl, String pluginKey, HttpServletRequest request) {
        String prefix = "/api/plugins/" + pluginKey;
        String requestUri = request.getRequestURI();
        String suffix = requestUri.startsWith(prefix) ? requestUri.substring(prefix.length()) : "/";
        if (suffix.isEmpty()) suffix = "/";
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(backendUrl).path(suffix);
        if (request.getQueryString() != null) {
            builder.query(request.getQueryString());
        }
        return builder.build(true).toUri();
    }

    private HttpHeaders copyRequestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return headers;
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (isHopByHopHeader(name) || "host".equalsIgnoreCase(name)
                    || "content-length".equalsIgnoreCase(name)) continue;
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) headers.add(name, values.nextElement());
        }
        return headers;
    }

    private HttpHeaders copyResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!isHopByHopHeader(name)) headers.put(name, values);
        });
        return headers;
    }

    private boolean isHopByHopHeader(String name) {
        return "connection".equalsIgnoreCase(name)
                || "keep-alive".equalsIgnoreCase(name)
                || "proxy-authenticate".equalsIgnoreCase(name)
                || "proxy-authorization".equalsIgnoreCase(name)
                || "te".equalsIgnoreCase(name)
                || "trailer".equalsIgnoreCase(name)
                || "transfer-encoding".equalsIgnoreCase(name)
                || "upgrade".equalsIgnoreCase(name);
    }

    private ResponseEntity<byte[]> errorResponse(HttpStatus status, String message) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(Collections.singletonMap("message", message));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, headers, status);
    }
}
