package com.heihu.workorder.service;

import com.heihu.workorder.dto.BindingResponse;
import com.heihu.workorder.dto.PhoneLoginResponse;
import com.heihu.workorder.model.AuthorizationCode;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认密码登录集成测试 — 验证绑定后默认密码可用于登录的完整流程。
 *
 * Feature: phone-factory-binding, Property 14: Default password round-trip — binding password works for login
 * **Validates: Requirements 6.1, 6.2**
 *
 * 使用 @RepeatedTest(100) 模拟属性测试效果，
 * 因为 jqwik 不原生支持 Spring Boot 依赖注入。
 */
@SpringBootTest
@ActiveProfiles("test")
class DefaultPasswordLoginIntegrationTest {

    @Autowired
    private FactoryBindingService factoryBindingService;

    @Autowired
    private PhoneAuthService phoneAuthService;

    @Autowired
    private FactoryRepository factoryRepository;

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private Factory testFactory;
    private String authCode;

    @BeforeEach
    void setUp() {
        int iteration = counter.incrementAndGet();

        // Create a unique factory for each iteration (no non-virtual users)
        testFactory = new Factory();
        testFactory.setCode("DPWD_" + iteration + "_" + System.nanoTime());
        testFactory.setName("Default Password Test Factory " + iteration);
        testFactory.setCreatedAt(LocalDateTime.now());
        testFactory = factoryRepository.save(testFactory);

        // Create a valid, unexpired, unconsumed authorization code for this factory
        authCode = generateUniqueAuthCode(iteration);
        AuthorizationCode code = new AuthorizationCode();
        code.setCode(authCode);
        code.setFactory(testFactory);
        code.setCreatedAt(LocalDateTime.now());
        code.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        code.setConsumed(false);
        code.setVersion(0);
        authorizationCodeRepository.save(code);
    }

    /**
     * Property 14: Default password round-trip — binding password works for login.
     *
     * For any successful binding, the returned defaultPassword value, when used as the
     * password in a subsequent loginByPassword(phone, defaultPassword) call, SHALL
     * successfully authenticate and return a token.
     *
     * Complete flow: bind → get default password → login with default password → verify success
     */
    @RepeatedTest(100)
    @DisplayName("Feature: phone-factory-binding, Property 14: Default password round-trip — binding password works for login")
    void defaultPasswordRoundTrip_bindingPasswordWorksForLogin() {
        // Arrange: generate a unique phone number for this iteration
        String phone = generateUniquePhone();

        // Act 1: Perform factory binding
        BindingResponse bindingResponse = factoryBindingService.bindFactory(
                phone, testFactory.getCode(), authCode);

        // Assert binding succeeded
        assertThat(bindingResponse).isNotNull();
        assertThat(bindingResponse.getToken()).isNotNull().isNotEmpty();
        assertThat(bindingResponse.getDefaultPassword()).isNotNull().hasSize(6);
        assertThat(bindingResponse.getUser()).isNotNull();

        String defaultPassword = bindingResponse.getDefaultPassword();

        // Act 2: Login using the default password
        PhoneLoginResponse loginResponse = phoneAuthService.loginByPassword(phone, defaultPassword);

        // Assert login succeeded
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.isBindingRequired())
                .as("Login should NOT require binding since user was just created")
                .isFalse();
        assertThat(loginResponse.getToken())
                .as("Login should return a valid token")
                .isNotNull()
                .isNotEmpty();
        assertThat(loginResponse.getUser())
                .as("Login should return user information")
                .isNotNull();
        assertThat(loginResponse.getUser().getPhone())
                .as("Returned user phone should match the bound phone")
                .isEqualTo(phone);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private String generateUniquePhone() {
        // Generate a unique 11-digit phone number starting with 1
        int iteration = counter.get();
        // Use iteration + nanoTime to ensure uniqueness across repeated tests
        long suffix = (System.nanoTime() % 1_000_000_000L) + iteration;
        String phone = "1" + String.format("%010d", suffix % 10_000_000_000L);
        return phone;
    }

    private String generateUniqueAuthCode(int iteration) {
        // Generate a unique 8-char alphanumeric code
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(8);
        long value = System.nanoTime() + iteration * 31L;
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt((int) (Math.abs(value) % chars.length())));
            value = value / chars.length() + i + 7;
        }
        return sb.toString();
    }
}
