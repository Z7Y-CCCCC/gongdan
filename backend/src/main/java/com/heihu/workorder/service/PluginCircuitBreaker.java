package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Per-plugin, per-core-instance circuit breaker. A broken extension is paused for a
 * short window without taking the main application down. The database enable switch
 * remains the authoritative operator-controlled kill switch.
 */
@Component
public class PluginCircuitBreaker {

    private final int failureThreshold;
    private final long failureWindowMs;
    private final long openWindowMs;
    private final ConcurrentMap<String, State> states = new ConcurrentHashMap<>();

    public PluginCircuitBreaker(
            @Value("${app.plugin.proxy.failure-threshold:5}") int failureThreshold,
            @Value("${app.plugin.proxy.failure-window-ms:30000}") long failureWindowMs,
            @Value("${app.plugin.proxy.open-window-ms:30000}") long openWindowMs
    ) {
        if (failureThreshold < 1 || failureWindowMs < 1 || openWindowMs < 1) {
            throw new IllegalArgumentException("plugin proxy circuit breaker settings must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.failureWindowMs = failureWindowMs;
        this.openWindowMs = openWindowMs;
    }

    public void beforeCall(String key) {
        State state = states.get(key);
        if (state == null) return;
        synchronized (state) {
            long now = Instant.now().toEpochMilli();
            if (state.openUntil > now) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "插件暂时不可用，系统正在自动重试");
            }
            if (state.openUntil > 0 || now - state.windowStartedAt > failureWindowMs) {
                state.failures = 0;
                state.windowStartedAt = now;
                state.openUntil = 0;
            }
        }
    }

    public void recordSuccess(String key) {
        states.remove(key);
    }

    public void recordFailure(String key) {
        State state = states.computeIfAbsent(key, ignored -> new State());
        synchronized (state) {
            long now = Instant.now().toEpochMilli();
            if (now - state.windowStartedAt > failureWindowMs || state.openUntil > 0) {
                state.failures = 0;
                state.windowStartedAt = now;
                state.openUntil = 0;
            }
            state.failures++;
            if (state.failures >= failureThreshold) {
                state.openUntil = now + openWindowMs;
            }
        }
    }

    private static final class State {
        private long windowStartedAt = Instant.now().toEpochMilli();
        private int failures;
        private long openUntil;
    }
}
