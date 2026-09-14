package com.heihu.plugins.inspection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

@Component
public class PluginGatewayContextFilter extends OncePerRequestFilter {

    private static final String PLUGIN_KEY = "equipment-inspection";
    private static final long MAX_CLOCK_SKEW_SECONDS = 60;
    private static final int MAX_REQUEST_BYTES = 1024 * 1024;

    @Value("${plugin.gateway.secret:}")
    private String gatewaySecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Match the same decoded, matrix-parameter-free path that Spring MVC routes.
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !(path.equals("/api/plugin") || path.startsWith("/api/plugin/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] requestBody = readBody(request);
        String pluginKey = request.getHeader("X-Plugin-Key");
        String factoryId = request.getHeader("X-Plugin-Factory-Id");
        String userId = request.getHeader("X-Plugin-User-Id");
        String role = request.getHeader("X-Plugin-Role");
        if (requestBody == null) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "插件请求内容过大");
            return;
        }
        if (!PLUGIN_KEY.equals(pluginKey) || !isPositiveId(factoryId) || !isPositiveId(userId)
                || !("ADMIN".equals(role) || "PRODUCTION".equals(role))
                || !isValidGatewaySignature(request, requestBody)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "必须通过工单系统插件网关访问");
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, requestBody), response);
    }

    private boolean isNumeric(String value) {
        if (!StringUtils.hasText(value)) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    private boolean isValidGatewaySignature(HttpServletRequest request, byte[] requestBody) {
        if (!StringUtils.hasText(gatewaySecret)) return false;
        String timestamp = request.getHeader("X-Plugin-Gateway-Timestamp");
        String signature = request.getHeader("X-Plugin-Gateway-Signature");
        String bodyHash = request.getHeader("X-Plugin-Body-SHA256");
        if (!isNumeric(timestamp) || !StringUtils.hasText(signature) || !StringUtils.hasText(bodyHash)) return false;
        try {
            if (!MessageDigest.isEqual(
                    MessageDigest.getInstance("SHA-256").digest(requestBody), hexToBytes(bodyHash))) return false;
            long issuedAt = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - issuedAt) > MAX_CLOCK_SKEW_SECONDS) return false;
            String query = request.getQueryString();
            String path = request.getRequestURI() + (query == null ? "" : "?" + query);
            String canonical = String.join("\n", request.getMethod(), path, PLUGIN_KEY,
                    request.getHeader("X-Plugin-Version") == null ? "" : request.getHeader("X-Plugin-Version"),
                    request.getHeader("X-Plugin-Factory-Id"), request.getHeader("X-Plugin-User-Id"),
                    request.getHeader("X-Plugin-Role"), timestamp, bodyHash,
                    request.getHeader("X-Plugin-Config-Base64") == null ? "" : request.getHeader("X-Plugin-Config-Base64"));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(gatewaySecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] actual = hexToBytes(signature);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            return false;
        }
    }

    private byte[] hexToBytes(String value) {
        if (value.length() != 64) throw new IllegalArgumentException("invalid signature length");
        byte[] result = new byte[32];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("invalid signature");
            result[i / 2] = (byte) ((high << 4) + low);
        }
        return result;
    }

    private boolean isPositiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return false;
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        ServletInputStream input = request.getInputStream();
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_REQUEST_BYTES) return null;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }
}
