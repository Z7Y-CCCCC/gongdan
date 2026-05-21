package com.heihu.workorder.service;

import com.heihu.workorder.dto.BindingResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FactoryBindingService 工厂绑定 — 属性测试（jqwik）
 *
 * Feature: phone-factory-binding
 * Validates: Requirements 2.1, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 6.1
 */
class FactoryBindingServicePropertyTest {

    // ─── Property 5 ──────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 5: Successful binding creates correct FactoryUser and returns token with default password
    // **Validates: Requirements 2.1, 2.5, 2.6, 2.7, 6.1**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 5: Successful binding creates correct FactoryUser and returns token with default password")
    void successfulBindingCreatesCorrectFactoryUserAndReturnsTokenWithDefaultPassword(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validAuthCodes") String authCode
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeService authCodeService = mock(AuthorizationCodeService.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        TokenService tokenService = mock(TokenService.class);

        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        // Factory has no non-virtual users (empty list)
        when(userRepo.findByFactoryIdOrderByCreatedAtAsc(factory.getId())).thenReturn(Collections.emptyList());
        // Phone not already bound to this factory
        when(userRepo.findByFactoryIdAndPhone(factory.getId(), phone)).thenReturn(Optional.empty());
        // Auth code validation passes (no exception thrown)
        doNothing().when(authCodeService).validateAndConsume(authCode, factoryCode);
        // Save returns the user with an ID
        when(userRepo.save(any(FactoryUser.class))).thenAnswer(invocation -> {
            FactoryUser user = invocation.getArgument(0);
            user.setId(100L);
            return user;
        });
        // Token service returns a token
        when(tokenService.issue(any(FactoryUser.class))).thenReturn("generated-token-123");

        FactoryBindingService service = new FactoryBindingService(
                factoryRepo, userRepo, authCodeService, passwordEncoder, tokenService
        );

        // Set defaultPasswordLength via reflection (normally injected by @Value)
        setField(service, "defaultPasswordLength", 6);

        // Act
        BindingResponse response = service.bindFactory(phone, factoryCode, authCode);

        // Assert: response contains non-null token
        assertThat(response.getToken()).isNotNull();
        assertThat(response.getToken()).isEqualTo("generated-token-123");

        // Assert: defaultPassword is exactly 6 characters
        assertThat(response.getDefaultPassword()).hasSize(6);

        // Assert: defaultPassword contains only alphanumeric characters
        assertThat(response.getDefaultPassword()).matches("^[a-zA-Z0-9]{6}$");

        // Assert: the saved FactoryUser has correct properties
        ArgumentCaptor<FactoryUser> userCaptor = ArgumentCaptor.forClass(FactoryUser.class);
        verify(userRepo).save(userCaptor.capture());
        FactoryUser savedUser = userCaptor.getValue();

        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.isVirtualUser()).isFalse();
        assertThat(savedUser.getUsername()).isEqualTo(phone);
        assertThat(savedUser.getName()).isEqualTo(phone);
        assertThat(savedUser.getPhone()).isEqualTo(phone);
        assertThat(savedUser.getFactory()).isEqualTo(factory);

        // Assert: bcrypt hash of defaultPassword matches the stored passwordHash
        assertThat(passwordEncoder.matches(response.getDefaultPassword(), savedUser.getPasswordHash())).isTrue();
    }

    // ─── Property 6 ──────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 6: Auth code factory mismatch causes rejection
    // **Validates: Requirements 2.3**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 6: Auth code factory mismatch causes rejection")
    void authCodeFactoryMismatchCausesRejection(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("validFactoryCodes") String factoryCodeA,
            @ForAll("validFactoryCodes") String factoryCodeB,
            @ForAll("validAuthCodes") String authCode
    ) {
        // Ensure factory codes are different
        Assume.that(!factoryCodeA.equals(factoryCodeB));

        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeService authCodeService = mock(AuthorizationCodeService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);

        // The binding request uses factoryCodeB, but the auth code belongs to factoryCodeA
        Factory factoryB = new Factory();
        factoryB.setId(2L);
        factoryB.setCode(factoryCodeB);
        factoryB.setName("Factory B");
        factoryB.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(factoryCodeB)).thenReturn(Optional.of(factoryB));
        when(userRepo.findByFactoryIdOrderByCreatedAtAsc(factoryB.getId())).thenReturn(Collections.emptyList());
        when(userRepo.findByFactoryIdAndPhone(factoryB.getId(), phone)).thenReturn(Optional.empty());

        // Mock authorizationCodeService to throw when factoryCode doesn't match
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "授权码无效"))
                .when(authCodeService).validateAndConsume(authCode, factoryCodeB);

        FactoryBindingService service = new FactoryBindingService(
                factoryRepo, userRepo, authCodeService, passwordEncoder, tokenService
        );

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.bindFactory(phone, factoryCodeB, authCode),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("授权码无效");

        // Verify no user was created
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    // ─── Property 7 ──────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 7: Expired auth code causes rejection
    // **Validates: Requirements 2.4**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 7: Expired auth code causes rejection")
    void expiredAuthCodeCausesRejection(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validAuthCodes") String authCode
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeService authCodeService = mock(AuthorizationCodeService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);

        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        when(userRepo.findByFactoryIdOrderByCreatedAtAsc(factory.getId())).thenReturn(Collections.emptyList());
        when(userRepo.findByFactoryIdAndPhone(factory.getId(), phone)).thenReturn(Optional.empty());

        // Mock authorizationCodeService to throw expired error
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "授权码已过期"))
                .when(authCodeService).validateAndConsume(authCode, factoryCode);

        FactoryBindingService service = new FactoryBindingService(
                factoryRepo, userRepo, authCodeService, passwordEncoder, tokenService
        );

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.bindFactory(phone, factoryCode, authCode),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("授权码已过期");

        // Verify no user was created
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    // ─── Property 8 ──────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 8: Already-bound factory rejects new binding
    // **Validates: Requirements 2.8**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 8: Already-bound factory rejects new binding")
    void alreadyBoundFactoryRejectsNewBinding(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validAuthCodes") String authCode
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeService authCodeService = mock(AuthorizationCodeService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);

        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));

        // Factory already has a non-virtual user (already bound)
        FactoryUser existingUser = new FactoryUser();
        existingUser.setId(50L);
        existingUser.setFactory(factory);
        existingUser.setVirtualUser(false);
        existingUser.setRole(Role.ADMIN);
        existingUser.setEnabled(true);
        existingUser.setUsername("13800000000");
        existingUser.setName("13800000000");
        existingUser.setPhone("13800000000");
        existingUser.setPasswordHash("$2a$10$hash");
        existingUser.setCreatedAt(LocalDateTime.now().minusDays(1));
        existingUser.setUpdatedAt(LocalDateTime.now().minusDays(1));

        when(userRepo.findByFactoryIdOrderByCreatedAtAsc(factory.getId()))
                .thenReturn(Collections.singletonList(existingUser));

        FactoryBindingService service = new FactoryBindingService(
                factoryRepo, userRepo, authCodeService, passwordEncoder, tokenService
        );

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.bindFactory(phone, factoryCode, authCode),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("该工厂已被绑定");

        // Verify auth code was NOT consumed and no user was created
        verifyNoInteractions(authCodeService);
        verify(userRepo, never()).save(any(FactoryUser.class));
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
    Arbitrary<String> validFactoryCodes() {
        // 4-16 alphanumeric characters
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(4)
                .ofMaxLength(16);
    }

    @Provide
    Arbitrary<String> validAuthCodes() {
        // 8 alphanumeric characters (matching the format of generated auth codes)
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofLength(8);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
