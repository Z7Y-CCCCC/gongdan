package com.heihu.workorder.service;

import com.heihu.workorder.dto.AuthResponse;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.dto.UserResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService
    ) {
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String factoryCode = normalize(request.getFactoryCode()).toUpperCase();
        String account = normalize(request.getUsername());

        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "工厂代码、用户名或密码不正确"));

        FactoryUser user = findUser(factory.getId(), account)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "工厂代码、用户名或密码不正确"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "工厂代码、用户名或密码不正确");
        }

        if (user.getRole() == Role.PRODUCTION) {
            throw new ApiException(HttpStatus.FORBIDDEN, "生产人员只能在手机端登录");
        }

        String token = tokenService.issue(user);
        return new AuthResponse(token, UserResponse.from(user));
    }

    private Optional<FactoryUser> findUser(Long factoryId, String account) {
        Optional<FactoryUser> byUsername = userRepository.findByFactoryIdAndUsername(factoryId, account);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        if (StringUtils.hasText(account)) {
            return userRepository.findByFactoryIdAndPhone(factoryId, account);
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
