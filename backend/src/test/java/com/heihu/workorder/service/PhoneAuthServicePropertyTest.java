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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PhoneAuthService 属性测试（jqwik）
 */
class PhoneAuthServicePropertyTest {

    // Feature: phone-factory-binding, Property 1: Unregistered phone returns bindingRequired without credential validation
    // **Validates: Requirements 1.1, 1.3, 1.6**
    @Property(tries = 100)
    void unregisteredPhoneShouldReturnBindingRequired(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("randomPasswords") String password
    ) {
        // Arrange: phone not found in system
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SmsService smsService = mock(SmsService.class);

        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.emptyList());

        PhoneAuthService service = new PhoneAuthService(userRepository, passwordEncoder, tokenService, smsService);

        // Act
        PhoneLoginResponse response = service.loginByPassword(phone, password);

        // Assert: bindingRequired=true, phone set, no token or user
        assertThat(response.isBindingRequired()).isTrue();
        assertThat(response.getPhone()).isEqualTo(phone);
        assertThat(response.getToken()).isNull();
        assertThat(response.getUser()).isNull();
        // Password should NOT be validated (no interaction with passwordEncoder)
        verifyNoInteractions(passwordEncoder);
    }

    // Feature: phone-login, Property 2: 无效凭证统一拒绝（密码登录）
    // **Validates: Requirements 2.4, 2.5**
    @Property(tries = 100)
    void invalidCredentialsShouldReturn401_wrongPassword(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("randomPasswords") String password
    ) {
        // Arrange: phone exists but password doesn't match
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SmsService smsService = mock(SmsService.class);

        FactoryUser user = createEnabledUser(1L, phone, "hashedPwd", 1L);
        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches(password, "hashedPwd")).thenReturn(false);

        PhoneAuthService service = new PhoneAuthService(userRepository, passwordEncoder, tokenService, smsService);

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.loginByPassword(phone, password),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo("手机号或密码不正确");
    }

    // Feature: phone-login, Property 11: 单用户匹配直接登录 vs 多用户匹配返回工厂列表
    // **Validates: Requirements 6.1, 6.2**
    @Property(tries = 100)
    void singleUserMatchShouldLoginDirectly_multipleUsersShouldReturnFactoryList(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("randomPasswords") String password,
            @ForAll("userCounts") int userCount
    ) {
        // Arrange
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SmsService smsService = mock(SmsService.class);

        List<FactoryUser> enabledUsers = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            FactoryUser user = createEnabledUser((long) (i + 1), phone, "hash" + i, (long) (i + 100));
            enabledUsers.add(user);
        }

        when(userRepository.findAllByPhone(phone)).thenReturn(enabledUsers);
        // All passwords match
        for (FactoryUser u : enabledUsers) {
            when(passwordEncoder.matches(password, u.getPasswordHash())).thenReturn(true);
        }
        when(tokenService.issue(any(FactoryUser.class))).thenReturn("mock-token-" + phone);

        PhoneAuthService service = new PhoneAuthService(userRepository, passwordEncoder, tokenService, smsService);

        // Act
        PhoneLoginResponse response = service.loginByPassword(phone, password);

        // Assert
        if (userCount == 1) {
            assertThat(response.isSelectRequired()).isFalse();
            assertThat(response.getToken()).isNotNull();
            assertThat(response.getToken()).isNotEmpty();
        } else {
            assertThat(response.isSelectRequired()).isTrue();
            assertThat(response.getFactories()).hasSize(userCount);
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> validPhoneNumbers() {
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
    Arbitrary<Integer> userCounts() {
        return Arbitraries.integers().between(1, 5);
    }

    // --- Helper methods ---

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
