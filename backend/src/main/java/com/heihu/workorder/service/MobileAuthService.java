package com.heihu.workorder.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.MobileAuthResponse;
import com.heihu.workorder.dto.MobileBindRequest;
import com.heihu.workorder.dto.MobileWechatLoginRequest;
import com.heihu.workorder.dto.UserResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class MobileAuthService {

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthAttemptLimiter authAttemptLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, OpenidTicket> openidTickets = new ConcurrentHashMap<>();

    @Value("${app.wechat.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${app.wechat.app-id:}")
    private String appId;

    @Value("${app.wechat.secret:}")
    private String secret;

    @Value("${app.wechat.openid-ticket-ttl-minutes:10}")
    private long openidTicketTtlMinutes;

    @Autowired
    public MobileAuthService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AuthAttemptLimiter authAttemptLimiter
    ) {
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.authAttemptLimiter = authAttemptLimiter;
    }

    public MobileAuthService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService
    ) {
        this(factoryRepository, userRepository, passwordEncoder, tokenService, new AuthAttemptLimiter());
    }

    @Transactional
    public MobileAuthResponse wechatLogin(MobileWechatLoginRequest request) {
        LocalDateTime now = LocalDateTime.now();
        cleanupExpiredTickets(now);
        String openid = resolveOpenid(request);
        Optional<FactoryUser> userOpt = userRepository.findByWechatOpenid(openid);
        if (userOpt.isPresent()) {
            FactoryUser user = userOpt.get();
            if (!user.isEnabled()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "账号已停用");
            }
            user.setLastMobileLoginAt(LocalDateTime.now());
            userRepository.save(user);
            return MobileAuthResponse.loggedIn(tokenService.issue(user), UserResponse.from(user));
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        openidTickets.put(ticket, new OpenidTicket(openid, now.plusMinutes(openidTicketTtlMinutes)));
        return MobileAuthResponse.needBind(ticket);
    }

    @Transactional
    @CacheEvict(cacheNames = {"users.list", "departments.list"}, allEntries = true)
    public MobileAuthResponse bind(MobileBindRequest request) {
        String ticket = normalize(request.getOpenidTicket());
        LocalDateTime now = LocalDateTime.now();
        String openid = getOpenidTicket(ticket, now);
        
        // 开发者模拟模式：如果以 MOCK_TICKET_ 开头，且开启了 mock 模式，则自动生成一个模拟 openid
        if (!StringUtils.hasText(openid) && mockEnabled && ticket.startsWith("MOCK_TICKET_")) {
            openid = "MOCK_OPENID_" + ticket.substring(12);
        }

        if (!StringUtils.hasText(openid)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "微信绑定凭证已失效，请重新进入小程序");
        }

        String factoryCode = normalize(request.getFactoryCode()).toUpperCase();
        String account = normalize(request.getUsername());
        String limitKey = AuthAttemptLimiter.key("mobile-bind", factoryCode, account);
        authAttemptLimiter.checkAllowed(limitKey);
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> invalidCredentials(limitKey));
        FactoryUser user = findUser(factory.getId(), account)
                .orElseThrow(() -> invalidCredentials(limitKey));
        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials(limitKey);
        }

        userRepository.findByWechatOpenid(openid).ifPresent(bound -> {
            if (!bound.getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "该微信已绑定其他账号");
            }
        });

        user.setWechatOpenid(openid);
        user.setMobileBoundAt(now);
        user.setLastMobileLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        consumeOpenidTicket(ticket);
        authAttemptLimiter.recordSuccess(limitKey);
        return MobileAuthResponse.loggedIn(tokenService.issue(user), UserResponse.from(user));
    }

    private Optional<FactoryUser> findUser(Long factoryId, String account) {
        Optional<FactoryUser> byUsername = userRepository.findByFactoryIdAndUsername(factoryId, account);
        if (byUsername.isPresent()) return byUsername;
        return userRepository.findByFactoryIdAndPhone(factoryId, account);
    }

    private String resolveOpenid(MobileWechatLoginRequest request) {
        String mockOpenid = request == null ? "" : normalize(request.getMockOpenid());
        if (mockEnabled && StringUtils.hasText(mockOpenid)) {
            return mockOpenid;
        }
        String code = request == null ? "" : normalize(request.getCode());
        if (!StringUtils.hasText(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "缺少微信登录 code");
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(secret)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "未配置微信 appId/secret");
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId
                + "&secret=" + secret + "&js_code=" + code + "&grant_type=authorization_code";
        String body = new RestTemplate().getForObject(url, String.class);
        try {
            Map<String, Object> data = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Object openid = data.get("openid");
            if (openid == null || !StringUtils.hasText(String.valueOf(openid))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "微信登录失败");
            }
            return String.valueOf(openid);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "微信登录失败");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String getOpenidTicket(String ticket, LocalDateTime now) {
        OpenidTicket value = openidTickets.get(ticket);
        if (value == null || value.isExpired(now)) {
            return "";
        }
        return value.openid;
    }

    private void consumeOpenidTicket(String ticket) {
        openidTickets.remove(ticket);
    }

    private ApiException invalidCredentials(String limitKey) {
        authAttemptLimiter.recordFailure(limitKey);
        return new ApiException(HttpStatus.UNAUTHORIZED, "工厂代码、账号或密码不正确");
    }

    private void cleanupExpiredTickets(LocalDateTime now) {
        openidTickets.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static class OpenidTicket {
        private final String openid;
        private final LocalDateTime expiresAt;

        private OpenidTicket(String openid, LocalDateTime expiresAt) {
            this.openid = openid;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(LocalDateTime now) {
            return expiresAt == null || !expiresAt.isAfter(now);
        }
    }
}
