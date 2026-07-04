package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthAttemptLimiter {

    static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 15;
    private static final int LOCK_MINUTES = 15;

    private final ConcurrentHashMap<String, AttemptEntry> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String key) {
        String normalizedKey = normalizeKey(key);
        AttemptEntry entry = attempts.get(normalizedKey);
        if (entry == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (entry.lockedUntil != null && entry.lockedUntil.isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
        }
        if (entry.windowStart.plusMinutes(WINDOW_MINUTES).isBefore(now)) {
            attempts.remove(normalizedKey);
        }
    }

    public void recordFailure(String key) {
        String normalizedKey = normalizeKey(key);
        LocalDateTime now = LocalDateTime.now();
        attempts.compute(normalizedKey, (ignored, existing) -> {
            if (existing == null || existing.windowStart.plusMinutes(WINDOW_MINUTES).isBefore(now)) {
                return new AttemptEntry(1, now, null);
            }
            int failedAttempts = existing.failedAttempts + 1;
            LocalDateTime lockedUntil = failedAttempts >= MAX_FAILED_ATTEMPTS
                    ? now.plusMinutes(LOCK_MINUTES)
                    : existing.lockedUntil;
            return new AttemptEntry(failedAttempts, existing.windowStart, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(normalizeKey(key));
    }

    public static String key(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(':');
            }
            builder.append(part == null ? "" : part.trim().toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static class AttemptEntry {
        private final int failedAttempts;
        private final LocalDateTime windowStart;
        private final LocalDateTime lockedUntil;

        private AttemptEntry(int failedAttempts, LocalDateTime windowStart, LocalDateTime lockedUntil) {
            this.failedAttempts = failedAttempts;
            this.windowStart = windowStart;
            this.lockedUntil = lockedUntil;
        }
    }
}
