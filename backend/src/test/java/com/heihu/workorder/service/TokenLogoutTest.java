package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TokenLogoutTest {

    @Test
    void memoryLogoutRevokesOnlyTheExactTokenAndPreservesAnotherUser() {
        TokenService tokens = new TokenService();
        String actor = tokens.issue(user(11L, 7L, Role.ADMIN));
        String other = tokens.issue(user(12L, 8L, Role.PRODUCTION));

        tokens.revoke("Bearer " + actor + "  ");

        assertUnauthorized(tokens, actor);
        assertThat(tokens.requireUser("Bearer " + other).getUserId()).isEqualTo(12L);
        assertThat(memorySessions(tokens)).containsOnlyKeys(other);
    }

    @Test
    void lateMemoryLogoutOfAnOldTokenCannotRevokeTheNewLogin() {
        TokenService tokens = new TokenService();
        FactoryUser actor = user(11L, 7L, Role.ADMIN);
        String old = tokens.issue(actor);
        String other = tokens.issue(user(12L, 8L, Role.PRODUCTION));
        String newer = tokens.issue(actor);

        tokens.revoke(old);
        tokens.revoke("Bearer " + old);

        assertUnauthorized(tokens, old);
        assertThat(tokens.requireUser("Bearer " + newer).getUserId()).isEqualTo(actor.getId());
        assertThat(tokens.requireUser("Bearer " + other).getUserId()).isEqualTo(12L);
        assertThat(memorySessions(tokens)).containsOnlyKeys(newer, other);
    }

    @Test
    void expiredMemoryTokenCanBeRemovedWithoutAuthenticatingIt() throws InterruptedException {
        TokenService tokens = new TokenService(Duration.ofMillis(1));
        String expired = tokens.issue(user(11L, 7L, Role.ADMIN));
        Thread.sleep(10L);

        assertThatCode(() -> {
            tokens.revoke("Bearer " + expired);
            tokens.revoke("Bearer " + expired);
        }).doesNotThrowAnyException();

        assertThat(memorySessions(tokens)).isEmpty();
        assertUnauthorized(tokens, expired);
    }

    @Test
    void anonymousEmptyAndUnknownMemoryRevocationsLeaveAnActiveSessionAlone() {
        TokenService tokens = new TokenService();
        String active = tokens.issue(user(11L, 7L, Role.PRODUCTION));

        for (String header : new String[]{null, "", "   ", "Bearer ", "Bearer fixture-unknown"}) {
            assertThatCode(() -> tokens.revoke(header)).doesNotThrowAnyException();
        }

        assertThat(tokens.requireUser("Bearer " + active).getUserId()).isEqualTo(11L);
        assertThat(memorySessions(tokens)).containsOnlyKeys(active);
    }

    @Test
    void persistentLogoutUsesOnlyTheExactTokenHashWithoutLoadingOrKickingUsers() throws Exception {
        UserSessionRepository sessions = mock(UserSessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        TokenService tokens = new TokenService(Duration.ofMinutes(10), sessions, users);
        String raw = "00000000000000000000000000000001";

        tokens.revoke("Bearer " + raw);

        verify(sessions).deleteByTokenHash(sha256(raw));
        verifyNoMoreInteractions(sessions);
        verifyNoInteractions(users);
    }

    @Test
    void repeatedExpiredAndUnknownPersistentRevocationsAreIdempotent() throws Exception {
        UserSessionRepository sessions = mock(UserSessionRepository.class);
        TokenService tokens = new TokenService(Duration.ofMinutes(10), sessions);
        String raw = "00000000000000000000000000000002";
        when(sessions.deleteByTokenHash(sha256(raw))).thenReturn(1, 0);

        assertThatCode(() -> {
            tokens.revoke(raw);
            tokens.revoke("Bearer " + raw);
            tokens.revoke("Bearer fixture-unknown");
            tokens.revoke(null);
            tokens.revoke("Bearer ");
        }).doesNotThrowAnyException();

        verify(sessions, times(2)).deleteByTokenHash(sha256(raw));
        verify(sessions).deleteByTokenHash(sha256("fixture-unknown"));
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void logoutAndExactTokenRevocationUseWritableTransactions() throws Exception {
        for (Class<?> service : new Class<?>[]{AuthService.class, TokenService.class}) {
            String method = service == AuthService.class ? "logout" : "revoke";
            Transactional transaction = service.getMethod(method, String.class).getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.readOnly()).isFalse();
        }
    }

    private void assertUnauthorized(TokenService tokens, String token) {
        assertThatThrownBy(() -> tokens.requireUser("Bearer " + token))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> memorySessions(TokenService tokens) {
        return (Map<String, ?>) ReflectionTestUtils.getField(tokens, "sessions");
    }

    private String sha256(String token) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : hash) hex.append(String.format("%02x", value));
        return hex.toString();
    }

    private FactoryUser user(Long id, Long factoryId, Role role) {
        Factory factory = new Factory();
        factory.setId(factoryId); factory.setCode("PRIVATE-" + factoryId); factory.setEnabled(true);
        FactoryUser user = new FactoryUser();
        user.setId(id); user.setFactory(factory); user.setName("Fixture " + id);
        user.setRole(role); user.setEnabled(true);
        return user;
    }
}
