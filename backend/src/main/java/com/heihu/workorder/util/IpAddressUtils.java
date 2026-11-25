package com.heihu.workorder.util;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

public final class IpAddressUtils {

    private static final ClientIpResolver DIRECT_PEER = new ClientIpResolver("");
    private static final String UNKNOWN = "unknown";
    private static final Pattern IPV4_PATTERN = Pattern.compile("^([0-9]{1,3}\\.){3}[0-9]{1,3}$");

    private IpAddressUtils() {
    }

    /**
     * Without explicit proxy configuration, only the socket peer is authoritative.
     */
    public static String getClientIp(HttpServletRequest request) {
        return DIRECT_PEER.getClientIp(request);
    }

    public static String getClientIp(HttpServletRequest request, String trustedProxies) {
        return new ClientIpResolver(trustedProxies).getClientIp(request);
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

}
