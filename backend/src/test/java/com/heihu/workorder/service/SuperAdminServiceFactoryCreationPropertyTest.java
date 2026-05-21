package com.heihu.workorder.service;

import com.heihu.workorder.dto.FactoryResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
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
 * Property tests for SuperAdminService factory creation validation.
 *
 * Feature: super-admin-management
 * Validates: Requirements 1.5, 3.1, 3.3, 3.4, 3.5, 3.7
 */
class SuperAdminServiceFactoryCreationPropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private SuperAdminService createService(FactoryRepository factoryRepo) {
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        return new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);
    }

    // ─── Property 1: Reserved factory code protection ─────────────────────────────
    // For any factory creation request where the factory code equals "ADMIN"
    // (case-insensitive comparison), the SuperAdminService SHALL reject the request
    // with an error.
    // **Validates: Requirements 1.5, 3.4**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 1: Reserved factory code protection")
    void reservedFactoryCodeIsRejected(
            @ForAll("adminCodeVariants") String reservedCode,
            @ForAll("validFactoryNames") String factoryName
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        SuperAdminService service = createService(factoryRepo);

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createFactory(reservedCode, factoryName),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("该工厂代码为系统保留代码");

        // Verify no factory was saved
        verify(factoryRepo, never()).save(any(Factory.class));
    }

    // ─── Property 4: Valid factory creation succeeds ──────────────────────────────
    // For any factory code matching [A-Z0-9]{1,16} that is not "ADMIN"
    // (case-insensitive) and does not already exist, and for any factory name of
    // 1-128 characters that does not already exist, the SuperAdminService SHALL
    // successfully create the factory.
    // **Validates: Requirements 3.1**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 4: Valid factory creation succeeds")
    void validFactoryCreationSucceeds(
            @ForAll("validNonAdminFactoryCodes") String factoryCode,
            @ForAll("validFactoryNames") String factoryName
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        SuperAdminService service = createService(factoryRepo);

        // No existing factory with same code or name
        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.empty());
        when(factoryRepo.findByName(factoryName)).thenReturn(Optional.empty());
        when(factoryRepo.save(any(Factory.class))).thenAnswer(invocation -> {
            Factory f = invocation.getArgument(0);
            f.setId(1L);
            return f;
        });

        // Act
        FactoryResponse response = service.createFactory(factoryCode, factoryName);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getFactoryCode()).isEqualTo(factoryCode);
        assertThat(response.getFactoryName()).isEqualTo(factoryName);
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();

        // Verify factory was saved
        verify(factoryRepo).save(any(Factory.class));
    }

    // ─── Property 5: Factory uniqueness constraints ──────────────────────────────
    // For any factory code that already exists, a second creation request with the
    // same code SHALL be rejected. Similarly for duplicate names.
    // **Validates: Requirements 3.3, 3.5**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 5: Factory uniqueness constraints - duplicate code")
    void duplicateFactoryCodeIsRejected(
            @ForAll("validNonAdminFactoryCodes") String factoryCode,
            @ForAll("validFactoryNames") String factoryName
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        SuperAdminService service = createService(factoryRepo);

        // Simulate existing factory with same code
        Factory existingFactory = new Factory();
        existingFactory.setId(1L);
        existingFactory.setCode(factoryCode);
        existingFactory.setName("Existing Factory");
        existingFactory.setEnabled(true);
        existingFactory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(existingFactory));

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createFactory(factoryCode, factoryName),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("工厂代码已存在");

        // Verify no new factory was saved
        verify(factoryRepo, never()).save(any(Factory.class));
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 5: Factory uniqueness constraints - duplicate name")
    void duplicateFactoryNameIsRejected(
            @ForAll("validNonAdminFactoryCodes") String factoryCode,
            @ForAll("validFactoryNames") String factoryName
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        SuperAdminService service = createService(factoryRepo);

        // Code does not exist, but name does
        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.empty());

        Factory existingFactory = new Factory();
        existingFactory.setId(2L);
        existingFactory.setCode("OTHER");
        existingFactory.setName(factoryName);
        existingFactory.setEnabled(true);
        existingFactory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByName(factoryName)).thenReturn(Optional.of(existingFactory));

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createFactory(factoryCode, factoryName),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("工厂名称已被使用");

        // Verify no new factory was saved
        verify(factoryRepo, never()).save(any(Factory.class));
    }

    // ─── Property 6: Invalid factory code format rejection ───────────────────────
    // For any string that does not match [A-Z0-9]{1,16}, a factory creation request
    // with that code SHALL be rejected with a format error.
    // **Validates: Requirements 3.7**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 6: Invalid factory code format rejection")
    void invalidFactoryCodeFormatIsRejected(
            @ForAll("invalidFactoryCodes") String invalidCode,
            @ForAll("validFactoryNames") String factoryName
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        SuperAdminService service = createService(factoryRepo);

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createFactory(invalidCode, factoryName),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("工厂代码格式无效，需为1-16位大写字母和数字");

        // Verify no factory was saved
        verify(factoryRepo, never()).save(any(Factory.class));
    }

    // ─── Providers ────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> adminCodeVariants() {
        // Generate case variations of "ADMIN" that match [A-Z0-9]{1,16}
        // The service checks case-insensitively, but the format check requires uppercase
        // So we only generate "ADMIN" (the uppercase version that passes format check)
        return Arbitraries.just("ADMIN");
    }

    @Provide
    Arbitrary<String> validNonAdminFactoryCodes() {
        // Valid factory codes: [A-Z0-9]{1,16} that are NOT "ADMIN"
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(16)
                .filter(code -> !"ADMIN".equalsIgnoreCase(code));
    }

    @Provide
    Arbitrary<String> validFactoryNames() {
        // Factory names: 1-128 characters (non-empty)
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(50); // Keep reasonable for test performance
    }

    @Provide
    Arbitrary<String> invalidFactoryCodes() {
        // Strings that do NOT match [A-Z0-9]{1,16}
        return Arbitraries.oneOf(
                // Contains lowercase letters
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofMinLength(1)
                        .ofMaxLength(16),
                // Contains special characters
                Arbitraries.strings()
                        .withChars('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')
                        .ofMinLength(1)
                        .ofMaxLength(16),
                // Too long (exceeds 16 characters)
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('0', '9')
                        .ofMinLength(17)
                        .ofMaxLength(30),
                // Empty string
                Arbitraries.just(""),
                // Mixed valid and invalid characters
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('a', 'z')
                        .withChars('-', '_', '.')
                        .ofMinLength(1)
                        .ofMaxLength(16)
                        .filter(s -> !s.matches("[A-Z0-9]{1,16}"))
        );
    }
}
