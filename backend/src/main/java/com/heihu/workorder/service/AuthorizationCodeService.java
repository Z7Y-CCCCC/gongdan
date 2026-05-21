package com.heihu.workorder.service;

import com.heihu.workorder.dto.AuthCodeListItem;
import com.heihu.workorder.dto.AuthCodeResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.AuthorizationCode;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationCodeService {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final int DEFAULT_EXPIRATION_MINUTES = 30;

    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final FactoryRepository factoryRepository;
    private final SecureRandom secureRandom;

    public AuthorizationCodeService(
            AuthorizationCodeRepository authorizationCodeRepository,
            FactoryRepository factoryRepository
    ) {
        this.authorizationCodeRepository = authorizationCodeRepository;
        this.factoryRepository = factoryRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * 为指定工厂生成授权码。
     *
     * @param factoryCode       工厂代码
     * @param expirationMinutes 有效期（分钟），为 null 时默认 30 分钟
     * @return 包含授权码和过期时间的响应
     */
    @Transactional
    public AuthCodeResponse generateCode(String factoryCode, Integer expirationMinutes) {
        // 验证有效期范围
        int minutes = DEFAULT_EXPIRATION_MINUTES;
        if (expirationMinutes != null) {
            if (expirationMinutes < 1 || expirationMinutes > 1440) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "有效期必须在1-1440分钟之间");
            }
            minutes = expirationMinutes;
        }

        // 验证工厂代码存在
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "工厂代码无效"));

        // 生成 8 位随机字母数字授权码
        String code = generateRandomCode();

        // 创建并保存授权码实体
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(minutes);

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode(code);
        authorizationCode.setFactory(factory);
        authorizationCode.setCreatedAt(now);
        authorizationCode.setExpiresAt(expiresAt);
        authorizationCode.setConsumed(false);

        authorizationCodeRepository.save(authorizationCode);

        return new AuthCodeResponse(code, expiresAt, factory.getCode());
    }

    /**
     * 验证并消费授权码。
     * 验证授权码存在、匹配工厂、未过期、未消费，然后使用乐观锁标记为已消费。
     *
     * @param code        授权码
     * @param factoryCode 工厂代码
     */
    @Transactional
    public void validateAndConsume(String code, String factoryCode) {
        // 1. 查找授权码，不存在则抛出异常
        AuthorizationCode authCode = authorizationCodeRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "授权码无效"));

        // 2. 验证授权码关联的工厂与提供的工厂代码匹配
        if (!authCode.getFactory().getCode().equals(factoryCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "授权码无效");
        }

        // 3. 检查是否已被消费
        if (authCode.isConsumed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "授权码已被使用");
        }

        // 4. 检查是否已过期
        if (authCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "授权码已过期");
        }

        // 5. 标记为已消费
        authCode.setConsumed(true);
        authCode.setConsumedAt(LocalDateTime.now());

        // 6. 保存（触发 @Version 乐观锁检查）
        try {
            authorizationCodeRepository.save(authCode);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // 7. 乐观锁冲突，转换为业务异常
            throw new ApiException(HttpStatus.CONFLICT, "授权码已被使用");
        }
    }

    /**
     * 查询指定工厂的授权码列表，按创建时间降序排列。
     * 每个授权码的状态根据当前时间动态计算：active、expired、consumed。
     */
    public List<AuthCodeListItem> listByFactory(String factoryCode) {
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "工厂代码无效"));

        List<AuthorizationCode> codes = authorizationCodeRepository.findByFactoryIdOrderByCreatedAtDesc(factory.getId());

        LocalDateTime now = LocalDateTime.now();

        return codes.stream()
                .map(ac -> {
                    String status;
                    if (ac.isDisabled()) {
                        status = "disabled";
                    } else if (ac.isConsumed()) {
                        status = "consumed";
                    } else if (!ac.getExpiresAt().isAfter(now)) {
                        status = "expired";
                    } else {
                        status = "active";
                    }
                    return new AuthCodeListItem(ac.getId(), ac.getCode(), factoryCode, status, ac.getCreatedAt(), ac.getExpiresAt());
                })
                .collect(Collectors.toList());
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }
}
