package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.repository.AuthAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class AuthAttemptLimiterIntegrationTest {

    @Autowired
    private AuthAttemptLimiter limiter;

    @Autowired
    private AuthAttemptRepository attemptRepository;

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAll();
    }

    @Test
    void limiterPersistsFailuresInDatabaseAndBlocksAcrossCalls() {
        String key = AuthAttemptLimiter.key("web", "F001", "admin");

        for (int i = 0; i < AuthAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.checkAllowed(key);
            limiter.recordFailure(key);
        }

        ApiException exception = assertThrows(ApiException.class, () -> limiter.checkAllowed(key));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertThat(attemptRepository.findByAttemptKey(key)).isPresent();
    }

    @Test
    void recordSuccessDeletesPersistedFailureState() {
        String key = AuthAttemptLimiter.key("phone-password", "13800138000");
        limiter.recordFailure(key);

        limiter.recordSuccess(key);

        assertThat(attemptRepository.findByAttemptKey(key)).isEmpty();
    }
}
