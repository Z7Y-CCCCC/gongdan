package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthAttemptLimiterTest {

    @Test
    void checkAllowed_shouldBlockAfterMaxFailures() {
        AuthAttemptLimiter limiter = new AuthAttemptLimiter();
        String key = AuthAttemptLimiter.key("web", "F001", "admin");

        for (int i = 0; i < AuthAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.checkAllowed(key);
            limiter.recordFailure(key);
        }

        ApiException exception = assertThrows(ApiException.class, () -> limiter.checkAllowed(key));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("登录失败次数过多，请稍后再试", exception.getMessage());
    }

    @Test
    void recordSuccess_shouldClearFailures() {
        AuthAttemptLimiter limiter = new AuthAttemptLimiter();
        String key = AuthAttemptLimiter.key("phone-password", "13800138000");

        for (int i = 0; i < AuthAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(key);
        }
        limiter.recordSuccess(key);

        assertDoesNotThrow(() -> limiter.checkAllowed(key));
    }
}
