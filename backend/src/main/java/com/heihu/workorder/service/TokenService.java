package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TokenService {

    private final Map<String, AuthenticatedUser> sessions = new ConcurrentHashMap<>();

    public String issue(FactoryUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new AuthenticatedUser(
                user.getId(),
                user.getFactory().getId(),
                user.getRole(),
                user.getName()
        ));
        return token;
    }

    public AuthenticatedUser requireAdmin(String authorization) {
        AuthenticatedUser currentUser = requireUser(authorization);
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号无管理员权限");
        }
        return currentUser;
    }

    public AuthenticatedUser requireUser(String authorization) {
        String token = extractToken(authorization);
        AuthenticatedUser currentUser = sessions.get(token);
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return currentUser;
    }

    private String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String prefix = "Bearer ";
        if (authorization.startsWith(prefix)) {
            return authorization.substring(prefix.length()).trim();
        }
        return authorization.trim();
    }
}
