package com.heihu.workorder.analytics;

import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TokenService tokenService;

    public AnalyticsController(AnalyticsService analyticsService, TokenService tokenService) {
        this.analyticsService = analyticsService;
        this.tokenService = tokenService;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(analyticsService.config());
    }

    @PostMapping("/guest-token")
    public ResponseEntity<Map<String, Object>> guestToken(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(analyticsService.guestToken(currentUser));
    }
}
