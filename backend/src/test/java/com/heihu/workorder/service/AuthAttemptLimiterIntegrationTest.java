package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.repository.AuthAttemptRepository;
import com.heihu.workorder.model.AuthAttempt;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth_attempt_audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
@ActiveProfiles("test")
class AuthAttemptLimiterIntegrationTest {

    @Autowired
    private AuthAttemptLimiter limiter;

    @Autowired
    private AuthAttemptRepository attemptRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Test
    void expiredFailureWindowIsResetWithoutInsertingDuplicateKey() {
        AuthAttempt attempt = new AuthAttempt();
        attempt.setAttemptKey("expired-window");
        attempt.setFailedAttempts(4);
        attempt.setWindowStart(LocalDateTime.now().minusMinutes(20));
        attemptRepository.saveAndFlush(attempt);
        limiter.recordFailure("expired-window");
        AuthAttempt actual = attemptRepository.findByAttemptKey("expired-window").get();
        assertThat(actual.getId()).isEqualTo(attempt.getId());
        assertThat(actual.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void failureCounterSurvivesOuterLoginTransactionRollback() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
            limiter.recordFailure("rollback-login");
            throw new IllegalStateException("login rejected");
        }));
        assertThat(attemptRepository.findByAttemptKey("rollback-login").get().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void concurrentFirstFailuresAreCountedWithoutLostUpdates() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> attempts = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    limiter.recordFailure("parallel-login");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> attempt : attempts) attempt.get(20, TimeUnit.SECONDS);
            assertThat(attemptRepository.findByAttemptKey("parallel-login").get().getFailedAttempts()).isEqualTo(8);
            assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                    assertThrows(ApiException.class, () -> limiter.checkAllowed("parallel-login")).getStatus());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
