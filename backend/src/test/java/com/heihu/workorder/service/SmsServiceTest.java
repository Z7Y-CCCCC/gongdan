package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SmsServiceTest {

    private SmsService smsService;

    @BeforeEach
    void setUp() {
        smsService = new SmsService();
    }

    @Test
    @DisplayName("验证码生成为 6 位数字")
    void sendCode_shouldGenerate6DigitCode() {
        smsService.sendCode("13800138000", "192.168.1.1");

        SmsService.SmsCodeEntry entry = smsService.getCodeEntry("13800138000");

        assertNotNull(entry);
        assertNotNull(entry.getCode());
        assertEquals(6, entry.getCode().length());
        assertTrue(entry.getCode().matches("^\\d{6}$"),
                "验证码应为 6 位数字，实际为: " + entry.getCode());
    }

    @Test
    @DisplayName("正确验证码校验通过")
    void verifyCode_withCorrectCode_shouldReturnTrue() {
        smsService.sendCode("13800138001", "192.168.1.1");

        SmsService.SmsCodeEntry entry = smsService.getCodeEntry("13800138001");
        String code = entry.getCode();

        boolean result = smsService.verifyCode("13800138001", code);

        assertTrue(result, "正确验证码应校验通过");
    }

    @Test
    @DisplayName("验证码非消费校验后仍可消费，消费后不可复用")
    void verifyCode_withoutConsume_shouldKeepCodeUsableUntilConsumed() {
        smsService.sendCode("13800138101", "192.168.1.1");

        SmsService.SmsCodeEntry entry = smsService.getCodeEntry("13800138101");
        String code = entry.getCode();

        assertTrue(smsService.verifyCode("13800138101", code, false), "非消费校验应通过");
        assertFalse(entry.isVerified(), "非消费校验不应标记验证码已使用");
        assertTrue(smsService.verifyCode("13800138101", code), "消费校验应通过");
        assertTrue(entry.isVerified(), "消费校验应标记验证码已使用");
        assertFalse(smsService.verifyCode("13800138101", code), "已消费验证码不能复用");
    }

    @Test
    @DisplayName("错误验证码校验失败")
    void verifyCode_withWrongCode_shouldReturnFalse() {
        smsService.sendCode("13800138002", "192.168.1.1");

        boolean result = smsService.verifyCode("13800138002", "000000");

        assertFalse(result, "错误验证码应校验失败");
    }

    @Test
    @DisplayName("过期验证码校验失败")
    void verifyCode_withExpiredCode_shouldReturnFalse() throws Exception {
        smsService.sendCode("13800138003", "192.168.1.1");

        SmsService.SmsCodeEntry entry = smsService.getCodeEntry("13800138003");
        String code = entry.getCode();

        // 使用反射将 createdAt 设置为 6 分钟前（超过 5 分钟有效期）
        Field createdAtField = SmsService.SmsCodeEntry.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(entry, LocalDateTime.now().minusMinutes(6));

        boolean result = smsService.verifyCode("13800138003", code);

        assertFalse(result, "过期验证码应校验失败");
    }

    @Test
    @DisplayName("手机号 60 秒频率限制")
    void sendCode_samePhoneWithin60Seconds_shouldThrowTooManyRequests() {
        smsService.sendCode("13800138004", "192.168.1.1");

        ApiException exception = assertThrows(ApiException.class, () ->
                smsService.sendCode("13800138004", "192.168.1.2"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    @DisplayName("短信发送器失败时返回服务不可用")
    void sendCode_senderFailure_shouldThrowServiceUnavailable() {
        SmsService service = new SmsService(null, null, (phone, code) -> {
            throw new IllegalStateException("provider failed");
        });

        ApiException exception = assertThrows(ApiException.class, () ->
                service.sendCode("13800138005", "192.168.1.3"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("短信发送失败，请稍后再试", exception.getMessage());
    }

    @Test
    @DisplayName("IP 每小时 10 次限制")
    void sendCode_sameIpExceeds10Times_shouldThrowTooManyRequests() {
        String ip = "10.0.0.1";

        // 使用不同手机号发送 10 次（避免手机号频率限制）
        for (int i = 0; i < 10; i++) {
            String phone = "1380013" + String.format("%04d", i);
            smsService.sendCode(phone, ip);
        }

        // 第 11 次应被拒绝
        ApiException exception = assertThrows(ApiException.class, () ->
                smsService.sendCode("13900000000", ip));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    void concurrentInMemoryFirstSendsCallProviderOnce() throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        SmsService service = new SmsService(null, null, (phone, code) -> deliveries.incrementAndGet());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> sends = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                final int index = i;
                sends.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.sendCode("13912345670", "192.0.2." + index);
                        return true;
                    } catch (ApiException exception) {
                        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> send : sends) if (send.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, deliveries.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void ipRejectionDoesNotReserveAnUnsentPhoneInMemory() {
        SmsService service = new SmsService(null, null, (phone, code) -> { });
        for (int i = 0; i < 10; i++) service.sendCode("1391234500" + i, "192.0.2.1");
        assertThrows(ApiException.class, () -> service.sendCode("13912345671", "192.0.2.1"));
        assertDoesNotThrow(() -> service.sendCode("13912345671", "192.0.2.2"));
    }
}
