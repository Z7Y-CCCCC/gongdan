package com.heihu.workorder.report;

import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bridges a platform-superadmin bearer session to the same-origin UReport servlet. */
@RestController
@RequestMapping("/api/ureport")
public class UReportController {

    private final TokenService tokenService;
    private final boolean cookieSecure;

    public UReportController(TokenService tokenService) {
        this(tokenService, false);
    }

    @Autowired
    public UReportController(TokenService tokenService,
            @Value("${app.ureport.cookie-secure:false}") boolean cookieSecure) {
        this.tokenService = tokenService;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> establishSession(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedUser user = tokenService.requireSuperAdmin(authorization);
        String token = rawToken(authorization);
        StringBuilder cookie = new StringBuilder()
                .append(UReportAuthFilter.SESSION_COOKIE).append('=').append(token)
                .append("; Max-Age=3600; Path=/ureport; HttpOnly; SameSite=Lax");
        // TLS-terminating deployments opt in explicitly; never infer trust from request headers.
        if (cookieSecure || request.isSecure()) cookie.append("; Secure");
        response.addHeader("Set-Cookie", cookie.toString());
        response.setHeader("Cache-Control", "no-store");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", true);
        body.put("path", "/ureport/designer");
        body.put("factoryCode", user.getFactoryCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private String rawToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
