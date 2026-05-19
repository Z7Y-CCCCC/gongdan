package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;

/**
 * SmsService 属性测试（jqwik）
 */
class SmsServicePropertyTest {

    // Feature: phone-login, Property 4: 验证码生成格式
    // **Validates: Requirements 3.2**
    @Property(tries = 100)
    void codeGeneratedForValidPhoneShouldBeExactly6Digits(@ForAll("validPhoneNumbers") String phone) {
        SmsService smsService = new SmsService();

        smsService.sendCode(phone, "127.0.0.1");

        SmsService.SmsCodeEntry entry = smsService.getCodeEntry(phone);
        assertThat(entry).isNotNull();
        assertThat(entry.getCode()).isNotNull();
        assertThat(entry.getCode()).hasSize(6);
        assertThat(entry.getCode()).matches("^\\d{6}$");
    }

    // Feature: phone-login, Property 9: 同一手机号 60 秒内发送频率限制
    // **Validates: Requirements 5.1**
    @Property(tries = 100)
    void sendingTwiceConsecutivelyForSamePhoneShouldRejectSecond(@ForAll("validPhoneNumbers") String phone) {
        SmsService smsService = new SmsService();

        // 第一次发送应成功
        smsService.sendCode(phone, "127.0.0.1");

        // 第二次发送应抛出 ApiException with TOO_MANY_REQUESTS
        ApiException exception = catchThrowableOfType(
                () -> smsService.sendCode(phone, "192.168.1.1"),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Provide
    Arbitrary<String> validPhoneNumbers() {
        // 生成以 "1" 开头，后跟 10 位数字的有效手机号
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofLength(10)
                .map(suffix -> "1" + suffix);
    }
}
