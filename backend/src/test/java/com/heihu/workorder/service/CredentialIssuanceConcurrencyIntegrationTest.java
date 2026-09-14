package com.heihu.workorder.service;

import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.dto.MobileBindRequest;
import com.heihu.workorder.dto.MobileWechatLoginRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.SmsCode;
import com.heihu.workorder.repository.AuthAttemptRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.SmsCodeRepository;
import com.heihu.workorder.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Uses isolated H2 transactions to prove credential checks are inside the issuance lock. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:credential_issuance_audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_TIMEOUT=10000",
        "spring.cache.type=none",
        "logging.file.name=output/credential-issuance-test.log"
})
@ActiveProfiles("test")
@Import(CredentialIssuanceConcurrencyIntegrationTest.EncoderConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CredentialIssuanceConcurrencyIntegrationTest {
    private static final String OLD_PASSWORD = "old-test-password";
    private static final String NEW_PASSWORD = "new-test-password";
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);

    @Autowired private AuthService authService;
    @Autowired private PhoneAuthService phoneAuthService;
    @Autowired private MobileAuthService mobileAuthService;
    @Autowired private TokenService tokenService;
    @Autowired private UserService userService;
    @Autowired private FactoryRepository factories;
    @Autowired private UserRepository users;
    @Autowired private AuthAttemptRepository attempts;
    @Autowired private SmsCodeRepository codes;
    @Autowired private GatedPasswordEncoder encoder;

    private Factory factory;
    private FactoryUser user;

    @BeforeEach
    void fixtures() {
        encoder.gate = null;
        factory = new Factory();
        factory.setCode("CRED" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT));
        factory.setName("Credential transaction fixture");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        factory = factories.saveAndFlush(factory);

        user = new FactoryUser();
        user.setFactory(factory);
        user.setUsername("credential-user");
        user.setPhone("139" + String.format("%08d", PHONE_SEQUENCE.getAndIncrement()));
        user.setName("Credential fixture user");
        user.setPasswordHash(encoder.encode(OLD_PASSWORD));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVirtualUser(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user = users.saveAndFlush(user);
    }

    @Test
    void webUsernameLoginSerializesPasswordResetWithIssuance() throws Exception {
        assertResetCannotCommitBetweenCredentialCheckAndIssuance(CredentialFlow.WEB_USERNAME);
    }

    @Test
    void webPhoneFallbackSerializesPasswordResetWithIssuance() throws Exception {
        assertResetCannotCommitBetweenCredentialCheckAndIssuance(CredentialFlow.WEB_PHONE);
    }

    @Test
    void phonePasswordLoginSerializesPasswordResetWithIssuance() throws Exception {
        assertResetCannotCommitBetweenCredentialCheckAndIssuance(CredentialFlow.PHONE_PASSWORD);
    }

    @Test
    void phoneFactorySelectionSerializesPasswordResetWithIssuance() throws Exception {
        assertResetCannotCommitBetweenCredentialCheckAndIssuance(CredentialFlow.PHONE_SELECTION);
    }

    @Test
    void mobileBindingSerializesPasswordResetWithIssuance() throws Exception {
        assertResetCannotCommitBetweenCredentialCheckAndIssuance(CredentialFlow.MOBILE_BIND);
    }

    private void assertResetCannotCommitBetweenCredentialCheckAndIssuance(CredentialFlow flow) throws Exception {
        Gate gate = new Gate();
        encoder.gate = gate;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch resetStarted = new CountDownLatch(1);
        try {
            Future<String> login = executor.submit(() -> login(flow));
            assertThat(gate.checked.await(10, TimeUnit.SECONDS)).as("password was checked").isTrue();
            Future<?> reset = executor.submit(() -> {
                resetStarted.countDown();
                return userService.resetPassword(factory.getId(), user.getId(), NEW_PASSWORD);
            });
            assertThat(resetStarted.await(10, TimeUnit.SECONDS)).isTrue();
            // Reset must be waiting for the credential check's user row lock, not commit ahead of issuance.
            assertThrows(TimeoutException.class, () -> reset.get(500, TimeUnit.MILLISECONDS));
            gate.release.countDown();
            String token = login.get(20, TimeUnit.SECONDS);
            reset.get(20, TimeUnit.SECONDS);
            encoder.gate = null;

            assertThat(token).isNotBlank();
            assertThat(users.findById(user.getId()).get().getPasswordHash()).isEqualTo(encoder.encode(NEW_PASSWORD));
            assertThat(assertThrows(ApiException.class, () -> tokenService.requireUser("Bearer " + token)).getStatus())
                    .as("the reset must revoke every session issued with the old credential")
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(assertThrows(ApiException.class, () -> login(flow)).getStatus())
                    .as("the old password cannot authenticate after the reset")
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            encoder.gate = null;
            gate.release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private String login(CredentialFlow flow) {
        switch (flow) {
            case PHONE_PASSWORD:
                return phoneAuthService.loginByPassword(user.getPhone(), OLD_PASSWORD).getToken();
            case PHONE_SELECTION:
                return phoneAuthService.selectFactory(user.getPhone(), factory.getId(), "password", OLD_PASSWORD).getToken();
            case MOBILE_BIND:
                MobileWechatLoginRequest wechat = new MobileWechatLoginRequest();
                wechat.setMockOpenid("credential-fixture-" + UUID.randomUUID());
                String ticket = mobileAuthService.wechatLogin(wechat).getOpenidTicket();
                MobileBindRequest bind = new MobileBindRequest();
                bind.setOpenidTicket(ticket);
                bind.setFactoryCode(factory.getCode());
                bind.setUsername(user.getUsername());
                bind.setPassword(OLD_PASSWORD);
                return mobileAuthService.bind(bind).getToken();
            default:
                LoginRequest request = new LoginRequest();
                request.setFactoryCode(factory.getCode());
                request.setUsername(flow == CredentialFlow.WEB_PHONE ? user.getPhone() : user.getUsername());
                request.setPassword(OLD_PASSWORD);
                return authService.login(request).getToken();
        }
    }

    @Test
    void badPasswordStillCommitsItsFailureCountWhileTheLoginTransactionRollsBack() {
        userService.resetPassword(factory.getId(), user.getId(), NEW_PASSWORD);
        assertThrows(ApiException.class, () -> login(CredentialFlow.WEB_USERNAME));
        String key = AuthAttemptLimiter.key("web", factory.getCode(), user.getUsername());
        assertThat(attempts.findByAttemptKey(key).get().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void smsConsumptionSurvivesDisabledAccountLoginFailure() {
        assertConsumedCodeSurvivesFailure(false);
    }

    @Test
    void smsConsumptionSurvivesDisabledAccountFactorySelectionFailure() {
        assertConsumedCodeSurvivesFailure(true);
    }

    private void assertConsumedCodeSurvivesFailure(boolean factorySelection) {
        user.setEnabled(false);
        users.saveAndFlush(user);
        SmsCode code = new SmsCode();
        code.setPhone(user.getPhone());
        code.setCode("246810");
        code.setCreatedAt(LocalDateTime.now());
        code.setVerified(false);
        codes.saveAndFlush(code);
        assertThrows(ApiException.class, () -> {
            if (factorySelection) {
                phoneAuthService.selectFactory(user.getPhone(), factory.getId(), "sms", "246810");
            } else {
                phoneAuthService.loginBySmsCode(user.getPhone(), "246810");
            }
        });
        assertThat(codes.findByPhone(user.getPhone()).get().isVerified()).isTrue();
    }

    private enum CredentialFlow { WEB_USERNAME, WEB_PHONE, PHONE_PASSWORD, PHONE_SELECTION, MOBILE_BIND }

    private static final class Gate {
        private final CountDownLatch checked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
    }

    /** Deliberately test-only: avoids timing a BCrypt implementation when testing database lock ordering. */
    static class GatedPasswordEncoder implements PasswordEncoder {
        private volatile Gate gate;

        @Override
        public String encode(CharSequence rawPassword) { return "fixture-hash:" + rawPassword; }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            boolean matches = encode(rawPassword).equals(encodedPassword);
            Gate current = gate;
            if (matches && current != null && OLD_PASSWORD.contentEquals(rawPassword)) {
                current.checked.countDown();
                try {
                    if (!current.release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("credential test gate timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("credential test gate interrupted", error);
                }
            }
            return matches;
        }
    }

    @TestConfiguration
    static class EncoderConfiguration {
        @Bean
        @Primary
        GatedPasswordEncoder credentialTestEncoder() { return new GatedPasswordEncoder(); }
    }
}
