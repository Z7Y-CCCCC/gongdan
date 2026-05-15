package com.heihu.workorder.controller;

import com.heihu.workorder.dto.MobileAuthResponse;
import com.heihu.workorder.dto.MobileBindRequest;
import com.heihu.workorder.dto.MobileWechatLoginRequest;
import com.heihu.workorder.service.MobileAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
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
