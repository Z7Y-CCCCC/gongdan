package com.heihu.workorder.service;

import com.heihu.workorder.dto.PhoneLoginResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.UserRepository;
import net.jqwik.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PhoneAuthService 首次登录检测 — 属性测试（jqwik）
 *
 * Feature: phone-factory-binding
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6
 */
class PhoneAuthServiceBindingPropertyTest {

    // ─── Property 1 ───────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 1: Unregistered phone returns bindingRequired without credential validation
    // **Validates: Requirements 1.1, 1.3, 1.6**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 1: Unregistered phone returns bindingRequired without credential validation")
    void unregisteredPhoneReturnsBindingRequiredWithoutCredentialValidation(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("randomPasswords") String password
    ) {
        // Arrange: phone has no FactoryUser records
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SmsService smsService = mock(SmsService.class);

        when(userRepository.findAllByPhoneForUpdate(phone)).thenReturn(Collections.emptyList());

        PhoneAuthService service = new PhoneAuthService(userRepository, passwordEncoder, tokenService, smsService);

        // Act
        PhoneLoginResponse response = service.loginByPassword(phone, password);

        // Assert: bindingRequired=true, phone included, no token, no user
        assertThat(response.isBindingRequired()).isTrue();
        assertThat(response.getPhone()).isEqualTo(phone);
        assertThat(response.getToken()).isNull();
        assertThat(response.getUser()).isNull();
        // Password should NOT be validated — no interaction with passwordEncoder
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(tokenService);
    }

    // ─── Property 2 ───────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 2: Registered phone proceeds with normal login flow
    // **Validates: Requirements 1.2**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 2: Registered phone proceeds with normal login flow")
    void registeredPhoneProceedsWithNormalLoginFlow(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("randomPasswords") String password,
            @ForAll("userCounts") int userCount
    ) {
        // Arrange: phone has at least one FactoryUser record
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SmsService smsService = mock(SmsService.class);

        List<FactoryUser> users = IntStream.rangeClosed(1, userCount)
                .mapToObj(i -> createEnabledUser((long) i, phone, "hash" + i, (long) (i + 100)))
                .collect(Collectors.toList());

        when(userRepository.findAllByPhoneForUpdate(phone)).thenReturn(users);
        // Password does not match — this is fine, we just verify it doesn't return bindingRequired
        when(passwordEncoder.matches(eq(password), anyString())).thenReturn(false);

        PhoneAuthService service = new PhoneAuthService(userRepository, passwordEncoder, tokenService, smsService);

        // Act & Assert: should NOT return bindingRequired=true
        // It may throw ApiException (wrong password) which is the normal flow — that's acceptable
        try {
            PhoneLoginResponse response = service.loginByPassword(phone, password);
            // If it returns a response, bindingRequired must NOT be true
            assertThat(response.isBindingRequired()).isFalse();
        } catch (ApiException e) {
            // Normal flow: password validation failed — this is expected behavior for registered phones
            // The key assertion: it did NOT return bindingRequired, it proceeded with password validation
            assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getMessage()).doesNotContain("bindingRequired");
        }

        // Verify that password WAS validated (normal flow engaged)
        verify(passwordEncoder, atLeastOnce()).matches(eq(password), anyString());
    }

    // ─── Property 3 ───────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 3: SMS login with valid code for unregistered phone returns bindingRequired
    // **Validates: Requirements 1.4**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 3: SMS login with valid code for unregistered phone returns bindingRequired")
    void smsLoginWithValidCodeForUnregisteredPhoneReturnsBindingRequired(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("smsCodes") String code
    ) {
        // Arrange: phone has no FactoryUser records, SMS code is valid
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SmsService smsService = mock(SmsService.class);

        when(smsService.verifyCode(phone, code, false)).thenReturn(true);
        when(smsService.verifyCode(phone, code)).thenReturn(true);
        when(userRepository.findAllByPhoneForUpdate(phone)).thenReturn(Collections.emptyList());

        PhoneAuthService service = new PhoneAuthService(userRepository, passwordEncoder, tokenService, smsService);

        // Act
        PhoneLoginResponse response = service.loginBySmsCode(phone, code);

        // Assert: bindingRequired=true with phone included
        assertThat(response.isBindingRequired()).isTrue();
        assertThat(response.getPhone()).isEqualTo(phone);
        assertThat(response.getToken()).isNull();
        assertThat(response.getUser()).isNull();
    }

    // ─── Property 4 ───────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 4: SMS login with invalid code returns auth error without revealing binding state
    // **Validates: Requirements 1.5**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 4: SMS login with invalid code returns auth error without revealing binding state")
    void smsLoginWithInvalidCodeReturnsAuthErrorWithoutRevealingBindingState(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("smsCodes") String code
    ) {
        // Arrange: phone has no FactoryUser records, SMS code is INVALID
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SmsService smsService = mock(SmsService.class);

        when(smsService.verifyCode(phone, code, false)).thenReturn(false);

        PhoneAuthService service = new PhoneAuthService(userRepository, passwordEncoder, tokenService, smsService);

        // Act & Assert: should throw authentication error without revealing bindingRequired state
        ApiException exception = catchThrowableOfType(
                () -> service.loginBySmsCode(phone, code),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Error message must NOT contain "bindingRequired" — don't reveal binding state
        assertThat(exception.getMessage()).doesNotContain("bindingRequired");
        // Should not even query the user repository since SMS code failed first
        verifyNoInteractions(userRepository);
    }

    // ─── Providers ────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> validPhoneNumbers() {
        // 11-digit string starting with 1
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofLength(10)
                .map(suffix -> "1" + suffix);
    }

    @Provide
    Arbitrary<String> randomPasswords() {
        return Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(6)
                .ofMaxLength(32);
    }

    @Provide
    Arbitrary<String> smsCodes() {
        // Typical SMS codes: 4-6 digit strings
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(4)
                .ofMaxLength(6);
    }

    @Provide
    Arbitrary<Integer> userCounts() {
        return Arbitraries.integers().between(1, 5);
    }

    // ─── Helper methods ───────────────────────────────────────────────────────────

    private FactoryUser createEnabledUser(Long userId, String phone, String passwordHash, Long factoryId) {
        Factory factory = new Factory();
        factory.setId(factoryId);
        factory.setCode("FAC-" + factoryId);
        factory.setName("Factory " + factoryId);
        factory.setCreatedAt(LocalDateTime.now());

        FactoryUser user = new FactoryUser();
        user.setId(userId);
        user.setFactory(factory);
        user.setPhone(phone);
        user.setUsername("user" + userId);
        user.setName("User " + userId);
        user.setPasswordHash(passwordHash);
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVirtualUser(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
