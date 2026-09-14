package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.SmsCode;
import com.heihu.workorder.repository.SmsCodeRepository;
import com.heihu.workorder.repository.SmsIpRateLimitRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sms_code_audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
@ActiveProfiles("test")
class SmsServiceIntegrationTest {

    @MockBean
    private SmsSender smsSender;

    @Autowired
    private SmsService smsService;

    @Autowired
    private SmsCodeRepository smsCodeRepository;

    @Autowired
    private SmsIpRateLimitRepository smsIpRateLimitRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        smsCodeRepository.deleteAll();
        smsIpRateLimitRepository.deleteAll();
    }

    @Test
    void sendCodePersistsCodeAndVerifyConsumesIt() {
        String phone = "13800138888";
        smsService.sendCode(phone, "10.0.0.88");

        SmsCode stored = smsCodeRepository.findByPhone(phone).orElseThrow(AssertionError::new);

        assertThat(stored.getCode()).matches("\\d{6}");
        assertThat(stored.isVerified()).isFalse();
        assertThat(smsService.verifyCode(phone, stored.getCode(), false)).isTrue();
        assertThat(smsCodeRepository.findByPhone(phone).orElseThrow(AssertionError::new).isVerified()).isFalse();
        assertThat(smsService.verifyCode(phone, stored.getCode())).isTrue();
        assertThat(smsCodeRepository.findByPhone(phone).orElseThrow(AssertionError::new).isVerified()).isTrue();
        assertThat(smsService.verifyCode(phone, stored.getCode())).isFalse();
    }

    @Test
    void concurrentRequestsCanConsumeSmsCodeOnlyOnce() throws Exception {
        String phone = "13800138777";
        SmsCode stored = new SmsCode();
        stored.setPhone(phone);
        stored.setCode("123456");
        stored.setCreatedAt(LocalDateTime.now());
        stored.setVerified(false);
        smsCodeRepository.saveAndFlush(stored);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return smsService.verifyCode(phone, "123456");
                }));
            }
            start.countDown();
            int successful = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(15, TimeUnit.SECONDS)) successful++;
            }
            assertThat(successful).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void phoneRateLimitUsesPersistedLastCodeAcrossServiceInstances() {
        String phone = "13800139999";
        smsService.sendCode(phone, "10.0.0.99");
        SmsService anotherInstance = new SmsService(smsCodeRepository, smsIpRateLimitRepository);

        ApiException exception = assertThrows(ApiException.class, () ->
                new TransactionTemplate(transactionManager).execute(status -> {
                    anotherInstance.sendCode(phone, "10.0.0.100");
                    return null;
                }));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("发送过于频繁，请稍后再试", exception.getMessage());
    }

    @Test
    @Transactional
    void ipRateLimitUsesPersistedCounterAcrossServiceInstances() {
        String ip = "10.0.0.123";
        for (int i = 0; i < 10; i++) {
            smsService.sendCode("1388812" + String.format("%04d", i), ip);
        }
        SmsService anotherInstance = new SmsService(smsCodeRepository, smsIpRateLimitRepository);

        ApiException exception = assertThrows(ApiException.class, () ->
                anotherInstance.sendCode("13988120000", ip));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("请求过于频繁，请稍后再试", exception.getMessage());
        assertThat(smsIpRateLimitRepository.findByClientIp(ip).orElseThrow(AssertionError::new).getRequestCount()).isEqualTo(10);
    }
}
