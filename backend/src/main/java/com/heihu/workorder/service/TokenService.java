package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TokenService {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;

    public TokenService() {
        this(Duration.ofMinutes(720L));
    }

    @Autowired
    public TokenService(@Value("${app.session.ttl-minutes:720}") long ttlMinutes) {
        this(Duration.ofMinutes(ttlMinutes));
    }

    TokenService(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("session ttl must be greater than 0");
        }
        this.ttl = ttl;
    }

    public String issue(FactoryUser user) {
        cleanupExpiredSessions();
        if (user.getId() != null) {
            for (Session existing : sessions.values()) {
                if (user.getId().equals(existing.getUser().getUserId())) {
                    existing.getUser().setKicked(true);
                }
            }
        }
        String token = UUID.randomUUID().toString().replace("-", "");
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

    public AuthenticatedUser requireUser(String authorization) {
        String token = extractToken(authorization);
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
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                sessions.remove(entry.getKey());
            }
        }
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
