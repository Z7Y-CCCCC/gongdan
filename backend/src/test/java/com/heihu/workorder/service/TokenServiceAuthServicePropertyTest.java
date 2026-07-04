package com.heihu.workorder.service;

import com.heihu.workorder.dto.AuthResponse;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.UserSession;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import com.heihu.workorder.repository.UserRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property tests for TokenService and AuthService.
 *
 * Feature: super-admin-management
 * Validates: Requirements 2.2, 2.5, 7.7, 9.1, 9.2, 9.3
 */
class TokenServiceAuthServicePropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Example
    @Label("Feature: authentication, token session expires after configured TTL")
    void tokenSessionExpiresAfterConfiguredTtl() throws InterruptedException {
        TokenService tokenService = new TokenService(Duration.ofMillis(1));
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");

        FactoryUser user = new FactoryUser();
        user.setId(1L);
        user.setFactory(factory);
        user.setName("Test User");
        user.setRole(Role.ADMIN);

        String token = tokenService.issue(user);
        Thread.sleep(10L);

        ApiException exception = catchThrowableOfType(
                () -> tokenService.requireUser("Bearer " + token),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Example
    @Label("Feature: authentication, token TTL must be positive")
    void tokenTtlMustBePositive() {
        assertThatThrownBy(() -> new TokenService(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Example
    @Label("Feature: authentication, database session can be required across instances")
    void databaseSessionCanBeRequiredAcrossInstances() {
        UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
        TokenService issuer = new TokenService(Duration.ofMinutes(10), sessionRepository);
        FactoryUser user = buildUser("HHGD");
        when(sessionRepository.findByUserIdAndKickedFalse(user.getId())).thenReturn(Collections.emptyList());
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String token = issuer.issue(user);
        ArgumentCaptor<UserSession> savedCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionRepository).save(savedCaptor.capture());
        UserSession saved = savedCaptor.getValue();
        when(sessionRepository.findByToken(token)).thenReturn(Optional.of(saved));

        TokenService reader = new TokenService(Duration.ofMinutes(10), sessionRepository);
        AuthenticatedUser currentUser = reader.requireUser("Bearer " + token);

        assertThat(currentUser.getUserId()).isEqualTo(user.getId());
        assertThat(currentUser.getFactoryId()).isEqualTo(user.getFactory().getId());
        assertThat(currentUser.getFactoryCode()).isEqualTo("HHGD");
        assertThat(currentUser.getRole()).isEqualTo(Role.ADMIN);
    }

    @Example
    @Label("Feature: authentication, database session kicks existing user sessions")
    void databaseSessionKicksExistingUserSessions() {
        UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
        TokenService tokenService = new TokenService(Duration.ofMinutes(10), sessionRepository);
        FactoryUser user = buildUser("HHGD");
        UserSession existing = new UserSession();
        existing.setToken("old-token");
        existing.setUserId(user.getId());
        existing.setFactoryId(user.getFactory().getId());
        existing.setFactoryCode(user.getFactory().getCode());
        existing.setRole(user.getRole());
        existing.setDisplayName(user.getName());
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        existing.setCreatedAt(LocalDateTime.now());
        when(sessionRepository.findByUserIdAndKickedFalse(user.getId())).thenReturn(Collections.singletonList(existing));
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tokenService.issue(user);

        assertThat(existing.isKicked()).isTrue();
        verify(sessionRepository, times(2)).save(any(UserSession.class));
    }

    // ─── Property 2 ──────────────────────────────────────────────────────────────
    // For any user login where the factory code is not "admin", the login response
    // SHALL have isSuperAdmin set to false.
    // **Validates: Requirements 2.2**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 2: Non-admin factory login does not grant super admin flag")
    void nonAdminFactoryLoginDoesNotGrantSuperAdminFlag(
            @ForAll("nonAdminFactoryCodes") String factoryCode,
            @ForAll("validUsernames") String username,
            @ForAll("validPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        TokenService tokenService = new TokenService();

        AuthService authService = new AuthService(factoryRepo, userRepo, passwordEncoder, tokenService);

        // The login flow uppercases the factory code
        String uppercasedCode = factoryCode.trim().toUpperCase();

        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(uppercasedCode);
        factory.setName("Test Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());

        FactoryUser user = new FactoryUser();
        user.setId(1L);
        user.setFactory(factory);
        user.setUsername(username);
        user.setName(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVirtualUser(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(uppercasedCode)).thenReturn(Optional.of(factory));
        when(userRepo.findByFactoryIdAndUsername(factory.getId(), username)).thenReturn(Optional.of(user));

        // Act
        LoginRequest request = new LoginRequest();
        request.setFactoryCode(factoryCode);
        request.setUsername(username);
        request.setPassword(password);

        AuthResponse response = authService.login(request);

        // Assert: isSuperAdmin must be false for non-admin factory codes
        assertThat(response.isSuperAdmin()).isFalse();
    }

    // ─── Property 3 ──────────────────────────────────────────────────────────────
    // For any request to a /api/super-admin/ endpoint where the Bearer token is
    // missing, invalid, or belongs to a user whose factory code is not "admin",
    // the system SHALL reject the request with an authentication/authorization error.
    // **Validates: Requirements 2.5, 9.1, 9.2, 9.3**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 3: Super admin endpoint authentication guard")
    void superAdminEndpointRejectsNonAdminTokens(
            @ForAll("nonAdminFactoryCodes") String factoryCode
    ) {
        // Arrange: create a TokenService and issue a token for a non-admin user
        TokenService tokenService = new TokenService();

        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode.trim().toUpperCase());
        factory.setName("Non-Admin Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());

        FactoryUser user = new FactoryUser();
        user.setId(1L);
        user.setFactory(factory);
        user.setUsername("testuser");
        user.setName("Test User");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVirtualUser(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        String token = tokenService.issue(user);
        String authorization = "Bearer " + token;

        // Act & Assert: requireSuperAdmin should reject with FORBIDDEN
        ApiException exception = catchThrowableOfType(
                () -> tokenService.requireSuperAdmin(authorization),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 3: Super admin endpoint rejects missing/invalid tokens")
    void superAdminEndpointRejectsMissingOrInvalidTokens(
            @ForAll("invalidAuthorizationHeaders") String authorization
    ) {
        // Arrange
        TokenService tokenService = new TokenService();

        // Act & Assert: requireSuperAdmin should reject with UNAUTHORIZED
        ApiException exception = catchThrowableOfType(
                () -> tokenService.requireSuperAdmin(authorization),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Property 14 ─────────────────────────────────────────────────────────────
    // For any factory where enabled=false and for any user in that factory with
    // valid credentials, a login attempt SHALL be rejected with a factory disabled error.
    // **Validates: Requirements 7.7**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 14: Disabled factory blocks login")
    void disabledFactoryBlocksLogin(
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validUsernames") String username,
            @ForAll("validPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        TokenService tokenService = new TokenService();

        AuthService authService = new AuthService(factoryRepo, userRepo, passwordEncoder, tokenService);

        String uppercasedCode = factoryCode.trim().toUpperCase();

        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(uppercasedCode);
        factory.setName("Disabled Factory");
        factory.setEnabled(false); // Factory is disabled
        factory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(uppercasedCode)).thenReturn(Optional.of(factory));

        // Act
        LoginRequest request = new LoginRequest();
        request.setFactoryCode(factoryCode);
        request.setUsername(username);
        request.setPassword(password);

        ApiException exception = catchThrowableOfType(
                () -> authService.login(request),
                ApiException.class
        );

        // Assert: login is rejected with factory disabled error
        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo("该工厂已被停用，无法登录");

        // Verify that user lookup was never attempted (factory check happens first)
        verifyNoInteractions(userRepo);
    }

    // ─── Providers ────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> nonAdminFactoryCodes() {
        // Generate factory codes that are NOT "admin" (case-insensitive)
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(16)
                .filter(code -> !"ADMIN".equalsIgnoreCase(code));
    }

    @Provide
    Arbitrary<String> validFactoryCodes() {
        // Any valid factory code (1-16 uppercase alphanumeric)
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(16);
    }

    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(3)
                .ofMaxLength(32);
    }

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(6)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> invalidAuthorizationHeaders() {
        // Generate missing, empty, or invalid authorization headers
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("   "),
                // Invalid token (random string that won't match any session)
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .ofMinLength(10)
                        .ofMaxLength(40)
                        .map(s -> "Bearer " + s)
        );
    }

    private FactoryUser buildUser(String factoryCode) {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Test Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());

        FactoryUser user = new FactoryUser();
        user.setId(1L);
        user.setFactory(factory);
        user.setUsername("admin");
        user.setName("Admin");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVirtualUser(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
