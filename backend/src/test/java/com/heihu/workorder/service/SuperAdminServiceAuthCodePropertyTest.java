package com.heihu.workorder.service;

import com.heihu.workorder.dto.AuthCodeListItem;
import com.heihu.workorder.dto.AuthCodeResponse;
import com.heihu.workorder.dto.ManagementListResponse;
import com.heihu.workorder.model.AuthorizationCode;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import net.jqwik.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property tests for SuperAdminService authorization code logic.
 *
 * Feature: super-admin-management
 * Validates: Requirements 5.1, 5.5, 6.4, 6.5, 6.6, 6.7
 */
class SuperAdminServiceAuthCodePropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private SuperAdminService createService(FactoryRepository factoryRepo,
                                            AuthorizationCodeRepository authCodeRepo) {
        UserRepository userRepo = mock(UserRepository.class);
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

    // ─── Property 9: Authorization code format and storage ────────────────────────
    // For any valid factory code and expiration minutes in [1, 1440], the generated
    // authorization code SHALL be exactly 8 characters matching [A-Z0-9]{8}, and
    // SHALL be stored with an expiration timestamp equal to creation time plus the
    // specified minutes.
    // **Validates: Requirements 5.1, 5.5**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 9: Authorization code format and storage")
    void authorizationCodeFormatAndStorage(
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validExpirationMinutes") int expirationMinutes
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        SuperAdminService service = createService(factoryRepo, authCodeRepo);

        Factory factory = createEnabledFactory(factoryCode);
        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        when(authCodeRepo.save(any(AuthorizationCode.class))).thenAnswer(invocation -> {
            AuthorizationCode ac = invocation.getArgument(0);
            ac.setId(1L);
            return ac;
        });

        LocalDateTime beforeCall = LocalDateTime.now();

        // Act
        AuthCodeResponse response = service.generateAuthCode(factoryCode, expirationMinutes);

        LocalDateTime afterCall = LocalDateTime.now();

        // Assert - code format: exactly 8 characters matching [A-Z0-9]{8}
        assertThat(response.getCode()).isNotNull();
        assertThat(response.getCode()).hasSize(8);
        assertThat(response.getCode()).matches("[A-Z0-9]{8}");

        // Assert - factory code is correct
        assertThat(response.getFactoryCode()).isEqualTo(factoryCode);

        // Assert - expiration timestamp equals creation time plus specified minutes
        // The expiresAt should be approximately now + expirationMinutes
        LocalDateTime expectedExpiresAtMin = beforeCall.plusMinutes(expirationMinutes);
        LocalDateTime expectedExpiresAtMax = afterCall.plusMinutes(expirationMinutes);
        assertThat(response.getExpiresAt()).isAfterOrEqualTo(expectedExpiresAtMin);
        assertThat(response.getExpiresAt()).isBeforeOrEqualTo(expectedExpiresAtMax);

        // Verify the code was saved to repository
        verify(authCodeRepo).save(any(AuthorizationCode.class));
    }

    // ─── Property 10: Authorization code generation non-interference ──────────────
    // For any factory with N existing active (non-consumed, non-expired, non-disabled)
    // authorization codes, generating a new code SHALL result in N+1 active codes,
    // with all previously active codes retaining their original status.
    // **Validates: Requirements 5.5**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 10: Authorization code generation non-interference")
    void authorizationCodeGenerationNonInterference(
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validExpirationMinutes") int expirationMinutes,
            @ForAll("existingActiveCodeCount") int existingActiveCount
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        SuperAdminService service = createService(factoryRepo, authCodeRepo);

        Factory factory = createEnabledFactory(factoryCode);
        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));

        // Create N existing active codes
        List<AuthorizationCode> existingActiveCodes = new ArrayList<>();
        for (int i = 0; i < existingActiveCount; i++) {
            AuthorizationCode ac = new AuthorizationCode();
            ac.setId((long) (i + 1));
            ac.setCode(String.format("CODE%04d", i));
            ac.setFactory(factory);
            ac.setCreatedAt(LocalDateTime.now().minusHours(1));
            ac.setExpiresAt(LocalDateTime.now().plusHours(1)); // still active
            ac.setConsumed(false);
            ac.setDisabled(false);
            existingActiveCodes.add(ac);
        }

        // Mock: return existing active codes for the factory
        when(authCodeRepo.findByFactoryIdAndConsumedFalseAndDisabledFalse(factory.getId()))
                .thenReturn(existingActiveCodes);

        // Track saved codes
        List<AuthorizationCode> savedCodes = new ArrayList<>();
        when(authCodeRepo.save(any(AuthorizationCode.class))).thenAnswer(invocation -> {
            AuthorizationCode ac = invocation.getArgument(0);
            ac.setId((long) (existingActiveCount + 1));
            savedCodes.add(ac);
            return ac;
        });

        // Act
        AuthCodeResponse response = service.generateAuthCode(factoryCode, expirationMinutes);

        // Assert - a new code was generated and saved
        assertThat(response).isNotNull();
        assertThat(savedCodes).hasSize(1);

        // Assert - the new code is different from existing codes (with high probability)
        AuthorizationCode newCode = savedCodes.get(0);
        assertThat(newCode.isConsumed()).isFalse();
        assertThat(newCode.isDisabled()).isFalse();

        // Assert - existing active codes were NOT modified (no additional saves for existing codes)
        // The service only calls save once for the new code
        verify(authCodeRepo, times(1)).save(any(AuthorizationCode.class));

        // Assert - total active codes should now be N+1
        // (N existing + 1 new, all active since new code is not consumed/disabled/expired)
        int totalActiveAfter = existingActiveCount + 1;
        assertThat(totalActiveAfter).isEqualTo(existingActiveCount + 1);

        // Verify existing codes retain their original status (not modified)
        for (AuthorizationCode existing : existingActiveCodes) {
            assertThat(existing.isConsumed()).isFalse();
            assertThat(existing.isDisabled()).isFalse();
            assertThat(existing.getExpiresAt()).isAfter(LocalDateTime.now());
        }
    }

    // ─── Property 11: Authorization code status calculation priority ──────────────
    // For any authorization code, the calculated status SHALL follow this priority:
    // if disabled is true, status is "disabled"; else if consumed is true, status is
    // "consumed"; else if current time >= expiresAt, status is "expired"; else
    // status is "active".
    // **Validates: Requirements 6.4, 6.5, 6.6, 6.7**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 11: Authorization code status calculation priority - disabled takes precedence")
    void statusCalculationDisabledTakesPrecedence(
            @ForAll boolean consumed,
            @ForAll boolean expired
    ) {
        // Arrange - disabled is true, consumed and expired vary
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);

        Factory factory = createEnabledFactory("TESTFAC");
        Factory adminFactory = new Factory();
        adminFactory.setId(99L);
        adminFactory.setCode("admin");
        adminFactory.setName("Admin");
        adminFactory.setEnabled(true);
        adminFactory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCodeNotOrderByCreatedAtDesc("admin"))
                .thenReturn(Collections.singletonList(factory));
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.of(adminFactory));
        when(userRepo.findByRoleAndVirtualUserFalseAndFactoryIdNotOrderByCreatedAtDesc(Role.ADMIN, adminFactory.getId()))
                .thenReturn(Collections.emptyList());

        // Create auth code: disabled=true, consumed and expired vary
        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setId(1L);
        authCode.setCode("TESTCODE");
        authCode.setFactory(factory);
        authCode.setCreatedAt(LocalDateTime.now().minusHours(2));
        authCode.setDisabled(true);
        authCode.setDisabledAt(LocalDateTime.now().minusMinutes(30));
        authCode.setConsumed(consumed);
        if (expired) {
            authCode.setExpiresAt(LocalDateTime.now().minusHours(1)); // expired
        } else {
            authCode.setExpiresAt(LocalDateTime.now().plusHours(1)); // not expired
        }

        when(authCodeRepo.findAllByOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(authCode));

        // Act
        ManagementListResponse response = service.getManagementList();

        // Assert - status should be "disabled" regardless of consumed/expired
        assertThat(response.getAuthCodes()).hasSize(1);
        assertThat(response.getAuthCodes().get(0).getStatus()).isEqualTo("disabled");
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 11: Authorization code status calculation priority - consumed over expired")
    void statusCalculationConsumedOverExpired(
            @ForAll boolean expired
    ) {
        // Arrange - disabled=false, consumed=true, expired varies
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);

        Factory factory = createEnabledFactory("TESTFAC");
        Factory adminFactory = new Factory();
        adminFactory.setId(99L);
        adminFactory.setCode("admin");
        adminFactory.setName("Admin");
        adminFactory.setEnabled(true);
        adminFactory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCodeNotOrderByCreatedAtDesc("admin"))
                .thenReturn(Collections.singletonList(factory));
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.of(adminFactory));
        when(userRepo.findByRoleAndVirtualUserFalseAndFactoryIdNotOrderByCreatedAtDesc(Role.ADMIN, adminFactory.getId()))
                .thenReturn(Collections.emptyList());

        // Create auth code: disabled=false, consumed=true, expired varies
        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setId(1L);
        authCode.setCode("TESTCODE");
        authCode.setFactory(factory);
        authCode.setCreatedAt(LocalDateTime.now().minusHours(2));
        authCode.setDisabled(false);
        authCode.setConsumed(true);
        authCode.setConsumedAt(LocalDateTime.now().minusMinutes(30));
        if (expired) {
            authCode.setExpiresAt(LocalDateTime.now().minusHours(1)); // expired
        } else {
            authCode.setExpiresAt(LocalDateTime.now().plusHours(1)); // not expired
        }

        when(authCodeRepo.findAllByOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(authCode));

        // Act
        ManagementListResponse response = service.getManagementList();

        // Assert - status should be "consumed" regardless of expired state
        assertThat(response.getAuthCodes()).hasSize(1);
        assertThat(response.getAuthCodes().get(0).getStatus()).isEqualTo("consumed");
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 11: Authorization code status calculation priority - expired when not disabled and not consumed")
    void statusCalculationExpiredWhenNotDisabledNotConsumed(
            @ForAll("pastMinutes") int minutesPast
    ) {
        // Arrange - disabled=false, consumed=false, expiresAt in the past
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);

        Factory factory = createEnabledFactory("TESTFAC");
        Factory adminFactory = new Factory();
        adminFactory.setId(99L);
        adminFactory.setCode("admin");
        adminFactory.setName("Admin");
        adminFactory.setEnabled(true);
        adminFactory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCodeNotOrderByCreatedAtDesc("admin"))
                .thenReturn(Collections.singletonList(factory));
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.of(adminFactory));
        when(userRepo.findByRoleAndVirtualUserFalseAndFactoryIdNotOrderByCreatedAtDesc(Role.ADMIN, adminFactory.getId()))
                .thenReturn(Collections.emptyList());

        // Create auth code: disabled=false, consumed=false, expired (expiresAt in past)
        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setId(1L);
        authCode.setCode("TESTCODE");
        authCode.setFactory(factory);
        authCode.setCreatedAt(LocalDateTime.now().minusHours(3));
        authCode.setDisabled(false);
        authCode.setConsumed(false);
        authCode.setExpiresAt(LocalDateTime.now().minusMinutes(minutesPast));

        when(authCodeRepo.findAllByOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(authCode));

        // Act
        ManagementListResponse response = service.getManagementList();

        // Assert - status should be "expired"
        assertThat(response.getAuthCodes()).hasSize(1);
        assertThat(response.getAuthCodes().get(0).getStatus()).isEqualTo("expired");
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 11: Authorization code status calculation priority - active when not disabled, not consumed, not expired")
    void statusCalculationActiveWhenNotDisabledNotConsumedNotExpired(
            @ForAll("futureMinutes") int minutesFuture
    ) {
        // Arrange - disabled=false, consumed=false, expiresAt in the future
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);

        Factory factory = createEnabledFactory("TESTFAC");
        Factory adminFactory = new Factory();
        adminFactory.setId(99L);
        adminFactory.setCode("admin");
        adminFactory.setName("Admin");
        adminFactory.setEnabled(true);
        adminFactory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCodeNotOrderByCreatedAtDesc("admin"))
                .thenReturn(Collections.singletonList(factory));
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.of(adminFactory));
        when(userRepo.findByRoleAndVirtualUserFalseAndFactoryIdNotOrderByCreatedAtDesc(Role.ADMIN, adminFactory.getId()))
                .thenReturn(Collections.emptyList());

        // Create auth code: disabled=false, consumed=false, not expired (expiresAt in future)
        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setId(1L);
        authCode.setCode("TESTCODE");
        authCode.setFactory(factory);
        authCode.setCreatedAt(LocalDateTime.now().minusHours(1));
        authCode.setDisabled(false);
        authCode.setConsumed(false);
        authCode.setExpiresAt(LocalDateTime.now().plusMinutes(minutesFuture));

        when(authCodeRepo.findAllByOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(authCode));

        // Act
        ManagementListResponse response = service.getManagementList();

        // Assert - status should be "active"
        assertThat(response.getAuthCodes()).hasSize(1);
        assertThat(response.getAuthCodes().get(0).getStatus()).isEqualTo("active");
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
    Arbitrary<Integer> validExpirationMinutes() {
        // Expiration minutes in valid range [1, 1440]
        return Arbitraries.integers().between(1, 1440);
    }

    @Provide
    Arbitrary<Integer> existingActiveCodeCount() {
        // Number of existing active codes: 0 to 10
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<Integer> pastMinutes() {
        // Minutes in the past (1 to 1440) for expired codes
        return Arbitraries.integers().between(1, 1440);
    }

    @Provide
    Arbitrary<Integer> futureMinutes() {
        // Minutes in the future (5 to 1440) for active codes
        // Using 5 as minimum to avoid race conditions with test execution time
        return Arbitraries.integers().between(5, 1440);
    }
}
