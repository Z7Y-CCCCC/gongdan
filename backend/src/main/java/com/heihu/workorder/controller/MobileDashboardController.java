package com.heihu.workorder.controller;

import com.heihu.workorder.dto.MobileDashboardResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.MobileDashboardService;
import com.heihu.workorder.service.TokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/dashboard")
public class MobileDashboardController {
    private final MobileDashboardService dashboard;
    private final TokenService tokenService;
    public MobileDashboardController(MobileDashboardService dashboard, TokenService tokenService) {
        this.dashboard = dashboard;
        this.tokenService = tokenService;
    }
    @GetMapping
    public MobileDashboardResponse get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam(required = false) String day) {
        AuthenticatedUser user = tokenService.requireUser(authorization);
        return dashboard.get(user.getFactoryId(), user.getUserId(), day);
    }
}
