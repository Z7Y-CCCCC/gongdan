package com.heihu.workorder.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.heihu.workorder.exception.ApiException;
import org.junit.jupiter.api.Test;

class PluginCircuitBreakerTest {

    @Test
    void opensOnlyTheFailingPluginRoute() {
        PluginCircuitBreaker breaker = new PluginCircuitBreaker(2, 60_000, 60_000);

        breaker.recordFailure("factory-a:quality-management");
        assertDoesNotThrow(() -> breaker.beforeCall("factory-a:inventory-extension"));

        breaker.recordFailure("factory-a:quality-management");
        assertThrows(ApiException.class, () -> breaker.beforeCall("factory-a:quality-management"));
        assertDoesNotThrow(() -> breaker.beforeCall("factory-b:quality-management"));
    }
}
