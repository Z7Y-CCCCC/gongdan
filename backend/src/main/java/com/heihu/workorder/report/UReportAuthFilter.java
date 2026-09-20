package com.heihu.workorder.report;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.service.TokenService;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects the UReport servlet with the application's existing admin session. */
@Component
public class UReportAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_COOKIE = "workorder_ureport_token";
    private final TokenService tokenService;

    public UReportAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (path == null) return true;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !("/ureport".equals(path) || path.startsWith("/ureport/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = findCookie(request, SESSION_COOKIE);
        try {
            if (!StringUtils.hasText(token)) {
                throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请先在工单系统中登录");
            }
            tokenService.requireAdmin("Bearer " + token);
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            response.setStatus(ex.getStatus().value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private String findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
