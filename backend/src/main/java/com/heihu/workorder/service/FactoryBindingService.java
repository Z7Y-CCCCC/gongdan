package com.heihu.workorder.service;

import com.heihu.workorder.dto.BindingResponse;
import com.heihu.workorder.dto.UserResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FactoryBindingService {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final AuthorizationCodeService authorizationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SecureRandom secureRandom;

    @Value("${app.binding.default-password-length:6}")
    private int defaultPasswordLength;

    public FactoryBindingService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            AuthorizationCodeService authorizationCodeService,
            PasswordEncoder passwordEncoder,
            TokenService tokenService
    ) {
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.authorizationCodeService = authorizationCodeService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.secureRandom = new SecureRandom();
    }

    /**
     * 绑定手机号到工厂。
     * 流程：验证必填字段 → 验证工厂存在 → 验证工厂未绑定 → 验证手机号未绑定该工厂 → 验证并消费授权码 → 创建 FactoryUser → 生成默认密码 → 发放 Token
     *
     * @param phone             手机号
     * @param factoryCode       工厂代码
     * @param authorizationCode 授权码
     * @return 绑定响应，包含 token、用户信息和默认密码
     */
    @Transactional
    @CacheEvict(cacheNames = {"users.list", "departments.list"}, allEntries = true)
    public BindingResponse bindFactory(String phone, String factoryCode, String authorizationCode) {
        // 1. 验证必填字段
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(factoryCode) || !StringUtils.hasText(authorizationCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "工厂代码和授权码不能为空");
        }

        // 2. 验证工厂存在
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "工厂代码无效"));

        // 3. 验证工厂未绑定（没有 virtualUser=false 的 FactoryUser）
        List<FactoryUser> existingUsers = userRepository.findByFactoryIdOrderByCreatedAtAsc(factory.getId());
        boolean hasNonVirtualUser = existingUsers.stream()
                .anyMatch(u -> !u.isVirtualUser());
        if (hasNonVirtualUser) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该工厂已被绑定");
        }

        // 4. 验证手机号未绑定该工厂
        if (userRepository.findByFactoryIdAndPhone(factory.getId(), phone).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该手机号已绑定此工厂");
        }

        // 5. 验证并消费授权码
        authorizationCodeService.validateAndConsume(authorizationCode, factoryCode);

        // 6. 生成默认密码
        String defaultPassword = generateRandomPassword();

        // 7. 创建 FactoryUser
        LocalDateTime now = LocalDateTime.now();
        FactoryUser newUser = new FactoryUser();
        newUser.setFactory(factory);
        newUser.setRole(Role.ADMIN);
        newUser.setEnabled(true);
        newUser.setVirtualUser(false);
        newUser.setUsername(phone);
        newUser.setName(phone);
        newUser.setPhone(phone);
        newUser.setPasswordHash(passwordEncoder.encode(defaultPassword));
        newUser.setCreatedAt(now);
        newUser.setUpdatedAt(now);

        // 8. 保存 FactoryUser
        FactoryUser savedUser = userRepository.save(newUser);

        // 9. 生成 Token
        String token = tokenService.issue(savedUser);

        // 10. 返回绑定响应
        return new BindingResponse(token, UserResponse.from(savedUser), defaultPassword);
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(defaultPasswordLength);
        for (int i = 0; i < defaultPasswordLength; i++) {
            int index = secureRandom.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }
}
