package com.heihu.workorder.service;

import com.heihu.workorder.model.SmsCode;
import com.heihu.workorder.repository.SmsCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SmsServiceIntegrationTest {

    @Autowired
    private SmsService smsService;

    @Autowired
    private SmsCodeRepository smsCodeRepository;

    @BeforeEach
    void setUp() {
        smsCodeRepository.deleteAll();
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
}
