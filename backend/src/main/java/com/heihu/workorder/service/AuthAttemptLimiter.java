package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.AuthAttempt;
import com.heihu.workorder.repository.AuthAttemptRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthAttemptLimiter {

    static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 15;
    private static final int LOCK_MINUTES = 15;

    private final ConcurrentHashMap<String, AttemptEntry> attempts = new ConcurrentHashMap<>();
    private final AuthAttemptRepository attemptRepository;
    private final TransactionTemplate failureTransaction;

    public AuthAttemptLimiter() {
        this(null, null);
    }

    public AuthAttemptLimiter(AuthAttemptRepository attemptRepository) {
        this(attemptRepository, null);
    }

    @Autowired
    public AuthAttemptLimiter(AuthAttemptRepository attemptRepository, PlatformTransactionManager transactionManager) {
        this.attemptRepository = attemptRepository;
        this.failureTransaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (this.failureTransaction != null) {
            this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAllowed(String key) {
        String normalizedKey = normalizeKey(key);
        if (attemptRepository != null) {
            checkAllowedInDatabase(normalizedKey);
            return;
        }
        AttemptEntry entry = attempts.get(normalizedKey);
        if (entry == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (entry.lockedUntil != null && entry.lockedUntil.isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
        }
        if (entry.windowStart.plusMinutes(WINDOW_MINUTES).isBefore(now)) {
            attempts.remove(normalizedKey, entry);
        }
    }

    public void recordFailure(String key) {
        String normalizedKey = normalizeKey(key);
        if (attemptRepository != null) {
            recordFailureWithRetry(normalizedKey);
            return;
        }
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String key) {
        if (attemptRepository != null) {
            attemptRepository.deleteByAttemptKey(normalizeKey(key));
            return;
        }
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

    private void checkAllowedInDatabase(String normalizedKey) {
        AuthAttempt entry = attemptRepository.findByAttemptKey(normalizedKey).orElse(null);
        if (entry == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (entry.getLockedUntil() != null && entry.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
        }
        // Expired windows are reset under the write lock. Deleting a stale snapshot
        // here can erase failures that another request has just recorded.
    }

    private void recordFailureWithRetry(String normalizedKey) {
        if (failureTransaction == null) {
            recordFailureInDatabase(normalizedKey);
            return;
        }
        for (int attempt = 0; ; attempt++) {
            try {
                failureTransaction.execute(status -> {
                    recordFailureInDatabase(normalizedKey);
                    return null;
                });
                return;
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
                // The first concurrent insert can lose a unique-key race (or a
                // MySQL gap-lock deadlock). Retry only after that transaction rolled back.
                if (attempt >= 2) throw ex;
            }
        }
    }

    private void recordFailureInDatabase(String normalizedKey) {
        LocalDateTime now = LocalDateTime.now();
        AuthAttempt entry = attemptRepository.findByAttemptKeyForUpdate(normalizedKey).orElse(null);
        if (entry == null) {
            AuthAttempt fresh = new AuthAttempt();
            fresh.setAttemptKey(normalizedKey);
            fresh.setFailedAttempts(1);
            fresh.setWindowStart(now);
            fresh.setLockedUntil(null);
            attemptRepository.saveAndFlush(fresh);
            return;
        }

        if (entry.getWindowStart().plusMinutes(WINDOW_MINUTES).isBefore(now)) {
            entry.setFailedAttempts(1);
            entry.setWindowStart(now);
            entry.setLockedUntil(null);
            attemptRepository.save(entry);
            return;
        }

        int failedAttempts = entry.getFailedAttempts() + 1;
        entry.setFailedAttempts(failedAttempts);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            entry.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
        }
        attemptRepository.save(entry);
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
