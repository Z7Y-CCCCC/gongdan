package com.heihu.workorder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.conn.util.InetAddressUtils;
import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

public final class IpAddressUtils {

    private static final Logger log = LoggerFactory.getLogger(IpAddressUtils.class);
    private static final String UNKNOWN = "unknown";
    private static final Pattern IPV4_PATTERN = Pattern.compile("^([0-9]{1,3}\\.){3}[0-9]{1,3}$");

    private IpAddressUtils() {
    }

    /**
     * 从 HTTP 请求中提取客户端真实 IP 地址。
     * 优先解析 CF-Connecting-IP，其次解析 X-Forwarded-For 第一个客户端 IP，
     * 再次解析 X-Real-IP 等代理头，最后兜底 getRemoteAddr。
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. 优先获取 Cloudflare 注入的客户端真实 IP
        String cfIp = cleanIp(request.getHeader("CF-Connecting-IP"));
        if (isValidIp(cfIp) && !isInternalIp(cfIp)) {
            log.info("从 CF-Connecting-IP 解析到公网客户端IP: {}", cfIp);
            return cfIp;
        }

        // 2. 从 X-Forwarded-For 中提取：标准格式为 client, proxy1, proxy2
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            String bestIp = extractClientFromForwardedFor(xForwardedFor);
            if (isValidIp(bestIp)) {
                log.debug("从 X-Forwarded-For 解析到客户端IP: {}", bestIp);
                return bestIp;
            }
        }

        // 3. 常见反向代理头依序回退
        String[] candidateHeaders = {
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : candidateHeaders) {
            String ip = cleanIp(request.getHeader(header));
            if (isValidIp(ip) && !isInternalIp(ip)) {
                log.info("从标头 {} 解析到公网客户端IP: {}", header, ip);
                return ip;
            }
        }

        if (isValidIp(cfIp)) {
            log.info("采用 CF-Connecting-IP: {}", cfIp);
            return cfIp;
        }

        // 4. 兜底获取底层连接地址
        String remoteAddr = normalizeLocalhost(cleanIp(request.getRemoteAddr()));
        log.info("兜底采用 remoteAddr: {}", remoteAddr);
        return remoteAddr;
    }

    /**
     * 从 X-Forwarded-For 提取客户端真实 IP。
     */
    private static String extractClientFromForwardedFor(String forwardedFor) {
        String[] rawList = forwardedFor.split(",");
        String firstValid = null;

        for (String raw : rawList) {
            String ip = cleanIp(raw);
            if (isValidIp(ip)) {
                if (!isInternalIp(ip)) {
                    return ip;
                }
                if (firstValid == null) {
                    firstValid = ip;
                }
            }
        }
        return firstValid;
    }

    /**
     * 判断给定 IP 是否为内网/私有局域网/回环地址。
     */
    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.trim().isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            return true;
        }
        String clean = ip.trim();
        if ("127.0.0.1".equals(clean) || "::1".equals(clean) || "0:0:0:0:0:0:0:1".equals(clean) || "localhost".equalsIgnoreCase(clean)) {
            return true;
        }
        if (!IPV4_PATTERN.matcher(clean).matches()) {
            return false;
        }

        String[] parts = clean.split("\\.");
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // 10.0.0.0/8
            if (first == 10) return true;
            // 172.16.0.0/12
            if (first == 172 && (second >= 16 && second <= 31)) return true;
            // 192.168.0.0/16
            if (first == 192 && second == 168) return true;
            // 169.254.0.0/16
            if (first == 169 && second == 254) return true;
            // 127.0.0.0/8
            if (first == 127) return true;
            // 0.0.0.0/8
            if (first == 0) return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
        return false;
    }

    private static String cleanIp(String ip) {
        if (ip == null) {
            return null;
        }
        String cleaned = ip.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        int commaIndex = cleaned.indexOf(',');
        if (commaIndex >= 0) {
            cleaned = cleaned.substring(0, commaIndex).trim();
        }
        return normalizeLocalhost(cleaned);
    }

    private static String normalizeLocalhost(String ip) {
        if (ip == null) {
            return null;
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    private static boolean isValidIp(String ip) {
        return ip != null && (InetAddressUtils.isIPv4Address(ip) || InetAddressUtils.isIPv6Address(ip));
    }
}
