package com.heihu.workorder.service;

import com.heihu.workorder.dto.FactoryAdminResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import net.jqwik.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property tests for SuperAdminService factory admin generation.
 *
 * Feature: super-admin-management
 * Validates: Requirements 4.3, 4.5
 */
class SuperAdminServiceFactoryAdminPropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private SuperAdminService createService(FactoryRepository factoryRepo, UserRepository userRepo) {
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        return new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);
    }

    private Factory createEnabledFactory(String code) {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(code);
        factory.setName("Test Factory " + code);
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        return factory;
    }

    // ─── Property 7: Generated password format compliance ─────────────────────────
    // For any factory admin creation request where no password is provided,
    // the generated password SHALL be exactly 6 characters long and match the
    // pattern [A-Za-z0-9]{6}.
    // **Validates: Requirements 4.3, 4.5**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 7: Generated password format compliance")
    void generatedPasswordFormatCompliance(
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validUsernames") String username
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        Factory factory = createEnabledFactory(factoryCode);
        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        when(userRepo.findByFactoryIdAndUsername(factory.getId(), username)).thenReturn(Optional.empty());
        when(userRepo.save(any(FactoryUser.class))).thenAnswer(invocation -> {
            FactoryUser u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        // Act - pass null password to trigger generation
        FactoryAdminResponse response = service.createFactoryAdmin(factoryCode, username, null);

        // Assert - generated password must be exactly 6 chars matching [A-Za-z0-9]{6}
        assertThat(response.getPassword()).isNotNull();
        assertThat(response.getPassword()).hasSize(6);
        assertThat(response.getPassword()).matches("[A-Za-z0-9]{6}");
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 7: Generated password format compliance - empty string password")
    void generatedPasswordFormatComplianceWithEmptyPassword(
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validUsernames") String username
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        Factory factory = createEnabledFactory(factoryCode);
        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        when(userRepo.findByFactoryIdAndUsername(factory.getId(), username)).thenReturn(Optional.empty());
        when(userRepo.save(any(FactoryUser.class))).thenAnswer(invocation -> {
            FactoryUser u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        // Act - pass empty string password to trigger generation
        FactoryAdminResponse response = service.createFactoryAdmin(factoryCode, username, "");

        // Assert - generated password must be exactly 6 chars matching [A-Za-z0-9]{6}
        assertThat(response.getPassword()).isNotNull();
        assertThat(response.getPassword()).hasSize(6);
        assertThat(response.getPassword()).matches("[A-Za-z0-9]{6}");
    }

    // ─── Property 8: Factory admin username uniqueness within factory ─────────────
    // For any factory and for any username that already exists in that factory,
    // a second factory admin creation request with the same username and factory
    // code SHALL be rejected.
    // **Validates: Requirements 4.3, 4.5**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 8: Factory admin username uniqueness within factory")
    void factoryAdminUsernameUniquenessWithinFactory(
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validUsernames") String username
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        Factory factory = createEnabledFactory(factoryCode);
        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));

        // Simulate existing user with same username in same factory
        FactoryUser existingUser = new FactoryUser();
        existingUser.setId(99L);
        existingUser.setFactory(factory);
        existingUser.setUsername(username);
        when(userRepo.findByFactoryIdAndUsername(factory.getId(), username))
                .thenReturn(Optional.of(existingUser));

        // Act & Assert - should reject with "用户名已存在"
        ApiException exception = catchThrowableOfType(
                () -> service.createFactoryAdmin(factoryCode, username, "password123"),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("用户名已存在");

        // Verify no user was saved
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    // ─── Providers ────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> validFactoryCodes() {
        // Valid factory codes: [A-Z0-9]{1,16} that are NOT "ADMIN"
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(16)
                .filter(code -> !"ADMIN".equalsIgnoreCase(code));
    }

    @Provide
    Arbitrary<String> validUsernames() {
        // Valid usernames: 1-64 characters, non-blank
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(30); // Keep reasonable for test performance
    }
}
