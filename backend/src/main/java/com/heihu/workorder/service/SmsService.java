package com.heihu.workorder.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.SmsCode;
import com.heihu.workorder.model.SmsIpRateLimit;
import com.heihu.workorder.repository.SmsCodeRepository;
import com.heihu.workorder.repository.SmsIpRateLimitRepository;

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
    private final SmsCodeRepository smsCodeRepository;
    private final SmsIpRateLimitRepository smsIpRateLimitRepository;

    public SmsService() {
        this(null, null);
    }

    public SmsService(SmsCodeRepository smsCodeRepository) {
        this(smsCodeRepository, null);
    }

    @Autowired
    public SmsService(SmsCodeRepository smsCodeRepository, SmsIpRateLimitRepository smsIpRateLimitRepository) {
        this.smsCodeRepository = smsCodeRepository;
        this.smsIpRateLimitRepository = smsIpRateLimitRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCode(String phone, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        checkPhoneRateLimit(phone, now);
        recordIpRateLimit(clientIp, now);

        String code = generateCode();
        storeCode(phone, code, now);
        phoneLastSendTime.put(phone, now);

        log.info("验证码已生成 [phone={}, code={}]", phone, code);
    }

    private void checkPhoneRateLimit(String phone, LocalDateTime now) {
        if (smsCodeRepository != null) {
            SmsCode lastCode = smsCodeRepository.findByPhone(phone).orElse(null);
            if (lastCode != null && lastCode.getCreatedAt().plusSeconds(PHONE_RATE_LIMIT_SECONDS).isAfter(now)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
            }
            return;
        }
        LocalDateTime lastSend = phoneLastSendTime.get(phone);
        if (lastSend != null && lastSend.plusSeconds(PHONE_RATE_LIMIT_SECONDS).isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
        }
    }

    private void recordIpRateLimit(String clientIp, LocalDateTime now) {
        if (smsIpRateLimitRepository != null) {
            recordIpRateLimitInDatabase(clientIp, now);
            return;
        }
        recordIpRateLimitInMemory(clientIp, now);
    }

    private void recordIpRateLimitInDatabase(String clientIp, LocalDateTime now) {
        SmsIpRateLimit rateLimit = smsIpRateLimitRepository.findByClientIpForUpdate(clientIp).orElse(null);
        if (rateLimit == null) {
            rateLimit = new SmsIpRateLimit();
            rateLimit.setClientIp(clientIp);
            rateLimit.setRequestCount(1);
            rateLimit.setWindowStart(now);
            smsIpRateLimitRepository.save(rateLimit);
            return;
        }
        if (rateLimit.getWindowStart().plusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES).isBefore(now)) {
            rateLimit.setRequestCount(1);
            rateLimit.setWindowStart(now);
            smsIpRateLimitRepository.save(rateLimit);
            return;
        }
        if (rateLimit.getRequestCount() >= IP_RATE_LIMIT_MAX_REQUESTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
        rateLimit.setRequestCount(rateLimit.getRequestCount() + 1);
        smsIpRateLimitRepository.save(rateLimit);
    }

    private void recordIpRateLimitInMemory(String clientIp, LocalDateTime now) {
        ipRateStore.compute(clientIp, (key, existing) -> {
            if (existing == null || existing.getWindowStart().plusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES).isBefore(now)) {
                return new IpRateEntry(1, now);
            }
            if (existing.getCount() >= IP_RATE_LIMIT_MAX_REQUESTS) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
            existing.incrementCount();
            return existing;
        });
    }

    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();
        if (smsCodeRepository != null) {
            smsCodeRepository.deleteByCreatedAtBefore(now.minusMinutes(CODE_EXPIRY_MINUTES));
        }
        if (smsIpRateLimitRepository != null) {
            smsIpRateLimitRepository.deleteByWindowStartBefore(now.minusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES));
        }

        Iterator<Map.Entry<String, LocalDateTime>> phoneIterator = phoneLastSendTime.entrySet().iterator();
        while (phoneIterator.hasNext()) {
            Map.Entry<String, LocalDateTime> entry = phoneIterator.next();
            if (entry.getValue().plusSeconds(PHONE_RATE_LIMIT_SECONDS).isBefore(now)) {
                phoneIterator.remove();
            }
        }

        Iterator<Map.Entry<String, IpRateEntry>> ipIterator = ipRateStore.entrySet().iterator();
        while (ipIterator.hasNext()) {
            Map.Entry<String, IpRateEntry> entry = ipIterator.next();
            if (entry.getValue().getWindowStart().plusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES).isBefore(now)) {
                ipIterator.remove();
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verifyCode(String phone, String code) {
        return verifyCode(phone, code, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verifyCode(String phone, String code, boolean consume) {
        if (smsCodeRepository != null) {
            return verifyCodeInDatabase(phone, code, consume);
        }
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

    private void storeCode(String phone, String code, LocalDateTime now) {
        if (smsCodeRepository == null) {
            codeStore.put(phone, new SmsCodeEntry(code, now, false));
            return;
        }
        SmsCode smsCode = smsCodeRepository.findByPhone(phone).orElseGet(SmsCode::new);
        smsCode.setPhone(phone);
        smsCode.setCode(code);
        smsCode.setCreatedAt(now);
        smsCode.setVerified(false);
        smsCodeRepository.save(smsCode);
    }

    private boolean verifyCodeInDatabase(String phone, String code, boolean consume) {
        SmsCode entry = smsCodeRepository.findByPhone(phone).orElse(null);
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
            smsCodeRepository.save(entry);
        }
        return true;
    }

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
