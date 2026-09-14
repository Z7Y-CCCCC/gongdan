package com.heihu.workorder.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
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
    private final SmsSender smsSender;

    public SmsService() {
        this(null, null, new LoggingSmsSender(true));
    }

    public SmsService(SmsCodeRepository smsCodeRepository) {
        this(smsCodeRepository, null, new LoggingSmsSender(true));
    }

    public SmsService(SmsCodeRepository smsCodeRepository, SmsIpRateLimitRepository smsIpRateLimitRepository) {
        this(smsCodeRepository, smsIpRateLimitRepository, new LoggingSmsSender(true));
    }

    @Autowired
    public SmsService(
            SmsCodeRepository smsCodeRepository,
            SmsIpRateLimitRepository smsIpRateLimitRepository,
            SmsSender smsSender
    ) {
        this.smsCodeRepository = smsCodeRepository;
        this.smsIpRateLimitRepository = smsIpRateLimitRepository;
        this.smsSender = smsSender;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCode(String phone, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        String code = generateCode();

        if (smsCodeRepository != null) {
            // Always acquire the phone row before the IP row. A new phone is inserted
            // and flushed first so concurrent first sends cannot both reach the provider.
            SmsCode reservation = reservePhoneForSend(phone, code);
            recordIpRateLimit(clientIp, LocalDateTime.now());
            // Time spent waiting for the IP lock must not consume the phone's cooldown or code lifetime.
            reservation.setCreatedAt(LocalDateTime.now());
            smsCodeRepository.flush();
        } else {
            // The no-database fallback needs an atomic first-send reservation as well.
            phoneLastSendTime.compute(phone, (key, previous) -> {
                if (previous != null && previous.plusSeconds(PHONE_RATE_LIMIT_SECONDS).isAfter(now)) {
                    throw phoneRateLimitExceeded();
                }
                return now;
            });
            try {
                recordIpRateLimit(clientIp, now);
            } catch (RuntimeException exception) {
                phoneLastSendTime.remove(phone, now);
                throw exception;
            }
            codeStore.put(phone, new SmsCodeEntry(code, now, false));
        }

        try {
            smsSender.sendVerificationCode(phone, code);
        } catch (RuntimeException ex) {
            log.warn("SMS delivery failed [phone={}, reason={}]", phone, ex.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "短信发送失败，请稍后再试");
        }
        log.info("SMS code sent [phone={}]", phone);
    }

    private SmsCode reservePhoneForSend(String phone, String code) {
        try {
            SmsCode entry = smsCodeRepository.findByPhoneForUpdate(phone).orElse(null);
            LocalDateTime now = LocalDateTime.now();
            if (entry != null && entry.getCreatedAt().plusSeconds(PHONE_RATE_LIMIT_SECONDS).isAfter(now)) {
                throw phoneRateLimitExceeded();
            }
            if (entry == null) entry = new SmsCode();
            entry.setPhone(phone);
            entry.setCode(code);
            entry.setCreatedAt(now);
            entry.setVerified(false);
            return smsCodeRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            // The unique phone constraint arbitrates the missing-row race. Do not retry
            // the whole send operation: provider calls are external, non-transactional effects.
            throw phoneRateLimitExceeded();
        }
    }

    private ApiException phoneRateLimitExceeded() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
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
                phoneLastSendTime.remove(entry.getKey(), entry.getValue());
            }
        }

        Iterator<Map.Entry<String, IpRateEntry>> ipIterator = ipRateStore.entrySet().iterator();
        while (ipIterator.hasNext()) {
            Map.Entry<String, IpRateEntry> entry = ipIterator.next();
            if (entry.getValue().getWindowStart().plusMinutes(IP_RATE_LIMIT_WINDOW_MINUTES).isBefore(now)) {
                ipRateStore.remove(entry.getKey(), entry.getValue());
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
        synchronized (entry) {
            if (entry.isVerified()
                    || !entry.getCreatedAt().plusMinutes(CODE_EXPIRY_MINUTES).isAfter(LocalDateTime.now())
                    || !entry.getCode().equals(code)) {
                return false;
            }
            if (consume) entry.setVerified(true);
            return true;
        }
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

    private boolean verifyCodeInDatabase(String phone, String code, boolean consume) {
        if (consume) {
            // A single compare-and-set lets only one concurrent request consume a code.
            return smsCodeRepository.consumeValidCode(phone, code,
                    LocalDateTime.now().minusMinutes(CODE_EXPIRY_MINUTES)) == 1;
        }
        SmsCode entry = smsCodeRepository.findByPhone(phone).orElse(null);
        if (entry == null) {
            return false;
        }
        if (entry.isVerified()) {
            return false;
        }
        if (!entry.getCreatedAt().plusMinutes(CODE_EXPIRY_MINUTES).isAfter(LocalDateTime.now())) {
            return false;
        }
        if (!entry.getCode().equals(code)) {
            return false;
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
