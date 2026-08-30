package com.heihu.workorder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * Records API write operations, failures, and slow requests without logging bodies,
 * credentials, authorization headers, or query parameters.
 */
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final long slowRequestThresholdMs;

    public ApiRequestLoggingFilter(
            @Value("${app.logging.slow-request-threshold-ms:1000}") long slowRequestThresholdMs
    ) {
        this.slowRequestThresholdMs = Math.max(1L, slowRequestThresholdMs);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            int status = response.getStatus();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String remoteAddress = clientAddress(request);
            String message = "API request requestId={}, method={}, path={}, status={}, durationMs={}, remoteAddress={} ";
            if (failure != null || status >= 500 || durationMs >= slowRequestThresholdMs) {
                log.warn(message, requestId, method, uri, status, durationMs, remoteAddress, failure);
            } else if (isWriteMethod(method)) {
                log.info(message, requestId, method, uri, status, durationMs, remoteAddress);
            } else {
                log.debug(message, requestId, method, uri, status, durationMs, remoteAddress);
            }
        }
    }

    private boolean isWriteMethod(String method) {
        String normalized = method == null ? "" : method.toUpperCase(Locale.ROOT);
        return "POST".equals(normalized)
                || "PUT".equals(normalized)
                || "PATCH".equals(normalized)
                || "DELETE".equals(normalized);
    }

    private String normalizeRequestId(String requestId) {
        if (requestId != null && requestId.matches("[A-Za-z0-9._-]{8,64}")) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int separator = forwarded.indexOf(',');
            return (separator >= 0 ? forwarded.substring(0, separator) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
