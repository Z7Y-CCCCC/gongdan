package com.heihu.workorder.service;

import com.heihu.workorder.dto.FactoryOption;
import com.heihu.workorder.dto.PhoneLoginResponse;
import com.heihu.workorder.dto.UserResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SmsService smsService;

    public PhoneAuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            SmsService smsService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.smsService = smsService;
    }

    /**
     * 手机号 + 密码登录。
     * 若手机号关联多个工厂且有多个启用用户密码匹配，返回工厂列表供选择。
     */
    @Transactional(readOnly = true)
    public PhoneLoginResponse loginByPassword(String phone, String password) {
        List<FactoryUser> users = userRepository.findAllByPhone(phone);
        if (users.isEmpty()) {
            PhoneLoginResponse response = new PhoneLoginResponse();
            response.setBindingRequired(true);
            response.setPhone(phone);
            return response;
        }

        // 筛选密码匹配的用户
        List<FactoryUser> matched = users.stream()
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "手机号或密码不正确");
        }

        // 筛选启用的用户
        List<FactoryUser> enabled = matched.stream()
                .filter(FactoryUser::isEnabled)
                .collect(Collectors.toList());

        if (enabled.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }

        if (enabled.size() == 1) {
            FactoryUser user = enabled.get(0);
            String token = tokenService.issue(user);
            PhoneLoginResponse response = new PhoneLoginResponse();
            response.setSelectRequired(false);
            response.setToken(token);
            response.setUser(UserResponse.from(user));
            return response;
        }

        // 多个启用用户 — 返回工厂列表
        PhoneLoginResponse response = new PhoneLoginResponse();
        response.setSelectRequired(true);
        response.setFactories(buildFactoryList(enabled));
        return response;
    }

    /**
     * 手机号 + 短信验证码登录。
     * 若手机号关联多个工厂且有多个启用用户，返回工厂列表供选择。
     * 若手机号无关联用户且验证码有效，返回 bindingRequired=true 引导绑定流程。
     */
    @Transactional(readOnly = true)
    public PhoneLoginResponse loginBySmsCode(String phone, String code) {
        if (!smsService.verifyCode(phone, code, false)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }

        List<FactoryUser> users = userRepository.findAllByPhone(phone);
        if (users.isEmpty()) {
            smsService.verifyCode(phone, code);
            // 验证码有效但手机号未注册，返回 bindingRequired 响应引导绑定
            PhoneLoginResponse response = new PhoneLoginResponse();
            response.setBindingRequired(true);
            response.setPhone(phone);
            return response;
        }

        // 筛选启用的用户
        List<FactoryUser> enabled = users.stream()
                .filter(FactoryUser::isEnabled)
                .collect(Collectors.toList());

        if (enabled.isEmpty()) {
            smsService.verifyCode(phone, code);
            throw new ApiException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }

        if (enabled.size() == 1) {
            smsService.verifyCode(phone, code);
            FactoryUser user = enabled.get(0);
            String token = tokenService.issue(user);
            PhoneLoginResponse response = new PhoneLoginResponse();
            response.setSelectRequired(false);
            response.setToken(token);
            response.setUser(UserResponse.from(user));
            return response;
        }

        // 多个启用用户 — 返回工厂列表
        PhoneLoginResponse response = new PhoneLoginResponse();
        response.setSelectRequired(true);
        response.setFactories(buildFactoryList(enabled));
        return response;
    }

    /**
     * 多工厂选择后完成登录。
     * 根据 loginType 决定是否需要再次验证凭证。
     */
    @Transactional(readOnly = true)
    public PhoneLoginResponse selectFactory(String phone, Long factoryId, String loginType, String credential) {
        List<FactoryUser> users = userRepository.findAllByPhone(phone);

        FactoryUser user = users.stream()
                .filter(u -> u.getFactory().getId().equals(factoryId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "无效的工厂选择"));

        if ("password".equals(loginType)) {
            if (!passwordEncoder.matches(credential, user.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "手机号或密码不正确");
            }
        } else if ("sms".equals(loginType)) {
            if (!smsService.verifyCode(phone, credential)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "登录类型不正确");
        }

        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }

        String token = tokenService.issue(user);
        PhoneLoginResponse response = new PhoneLoginResponse();
        response.setSelectRequired(false);
        response.setToken(token);
        response.setUser(UserResponse.from(user));
        return response;
    }

    private List<FactoryOption> buildFactoryList(List<FactoryUser> users) {
        return users.stream()
                .map(u -> new FactoryOption(u.getFactory().getId(), u.getFactory().getCode(), u.getFactory().getName()))
                .collect(Collectors.toList());
    }
}
