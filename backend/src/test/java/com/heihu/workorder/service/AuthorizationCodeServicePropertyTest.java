package com.heihu.workorder.service;

import com.heihu.workorder.dto.AuthCodeResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import net.jqwik.api.*;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AuthorizationCodeService 授权码生成 — 属性测试（jqwik）
 *
 * Feature: phone-factory-binding
 * Validates: Requirements 3.1, 3.3, 3.4, 3.6
 */
class AuthorizationCodeServicePropertyTest {

    // ─── Property 10 ──────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 10: Generated auth codes are exactly 8 alphanumeric characters with correct expiration
    // **Validates: Requirements 3.1, 3.3, 3.4**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 10: Generated auth codes are exactly 8 alphanumeric characters with correct expiration")
    void generatedAuthCodesAreExactly8AlphanumericWithCorrectExpiration(
            @ForAll("validFactoryCodes") String factoryCode,
            @ForAll("validExpirationMinutes") int expirationMinutes
    ) {
        // Arrange: mock repositories, factory exists
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        FactoryRepository factoryRepo = mock(FactoryRepository.class);

        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        when(authCodeRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuthorizationCodeService service = new AuthorizationCodeService(authCodeRepo, factoryRepo);

        // Act
        LocalDateTime beforeCall = LocalDateTime.now();
        AuthCodeResponse response = service.generateCode(factoryCode, expirationMinutes);
        LocalDateTime afterCall = LocalDateTime.now();

        // Assert: code is exactly 8 characters
        assertThat(response.getCode()).hasSize(8);

        // Assert: code contains only alphanumeric characters [a-zA-Z0-9]
        assertThat(response.getCode()).matches("^[a-zA-Z0-9]{8}$");

        // Assert: expiresAt equals createdAt + expirationMinutes (within tolerance)
        // The expiresAt should be approximately now + expirationMinutes
        LocalDateTime expectedEarliestExpiry = beforeCall.plusMinutes(expirationMinutes);
        LocalDateTime expectedLatestExpiry = afterCall.plusMinutes(expirationMinutes);

        assertThat(response.getExpiresAt()).isAfterOrEqualTo(expectedEarliestExpiry.truncatedTo(ChronoUnit.SECONDS));
        assertThat(response.getExpiresAt()).isBeforeOrEqualTo(expectedLatestExpiry.plusSeconds(1));
    }

    // ─── Property 11 ──────────────────────────────────────────────────────────────
    // Feature: phone-factory-binding, Property 11: Out-of-range expiration minutes causes rejection
    // **Validates: Requirements 3.6**
    @Property(tries = 100)
    @Label("Feature: phone-factory-binding, Property 11: Out-of-range expiration minutes causes rejection")
    void outOfRangeExpirationMinutesCausesRejection(
            @ForAll("invalidExpirationMinutes") int expirationMinutes
    ) {
        // Arrange: mock repositories (factory validation doesn't matter since expiration is checked first)
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        FactoryRepository factoryRepo = mock(FactoryRepository.class);

        AuthorizationCodeService service = new AuthorizationCodeService(authCodeRepo, factoryRepo);

        // Act & Assert: should throw ApiException with range error message
        ApiException exception = catchThrowableOfType(
                () -> service.generateCode("ANY_FACTORY", expirationMinutes),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("有效期必须在1-1440分钟之间");

        // Verify that factory repository was NOT consulted (expiration checked first)
        verifyNoInteractions(factoryRepo);
        verifyNoInteractions(authCodeRepo);
    }

    // ─── Providers ────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> validFactoryCodes() {
        // Factory code: 4-16 alphanumeric characters
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(4)
                .ofMaxLength(16);
    }

    @Provide
    Arbitrary<Integer> validExpirationMinutes() {
        // Valid range: [1, 1440]
        return Arbitraries.integers().between(1, 1440);
    }

    @Provide
    Arbitrary<Integer> invalidExpirationMinutes() {
        // Out of range: < 1 or > 1440
        return Arbitraries.oneOf(
                Arbitraries.integers().between(Integer.MIN_VALUE, 0),
                Arbitraries.integers().between(1441, Integer.MAX_VALUE)
        );
    }
}
