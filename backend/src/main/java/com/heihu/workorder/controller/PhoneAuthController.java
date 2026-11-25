package com.heihu.workorder.controller;

import com.heihu.workorder.dto.PhoneLoginResponse;
import com.heihu.workorder.dto.PhonePasswordLoginRequest;
import com.heihu.workorder.dto.PhoneSmsLoginRequest;
import com.heihu.workorder.dto.SelectFactoryRequest;
import com.heihu.workorder.dto.SmsSendRequest;
import com.heihu.workorder.service.PhoneAuthService;
import com.heihu.workorder.service.SmsService;
import com.heihu.workorder.util.ClientIpResolver;
import org.springframework.beans.factory.annotation.Autowired;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/phone")
public class PhoneAuthController {

    private final PhoneAuthService phoneAuthService;
    private final SmsService smsService;
    @Autowired private ClientIpResolver clientIpResolver = new ClientIpResolver("");

    public PhoneAuthController(PhoneAuthService phoneAuthService, SmsService smsService) {
        this.phoneAuthService = phoneAuthService;
        this.smsService = smsService;
    }

    @PostMapping("/password-login")
    public PhoneLoginResponse passwordLogin(@Valid @RequestBody PhonePasswordLoginRequest request) {
        return phoneAuthService.loginByPassword(request.getPhone(), request.getPassword());
    }

    @PostMapping("/sms-login")
    public PhoneLoginResponse smsLogin(@Valid @RequestBody PhoneSmsLoginRequest request) {
        return phoneAuthService.loginBySmsCode(request.getPhone(), request.getCode());
    }

    @PostMapping("/sms/send")
    public void sendSmsCode(@Valid @RequestBody SmsSendRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        smsService.sendCode(request.getPhone(), clientIp);
    }

    @PostMapping("/select-factory")
    public PhoneLoginResponse selectFactory(@Valid @RequestBody SelectFactoryRequest request) {
        return phoneAuthService.selectFactory(
                request.getPhone(),
                request.getFactoryId(),
                request.getLoginType(),
                request.getCredential()
        );
    }

    private String getClientIp(HttpServletRequest request) {
        return clientIpResolver.getClientIp(request);
    }
}
