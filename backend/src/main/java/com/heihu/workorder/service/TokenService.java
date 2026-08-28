package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.UserSession;
import com.heihu.workorder.repository.UserSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import javax.servlet.http.HttpServletRequest;

@Service
public class TokenService {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final UserSessionRepository sessionRepository;

    public TokenService() {
        this(Duration.ofMinutes(720L), null);
    }

    @Autowired
    public TokenService(
            @Value("${app.session.ttl-minutes:720}") long ttlMinutes,
            UserSessionRepository sessionRepository
    ) {
        this(Duration.ofMinutes(ttlMinutes), sessionRepository);
    }

    TokenService(Duration ttl) {
        this(ttl, null);
    }

    TokenService(Duration ttl, UserSessionRepository sessionRepository) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("session ttl must be greater than 0");
        }
        this.ttl = ttl;
        this.sessionRepository = sessionRepository;
    }

    public String issue(FactoryUser user) {
        return issue(user, null, null, null, null);
    }

    public String issue(FactoryUser user, String ipAddress, String loginLocation, String operatingSystem, String browser) {
        cleanupExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        if (sessionRepository != null) {
            if (user.getId() != null) {
                for (UserSession existing : sessionRepository.findByUserIdAndKickedFalse(user.getId())) {
                    existing.setKicked(true);
                    sessionRepository.save(existing);
                }
            }
            UserSession session = new UserSession();
            session.setTokenHash(hashToken(token));
            session.setUserId(user.getId());
            session.setFactoryId(user.getFactory().getId());
            session.setFactoryCode(user.getFactory().getCode());
            session.setRole(user.getRole());
            session.setDisplayName(user.getName());
            session.setKicked(false);
            session.setExpiresAt(Instant.now().plus(ttl));
            session.setCreatedAt(LocalDateTime.now());
            session.setIpAddress(ipAddress == null ? "-" : ipAddress);
            session.setLoginLocation(loginLocation == null ? "内网IP" : loginLocation);
            session.setOperatingSystem(operatingSystem == null ? "未知系统" : operatingSystem);
            session.setBrowser(browser == null ? "未知浏览器" : browser);
            sessionRepository.save(session);
            return token;
        }
        if (user.getId() != null) {
            for (Session existing : sessions.values()) {
                if (user.getId().equals(existing.getUser().getUserId())) {
                    existing.getUser().setKicked(true);
                }
            }
        }
        AuthenticatedUser currentUser = new AuthenticatedUser(
                user.getId(),
                user.getFactory().getId(),
                user.getFactory().getCode(),
                user.getRole(),
                user.getName()
        );
        sessions.put(token, new Session(currentUser, Instant.now().plus(ttl)));
        return token;
    }

    public AuthenticatedUser requireSuperAdmin(String authorization) {
        AuthenticatedUser user = requireUser(authorization);
        if (!"ADMIN".equalsIgnoreCase(user.getFactoryCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "\u9700\u8981\u8d85\u7ea7\u7ba1\u7406\u5458\u6743\u9650");
        }
        return user;
    }

    public AuthenticatedUser requireAdmin(String authorization) {
        AuthenticatedUser currentUser = requireUser(authorization);
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "\u5f53\u524d\u8d26\u53f7\u65e0\u7ba1\u7406\u5458\u6743\u9650");
        }
        return currentUser;
    }

    /**
     * Refresh metadata for the current persistent session. This also repairs sessions
     * created before browser metadata collection was added.
     */
    public void refreshSessionMetadata(String authorization, HttpServletRequest request) {
        if (sessionRepository == null || request == null) return;
        String token = extractToken(authorization);
        sessionRepository.findByTokenHash(hashToken(token)).ifPresent(session -> {
            String forwardedAddress = request.getHeader("X-Forwarded-For");
            String remoteAddress = StringUtils.hasText(forwardedAddress)
                    ? forwardedAddress.split(",")[0].trim()
                    : request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            session.setIpAddress(StringUtils.hasText(remoteAddress) ? remoteAddress : "-");
            session.setLoginLocation("\u5185\u7f51IP");
            session.setOperatingSystem(detectOperatingSystem(userAgent));
            session.setBrowser(detectBrowser(userAgent));
            sessionRepository.save(session);
        });
    }

    public AuthenticatedUser requireUser(String authorization) {
        String token = extractToken(authorization);
        if (sessionRepository != null) {
            UserSession session = sessionRepository.findByTokenHash(hashToken(token))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "\u8bf7\u5148\u767b\u5f55"));
            if (!Instant.now().isBefore(session.getExpiresAt())) {
                sessionRepository.delete(session);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "\u767b\u5f55\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55");
            }
            if (session.isKicked()) {
                sessionRepository.delete(session);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "\u60a8\u7684\u8d26\u53f7\u5df2\u5728\u5176\u4ed6\u5730\u65b9\u767b\u5f55\uff0c\u60a8\u5df2\u88ab\u6324\u4e0b\u7ebf");
            }
            return new AuthenticatedUser(
                    session.getUserId(),
                    session.getFactoryId(),
                    session.getFactoryCode(),
                    session.getRole(),
                    session.getDisplayName()
            );
        }
        Session session = sessions.get(token);
        if (session == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "\u8bf7\u5148\u767b\u5f55");
        }
        if (session.isExpired()) {
            sessions.remove(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "\u767b\u5f55\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55");
        }
        AuthenticatedUser currentUser = session.getUser();
        if (currentUser.isKicked()) {
            sessions.remove(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "\u60a8\u7684\u8d26\u53f7\u5df2\u5728\u5176\u4ed6\u5730\u65b9\u767b\u5f55\uff0c\u60a8\u5df2\u88ab\u6324\u4e0b\u7ebf");
        }
        return currentUser;
    }

    private String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "\u8bf7\u5148\u767b\u5f55");
        }
        String prefix = "Bearer ";
        if (authorization.startsWith(prefix)) {
            return authorization.substring(prefix.length()).trim();
        }
        return authorization.trim();
    }

    private void cleanupExpiredSessions() {
        if (sessionRepository != null) {
            sessionRepository.deleteByExpiresAtBefore(Instant.now());
            return;
        }
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                sessions.remove(entry.getKey());
            }
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private String detectOperatingSystem(String userAgent) {
        if (!StringUtils.hasText(userAgent)) return "\u672a\u77e5\u7cfb\u7edf";
        String value = userAgent.toLowerCase();
        if (value.contains("windows")) return "Windows";
        if (value.contains("android")) return "Android";
        if (value.contains("iphone") || value.contains("ipad")) return "iOS";
        if (value.contains("mac os")) return "macOS";
        if (value.contains("linux")) return "Linux";
        return "\u5176\u4ed6\u7cfb\u7edf";
    }

    private String detectBrowser(String userAgent) {
        if (!StringUtils.hasText(userAgent)) return "\u672a\u77e5\u6d4f\u89c8\u5668";
        String value = userAgent.toLowerCase();
        if (value.contains("edg/")) return "Edge";
        if (value.contains("chrome/") && !value.contains("edg/")) return "Chrome";
        if (value.contains("firefox/")) return "Firefox";
        if (value.contains("safari/") && !value.contains("chrome/")) return "Safari";
        return "\u5176\u4ed6\u6d4f\u89c8\u5668";
    }

    private static final class Session {
        private final AuthenticatedUser user;
        private final Instant expiresAt;

        private Session(AuthenticatedUser user, Instant expiresAt) {
            this.user = user;
            this.expiresAt = expiresAt;
        }

        private AuthenticatedUser getUser() {
            return user;
        }

        private boolean isExpired() {
            return !Instant.now().isBefore(expiresAt);
        }
    }
}
