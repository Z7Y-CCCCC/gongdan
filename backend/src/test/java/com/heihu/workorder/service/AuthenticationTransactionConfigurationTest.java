package com.heihu.workorder.service;

import com.heihu.workorder.dto.LoginRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationTransactionConfigurationTest {

    @Test
    void tokenIssuingLoginMethodsUseWritableTransactions() throws Exception {
        assertWritable(AuthService.class.getMethod("login", LoginRequest.class));
        assertWritable(PhoneAuthService.class.getMethod("loginByPassword", String.class, String.class));
        assertWritable(PhoneAuthService.class.getMethod("loginBySmsCode", String.class, String.class));
        assertWritable(PhoneAuthService.class.getMethod(
                "selectFactory", String.class, Long.class, String.class, String.class));
    }

    private void assertWritable(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("%s must declare a transaction", method)
                .isNotNull();
        assertThat(transactional.readOnly())
                .as("%s writes the issued session token", method)
                .isFalse();
    }
}
