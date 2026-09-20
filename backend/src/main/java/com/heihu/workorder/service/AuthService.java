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
import com.heihu.workorder.util.IpAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import javax.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthAttemptLimiter authAttemptLimiter;
    private final IpLocationService ipLocationService;

    @Autowired
    public AuthService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AuthAttemptLimiter authAttemptLimiter,
            IpLocationService ipLocationService
    ) {
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.authAttemptLimiter = authAttemptLimiter;
        this.ipLocationService = ipLocationService;
    }

    public AuthService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AuthAttemptLimiter authAttemptLimiter
    ) {
        this(factoryRepository, userRepository, passwordEncoder, tokenService, authAttemptLimiter, new IpLocationService());
    }

    public AuthService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService
    ) {
        this(factoryRepository, userRepository, passwordEncoder, tokenService, new AuthAttemptLimiter(), new IpLocationService());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String factoryCode = normalize(request.getFactoryCode()).toUpperCase();
        String account = normalize(request.getUsername());
        String limitKey = AuthAttemptLimiter.key("web", factoryCode, account);
        authAttemptLimiter.checkAllowed(limitKey);

        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> invalidCredentials(limitKey));

        if (!factory.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "该工厂已被停用，无法登录");
        }

        FactoryUser user = findUser(factory.getId(), account)
                .orElseThrow(() -> invalidCredentials(limitKey));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials(limitKey);
        }

        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "工厂代码、用户名或密码不正确");
        }

        if (user.getRole() == Role.PRODUCTION) {
            throw new ApiException(HttpStatus.FORBIDDEN, "生产人员只能在手机端登录");
        }

        authAttemptLimiter.recordSuccess(limitKey);
        String userAgent = httpRequest == null ? null : httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest == null ? null : IpAddressUtils.getClientIp(httpRequest);
        String loginLocation = ipLocationService == null ? "内网IP" : ipLocationService.searchLocation(ipAddress);
        log.info("用户 [{}] 于工厂 [{}] 登录成功，解析客户端真实IP: [{}], 归属地: [{}]", account, factoryCode, ipAddress, loginLocation);

        String token = tokenService.issue(
                user,
                ipAddress,
                loginLocation,
                detectOperatingSystem(userAgent),
                detectBrowser(userAgent)
        );
        boolean isSuperAdmin = "ADMIN".equals(factory.getCode());
        return new AuthResponse(token, UserResponse.from(user), isSuperAdmin);
    }

    private Optional<FactoryUser> findUser(Long factoryId, String account) {
        Optional<FactoryUser> byUsername = userRepository.findByFactoryIdAndUsernameForUpdate(factoryId, account);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        if (StringUtils.hasText(account)) {
            return userRepository.findByFactoryIdAndPhoneForUpdate(factoryId, account);
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ApiException invalidCredentials(String limitKey) {
        authAttemptLimiter.recordFailure(limitKey);
        return new ApiException(HttpStatus.UNAUTHORIZED, "工厂代码、用户名或密码不正确");
    }

    private String detectOperatingSystem(String userAgent) {
        if (!StringUtils.hasText(userAgent)) return null;
        String value = userAgent.toLowerCase();
        if (value.contains("windows")) return "Windows";
        if (value.contains("android")) return "Android";
        if (value.contains("iphone") || value.contains("ipad")) return "iOS";
        if (value.contains("mac os")) return "macOS";
        if (value.contains("linux")) return "Linux";
        return "其他系统";
    }

    private String detectBrowser(String userAgent) {
        if (!StringUtils.hasText(userAgent)) return null;
        String value = userAgent.toLowerCase();
        if (value.contains("edg/")) return "Edge";
        if (value.contains("chrome/") && !value.contains("edg/")) return "Chrome";
        if (value.contains("firefox/")) return "Firefox";
        if (value.contains("safari/") && !value.contains("chrome/")) return "Safari";
        return "其他浏览器";
    }
}
