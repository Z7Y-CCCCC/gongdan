package com.heihu.workorder.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.heihu.workorder.exception.ApiException;

/**
 * 短信验证码服务：负责验证码的生成、存储和校验。
 * 当前阶段仅日志输出验证码，不实际发送短信。
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 5;
    private static final int PHONE_RATE_LIMIT_SECONDS = 60;
    private static final int IP_RATE_LIMIT_MAX_REQUESTS = 10;
    private static final int IP_RATE_LIMIT_WINDOW_MINUTES = 60;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, SmsCodeEntry> codeStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> phoneLastSendTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpRateEntry> ipRateStore = new ConcurrentHashMap<>();

    /**
     * 生成并"发送"验证码（当前仅日志输出）。
     * 发送前检查频率限制：手机号 60 秒内最多 1 次，IP 1 小时内最多 10 次。
     *
     * @param phone    手机号
     * @param clientIp 客户端 IP
     */
    public void sendCode(String phone, String clientIp) {
        checkPhoneRateLimit(phone);
        checkIpRateLimit(clientIp);

        String code = generateCode();
        SmsCodeEntry entry = new SmsCodeEntry(code, LocalDateTime.now(), false);
        codeStore.put(phone, entry);

        // 记录手机号发送时间
        phoneLastSendTime.put(phone, LocalDateTime.now());

        // 记录 IP 发送次数
        ipRateStore.compute(clientIp, (key, existing) -> {
            LocalDateTime now = LocalDateTime.now();
            if (existing == null || existing.getWindowStart().plusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES).isBefore(now)) {
                return new IpRateEntry(1, now);
            }
            existing.incrementCount();
            return existing;
        });

        log.info("验证码已生成 [phone={}, code={}]", phone, code);
    }

    /**
     * 检查手机号维度频率限制：60 秒内同一手机号最多发送 1 次。
     */
    private void checkPhoneRateLimit(String phone) {
        LocalDateTime lastSend = phoneLastSendTime.get(phone);
        if (lastSend != null && lastSend.plusSeconds(PHONE_RATE_LIMIT_SECONDS).isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
        }
    }

    /**
     * 检查 IP 维度频率限制：1 小时内同一 IP 最多发送 10 次。
     */
    private void checkIpRateLimit(String clientIp) {
        IpRateEntry ipEntry = ipRateStore.get(clientIp);
        if (ipEntry == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (ipEntry.getWindowStart().plusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES).isBefore(now)) {
            // 窗口已过期，不限制
            return;
        }
        if (ipEntry.getCount() >= IP_RATE_LIMIT_MAX_REQUESTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
    }

    /**
     * 清理过期的频率限制记录。
     * 可由定时任务调用，或在每次请求时内联清理。
     */
    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();

        // 清理过期的手机号发送记录
        Iterator<Map.Entry<String, LocalDateTime>> phoneIterator = phoneLastSendTime.entrySet().iterator();
        while (phoneIterator.hasNext()) {
            Map.Entry<String, LocalDateTime> entry = phoneIterator.next();
            if (entry.getValue().plusSeconds(PHONE_RATE_LIMIT_SECONDS).isBefore(now)) {
                phoneIterator.remove();
            }
        }

        // 清理过期的 IP 频率记录
        Iterator<Map.Entry<String, IpRateEntry>> ipIterator = ipRateStore.entrySet().iterator();
        while (ipIterator.hasNext()) {
            Map.Entry<String, IpRateEntry> entry = ipIterator.next();
            if (entry.getValue().getWindowStart().plusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES).isBefore(now)) {
                ipIterator.remove();
            }
        }
    }

    /**
     * 校验验证码是否正确且未过期。
     * 校验成功后标记为已使用，防止重放。
     *
     * @param phone 手机号
     * @param code  用户提交的验证码
     * @return true 表示校验通过，false 表示校验失败
     */
    public boolean verifyCode(String phone, String code) {
        return verifyCode(phone, code, true);
    }

    /**
     * 校验验证码，可选择是否立即消费。
     * 多工厂短信登录需要先预校验并展示工厂列表，最终选择工厂时再消费验证码。
     */
    public boolean verifyCode(String phone, String code, boolean consume) {
        SmsCodeEntry entry = codeStore.get(phone);
        if (entry == null) {
            return false;
        }
        if (entry.isVerified()) {
            return false;
        }
        if (entry.getCreatedAt().plusMinutes(CODE_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
            return false;
        }
        if (!entry.getCode().equals(code)) {
            return false;
        }
        if (consume) {
            entry.setVerified(true);
        }
        return true;
    }

    /**
     * 获取指定手机号当前存储的验证码条目（供测试使用）。
     */
    SmsCodeEntry getCodeEntry(String phone) {
        return codeStore.get(phone);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 验证码存储条目。
     */
    static class SmsCodeEntry {
        private final String code;
        private final LocalDateTime createdAt;
        private boolean verified;

        SmsCodeEntry(String code, LocalDateTime createdAt, boolean verified) {
            this.code = code;
            this.createdAt = createdAt;
            this.verified = verified;
        }

        public String getCode() {
            return code;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public boolean isVerified() {
            return verified;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }
    }

    /**
     * IP 频率限制条目。
     */
    static class IpRateEntry {
        private int count;
        private final LocalDateTime windowStart;

        IpRateEntry(int count, LocalDateTime windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }

        public int getCount() {
            return count;
        }

        public LocalDateTime getWindowStart() {
            return windowStart;
        }

        public void incrementCount() {
            this.count++;
        }
    }
}
