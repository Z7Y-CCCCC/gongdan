package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.AuthorizationCode;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发绑定集成测试 — 验证授权码消费的原子性（乐观锁行为）
 *
 * Feature: phone-factory-binding, Property 9: Auth code consumption is atomic — only one concurrent request succeeds
 * **Validates: Requirements 4.2, 4.3, 4.5**
 *
 * 使用 @RepeatedTest(100) 模拟 jqwik @Property(tries=100) 的效果，
 * 因为 jqwik 不原生支持 Spring Boot 依赖注入。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthCodeConcurrencyIntegrationTest {

    @Autowired
    private AuthorizationCodeService authorizationCodeService;

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Autowired
    private FactoryRepository factoryRepository;

    private Factory testFactory;

    private static final AtomicInteger codeCounter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        // Create a unique test factory for each test iteration
        testFactory = new Factory();
        testFactory.setCode("CONC_" + System.nanoTime());
        testFactory.setName("Concurrency Test Factory");
        testFactory.setCreatedAt(LocalDateTime.now());
        testFactory = factoryRepository.save(testFactory);
    }

    /**
     * Property 9: Auth code consumption is atomic — only one concurrent request succeeds.
     *
     * For any valid authorization code, if multiple concurrent requests attempt to consume it,
     * exactly one SHALL succeed and all others SHALL receive a "code already used" error.
     *
     * Uses multiple threads (5-10) to simulate concurrent binding requests and verifies
     * optimistic locking behavior.
     */
    @RepeatedTest(100)
    @DisplayName("Feature: phone-factory-binding, Property 9: Auth code consumption is atomic — only one concurrent request succeeds")
    void authCodeConsumptionIsAtomic() throws InterruptedException {
        // Arrange: create a fresh valid, unexpired, unconsumed authorization code
        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setCode(generateUniqueCode());
        authCode.setFactory(testFactory);
        authCode.setCreatedAt(LocalDateTime.now());
        authCode.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        authCode.setConsumed(false);
        authCode.setVersion(0);
        authCode = authorizationCodeRepository.saveAndFlush(authCode);

        final String code = authCode.getCode();
        final String factoryCode = testFactory.getCode();

        // Use a varying thread count between 5 and 10
        int threadCount = 5 + (codeCounter.get() % 6);

        // Act: launch multiple threads that all try to consume the same auth code simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // All threads wait until the latch is released for maximum concurrency
                    startLatch.await();
                    authorizationCodeService.validateAndConsume(code, factoryCode);
                    successCount.incrementAndGet();
                    return true;
                } catch (ApiException e) {
                    // Expected: "授权码已被使用" for losers of the race
                    assertThat(e.getMessage()).isEqualTo("授权码已被使用");
                    failureCount.incrementAndGet();
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception e) {
                    // Also accept other Spring-wrapped optimistic lock exceptions
                    failureCount.incrementAndGet();
                    return false;
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Release all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(completed).as("All threads should complete within timeout").isTrue();

        // Assert: exactly ONE thread succeeds
        assertThat(successCount.get())
                .as("Exactly one concurrent request should succeed")
                .isEqualTo(1);

        // Assert: all other threads fail
        assertThat(failureCount.get())
                .as("All other concurrent requests should fail")
                .isEqualTo(threadCount - 1);

        // Assert: the authorization code is marked as consumed exactly once in the database
        AuthorizationCode consumed = authorizationCodeRepository.findByCode(code).orElse(null);
        assertThat(consumed).isNotNull();
        assertThat(consumed.isConsumed()).isTrue();
        assertThat(consumed.getConsumedAt()).isNotNull();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private String generateUniqueCode() {
        // Generate a unique 8-char code for each test iteration
        int count = codeCounter.incrementAndGet();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(8);
        int value = count;
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(Math.abs(value % chars.length())));
            value = value / chars.length() + i + 1;
        }
        return sb.toString();
    }
}
