package com.heihu.workorder.controller;

import com.heihu.workorder.dto.AuthResponse;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.dto.MobileAuthResponse;
import com.heihu.workorder.dto.MobileBindRequest;
import com.heihu.workorder.dto.MobileWechatLoginRequest;
import com.heihu.workorder.service.MobileAuthService;
import com.heihu.workorder.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;
    private final AuthService authService;

    public MobileAuthController(MobileAuthService mobileAuthService, AuthService authService) {
        this.mobileAuthService = mobileAuthService;
        this.authService = authService;
    }

    @PostMapping("/password-login")
    public AuthResponse passwordLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.loginMobile(request, httpRequest);
    }

    @PostMapping("/wechat-login")
    public MobileAuthResponse wechatLogin(@RequestBody MobileWechatLoginRequest request) {
        return mobileAuthService.wechatLogin(request);
    }

    @PostMapping("/bind")
    public MobileAuthResponse bind(@RequestBody MobileBindRequest request) {
        return mobileAuthService.bind(request);
    }
}
