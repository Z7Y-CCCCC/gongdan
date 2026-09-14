package com.heihu.workorder.service;

import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.UserRepository;
import javax.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationTransactionConfigurationTest {

    @Test
    void tokenIssuingLoginMethodsUseWritableTransactions() throws Exception {
        assertWritable(AuthService.class.getMethod("login", LoginRequest.class));
        assertWritable(TokenService.class.getMethod("issue", FactoryUser.class));
        assertWritable(TokenService.class.getMethod("issue", FactoryUser.class,
                String.class, String.class, String.class, String.class));
        assertWritable(PhoneAuthService.class.getMethod("loginByPassword", String.class, String.class));
        assertWritable(PhoneAuthService.class.getMethod("loginBySmsCode", String.class, String.class));
        assertWritable(PhoneAuthService.class.getMethod(
                "selectFactory", String.class, Long.class, String.class, String.class));
    }

    @Test
    void credentialLookupMethodsAcquireUserWriteLocksWithoutChangingGenericReadQueries() throws Exception {
        assertThat(UserRepository.class.getMethod("findByFactoryIdAndUsernameForUpdate", Long.class, String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(UserRepository.class.getMethod("findByFactoryIdAndPhoneForUpdate", Long.class, String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(UserRepository.class.getMethod("findAllByPhoneForUpdate", String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(UserRepository.class.getMethod("findByFactoryIdAndUsername", Long.class, String.class)
                .getAnnotation(Lock.class)).isNull();
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
