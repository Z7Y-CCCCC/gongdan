package com.heihu.workorder.service;

import com.heihu.workorder.dto.AuthCodeListItem;
import com.heihu.workorder.dto.AuthCodeResponse;
import com.heihu.workorder.dto.FactoryAdminListItem;
import com.heihu.workorder.dto.FactoryAdminResponse;
import com.heihu.workorder.dto.FactoryListItem;
import com.heihu.workorder.dto.FactoryResponse;
import com.heihu.workorder.dto.ManagementListResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.AuthorizationCode;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuperAdminService {

    private static final Pattern FACTORY_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{1,16}$");
    private static final Pattern SUPER_ADMIN_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]{3,32}$");
    private static final String RESERVED_CODE = "ADMIN";
    private static final String ADMIN_FACTORY_CODE = "ADMIN";
    private static final int SUPER_ADMIN_PASSWORD_MIN_LENGTH = 6;
    private static final int SUPER_ADMIN_PASSWORD_MAX_LENGTH = 64;
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int GENERATED_PASSWORD_LENGTH = 6;
    private static final String AUTH_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int AUTH_CODE_LENGTH = 8;
    private static final int DEFAULT_EXPIRATION_MINUTES = 30;
    private static final int MIN_EXPIRATION_MINUTES = 1;
    private static final int MAX_EXPIRATION_MINUTES = 1440;

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminService(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            AuthorizationCodeRepository authorizationCodeRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.authorizationCodeRepository = authorizationCodeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建工厂。
     * 验证工厂代码格式、保留代码、唯一性（代码和名称）。
     */
    @Transactional
    public FactoryResponse createFactory(String code, String name) {
        // 验证工厂代码格式
        if (!FACTORY_CODE_PATTERN.matcher(code).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "工厂代码格式无效，需为1-16位大写字母和数字");
        }

        // 验证保留代码
        if (RESERVED_CODE.equalsIgnoreCase(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该工厂代码为系统保留代码");
        }

        // 验证代码唯一性
        if (factoryRepository.findByCode(code).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "工厂代码已存在");
        }

        // 验证名称唯一性
        if (factoryRepository.findByName(name).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "工厂名称已被使用");
        }

        // 创建工厂
        Factory factory = new Factory();
        factory.setCode(code);
        factory.setName(name);
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        factory = factoryRepository.save(factory);

        return new FactoryResponse(factory.getCode(), factory.getName(), factory.isEnabled(), factory.getCreatedAt());
    }

    /**
     * 停用工厂（级联操作）。
     * 设置 factory.enabled=false，停用所有用户，停用所有未消费且未停用的授权码。
     */
    @Transactional
    public void disableFactory(String factoryCode) {
        // 禁止停用超级管理员工厂
        if ("admin".equalsIgnoreCase(factoryCode)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "超级管理员工厂不能被停用");
        }

        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));

        // 幂等：如果已停用，直接返回
        if (!factory.isEnabled()) {
            return;
        }

        // 停用工厂
        factory.setEnabled(false);
        factoryRepository.save(factory);

        // 级联停用所有用户
        List<FactoryUser> users = userRepository.findByFactoryId(factory.getId());
        LocalDateTime now = LocalDateTime.now();
        for (FactoryUser user : users) {
            if (user.isEnabled()) {
                user.setEnabled(false);
                user.setUpdatedAt(now);
                userRepository.save(user);
            }
        }

        // 级联停用所有未消费且未停用的授权码
        List<AuthorizationCode> activeCodes = authorizationCodeRepository
                .findByFactoryIdAndConsumedFalseAndDisabledFalse(factory.getId());
        for (AuthorizationCode authCode : activeCodes) {
            authCode.setDisabled(true);
            authCode.setDisabledAt(now);
            authorizationCodeRepository.save(authCode);
        }
    }

    /**
     * 启用工厂（不级联）。
     * 仅设置 factory.enabled=true，不恢复用户或授权码状态。
     */
    @Transactional
    public void enableFactory(String factoryCode) {
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));

        factory.setEnabled(true);
        factoryRepository.save(factory);
    }

    /**
     * 生成随机密码。
     * 6个字符，从 [A-Za-z0-9] 中随机选取。
     */
    public String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 创建工厂管理员账号。
     * 验证工厂存在且启用、用户名格式和唯一性、密码长度。
     */
    @Transactional
    public FactoryAdminResponse createFactoryAdmin(String factoryCode, String username, String password) {
        // 1. 查找工厂
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "工厂代码无效"));

        // 2. 检查工厂是否启用
        if (!factory.isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该工厂已被停用");
        }

        // 3. 验证用户名格式：不为空白，1-64字符
        if (username == null || username.trim().isEmpty() || username.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名格式无效");
        }

        // 4. 检查用户名在工厂内唯一性
        if (userRepository.findByFactoryIdAndUsername(factory.getId(), username).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        // 5. 处理密码
        String actualPassword;
        if (password != null && !password.isEmpty()) {
            // 密码已提供，验证长度
            if (password.length() < 6) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度不能少于6位");
            }
            actualPassword = password;
        } else {
            // 未提供密码，生成随机密码
            actualPassword = generateRandomPassword();
        }

        // 6. 创建 FactoryUser
        LocalDateTime now = LocalDateTime.now();
        FactoryUser user = new FactoryUser();
        user.setFactory(factory);
        user.setUsername(username);
        user.setName(username);
        user.setPasswordHash(passwordEncoder.encode(actualPassword));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVirtualUser(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        // 7. 返回响应（包含明文密码）
        return new FactoryAdminResponse(
                factory.getCode(),
                username,
                actualPassword,
                true,
                user.getCreatedAt()
        );
    }

    /**
     * 停用工厂管理员账号。
     */
    @Transactional
    public void disableFactoryAdmin(Long userId) {
        FactoryUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));

        user.setEnabled(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 启用工厂管理员账号。
     */
    @Transactional
    public void enableFactoryAdmin(Long userId) {
        FactoryUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));

        user.setEnabled(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 创建超级管理员账号。
     * 验证用户名格式 [A-Za-z0-9]{3,32}，密码长度 6-64，用户名在admin工厂内唯一。
     */
    @Transactional
    public void createSuperAdmin(String username, String password) {
        // 1. 验证用户名格式
        if (username == null || !SUPER_ADMIN_USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名格式无效");
        }

        // 2. 验证密码长度
        if (password == null || password.length() < SUPER_ADMIN_PASSWORD_MIN_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度不能少于6位");
        }
        if (password.length() > SUPER_ADMIN_PASSWORD_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度不能超过64位");
        }

        // 3. 查找admin工厂
        Factory adminFactory = factoryRepository.findByCode(ADMIN_FACTORY_CODE)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "系统错误：admin工厂不存在"));

        // 4. 检查用户名在admin工厂内唯一性
        if (userRepository.findByFactoryIdAndUsername(adminFactory.getId(), username).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        // 5. 创建 FactoryUser
        LocalDateTime now = LocalDateTime.now();
        FactoryUser user = new FactoryUser();
        user.setFactory(adminFactory);
        user.setUsername(username);
        user.setName(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVirtualUser(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    /**
     * 生成授权码。
     * 验证工厂存在，验证有效期范围，生成8位随机码。
     */
    @Transactional
    public AuthCodeResponse generateAuthCode(String factoryCode, Integer expirationMinutes) {
        // 1. 查找工厂
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "工厂代码无效"));

        // 2. 验证有效期
        int minutes;
        if (expirationMinutes == null) {
            minutes = DEFAULT_EXPIRATION_MINUTES;
        } else {
            if (expirationMinutes < MIN_EXPIRATION_MINUTES || expirationMinutes > MAX_EXPIRATION_MINUTES) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "有效期必须在1-1440分钟之间");
            }
            minutes = expirationMinutes;
        }

        // 3. 生成8位随机授权码
        String code = generateAuthCodeString();

        // 4. 创建授权码记录
        LocalDateTime now = LocalDateTime.now();
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode(code);
        authorizationCode.setFactory(factory);
        authorizationCode.setCreatedAt(now);
        authorizationCode.setExpiresAt(now.plusMinutes(minutes));
        authorizationCode.setConsumed(false);
        authorizationCode.setDisabled(false);

        // 5. 保存并返回
        authorizationCodeRepository.save(authorizationCode);

        return new AuthCodeResponse(code, authorizationCode.getExpiresAt(), factory.getCode());
    }

    /**
     * 停用授权码。
     * 设置 disabled=true 和 disabledAt=now（幂等操作）。
     */
    @Transactional
    public void disableAuthCode(Long authCodeId) {
        AuthorizationCode authCode = authorizationCodeRepository.findById(authCodeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));

        authCode.setDisabled(true);
        authCode.setDisabledAt(LocalDateTime.now());
        authorizationCodeRepository.save(authCode);
    }

    /**
     * 生成8位随机授权码，字符集为 [A-Z0-9]。
     */
    private String generateAuthCodeString() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(AUTH_CODE_LENGTH);
        for (int i = 0; i < AUTH_CODE_LENGTH; i++) {
            sb.append(AUTH_CODE_CHARS.charAt(random.nextInt(AUTH_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 获取管理列表。
     * 返回工厂（排除admin）、工厂管理员、授权码，均按创建时间降序排列。
     */
    @Transactional(readOnly = true)
    public ManagementListResponse getManagementList() {
        // 获取工厂列表（排除admin工厂），按创建时间降序
        List<FactoryListItem> factories = factoryRepository.findByCodeNotOrderByCreatedAtDesc(ADMIN_FACTORY_CODE)
                .stream()
                .map(f -> new FactoryListItem(f.getCode(), f.getName(), f.isEnabled(), f.getCreatedAt()))
                .collect(Collectors.toList());

        // 获取admin工厂以排除其用户
        Factory adminFactory = factoryRepository.findByCode(ADMIN_FACTORY_CODE).orElse(null);
        Long adminFactoryId = adminFactory != null ? adminFactory.getId() : -1L;

        // 获取工厂管理员列表（role=ADMIN, virtualUser=false, 不属于admin工厂），按创建时间降序
        List<FactoryAdminListItem> factoryAdmins = userRepository
                .findByRoleAndVirtualUserFalseAndFactoryIdNotOrderByCreatedAtDesc(Role.ADMIN, adminFactoryId)
                .stream()
                .map(u -> new FactoryAdminListItem(
                        u.getId(),
                        u.getFactory().getCode(),
                        u.getUsername(),
                        u.isEnabled(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());

        // 获取所有授权码，按创建时间降序
        LocalDateTime now = LocalDateTime.now();
        List<AuthCodeListItem> authCodes = authorizationCodeRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ac -> {
                    String status = calculateAuthCodeStatus(ac, now);
                    return new AuthCodeListItem(
                            ac.getId(),
                            ac.getCode(),
                            ac.getFactory().getCode(),
                            status,
                            ac.getCreatedAt(),
                            ac.getExpiresAt()
                    );
                })
                .collect(Collectors.toList());

        return new ManagementListResponse(factories, factoryAdmins, authCodes);
    }

    /**
     * 计算授权码状态。
     * 优先级：disabled > consumed > expired > active
     */
    private String calculateAuthCodeStatus(AuthorizationCode code, LocalDateTime now) {
        if (code.isDisabled()) {
            return "disabled";
        }
        if (code.isConsumed()) {
            return "consumed";
        }
        if (!code.getExpiresAt().isAfter(now)) {
            return "expired";
        }
        return "active";
    }

    /**
     * 重置用户密码。
     * 如果提供了新密码则使用新密码，否则随机生成6位密码。
     */
    @Transactional
    public String resetUserPassword(Long userId, String newPassword) {
        FactoryUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));

        String password;
        if (newPassword != null && !newPassword.isEmpty()) {
            if (newPassword.length() < 6 || newPassword.length() > 64) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度必须为6-64位");
            }
            password = newPassword;
        } else {
            password = generateRandomPassword();
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return password;
    }

    /**
     * 列出超级管理员账号（admin工厂中role=ADMIN的非虚拟用户）。
     */
    @Transactional(readOnly = true)
    public List<FactoryAdminListItem> listSuperAdmins() {
        Factory adminFactory = factoryRepository.findByCode(ADMIN_FACTORY_CODE)
                .orElse(null);
        if (adminFactory == null) return Collections.emptyList();

        return userRepository.findByFactoryId(adminFactory.getId())
                .stream()
                .filter(u -> u.getRole() == Role.ADMIN && !u.isVirtualUser())
                .map(u -> new FactoryAdminListItem(
                        u.getId(),
                        ADMIN_FACTORY_CODE,
                        u.getUsername(),
                        u.isEnabled(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 删除工厂管理员账号。
     */
    @Transactional
    public void deleteFactoryAdmin(Long userId) {
        FactoryUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));
        if (ADMIN_FACTORY_CODE.equals(user.getFactory().getCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能通过此接口删除超级管理员账号");
        }
        userRepository.delete(user);
    }

    /**
     * 删除授权码。
     */
    @Transactional
    public void deleteAuthCode(Long authCodeId) {
        AuthorizationCode authCode = authorizationCodeRepository.findById(authCodeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));
        authorizationCodeRepository.delete(authCode);
    }

    /**
     * 删除超级管理员账号。
     */
    @Transactional
    public void deleteSuperAdmin(Long userId) {
        FactoryUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "资源不存在"));
        
        if (!ADMIN_FACTORY_CODE.equals(user.getFactory().getCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该用户不是超级管理员");
        }
        
        List<FactoryUser> superAdmins = userRepository.findByFactoryId(user.getFactory().getId())
                .stream()
                .filter(u -> u.getRole() == Role.ADMIN && !u.isVirtualUser())
                .collect(Collectors.toList());
        if (superAdmins.size() <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "系统必须保留至少一个超级管理员账号");
        }
        
        userRepository.delete(user);
    }
}
