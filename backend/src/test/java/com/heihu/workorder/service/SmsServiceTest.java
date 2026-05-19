package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

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
}
