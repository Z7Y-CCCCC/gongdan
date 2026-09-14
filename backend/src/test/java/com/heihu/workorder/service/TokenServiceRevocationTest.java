package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.UserSession;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenServiceRevocationTest {

    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TokenService service = new TokenService(Duration.ofMinutes(10), sessions, users);
    private Factory factory;
    private FactoryUser user;
    private UserSession session;

    @BeforeEach
    void setUp() {
        factory = new Factory();
        factory.setId(7L);
        factory.setCode("FACTORY");
        factory.setEnabled(true);
        user = new FactoryUser();
        user.setId(11L);
        user.setFactory(factory);
        user.setName("Current name");
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        session = new UserSession();
        session.setId(23L);
        session.setUserId(user.getId());
        session.setFactoryId(factory.getId());
        session.setFactoryCode(factory.getCode());
        session.setRole(Role.ADMIN);
        session.setDisplayName("Old name");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setLastActiveAt(LocalDateTime.now().minusMinutes(1));
        when(sessions.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        when(users.findByIdWithFactory(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void disabledUserCannotReuseExistingToken() {
        user.setEnabled(false);
        assertUnauthorized();
        verify(sessions).delete(session);
    }

    @Test
    void disabledFactoryCannotReuseExistingToken() {
        factory.setEnabled(false);
        assertUnauthorized();
    }

    @Test
    void deletedUserCannotReuseExistingToken() {
        when(users.findByIdWithFactory(user.getId())).thenReturn(Optional.empty());
        assertUnauthorized();
    }

    @Test
    void movedUserCannotReusePreviousFactoryToken() {
        factory.setId(99L);
        assertUnauthorized();
    }

    @Test
    void demotedAdministratorLosesAdminRightsImmediately() {
        user.setRole(Role.PRODUCTION);
        assertThatThrownBy(() -> service.requireAdmin("Bearer token"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void sessionReadsLiveProfileAndTouchesOnlyActivity() {
        AuthenticatedUser actual = service.requireUser("Bearer token");
        assertThat(actual.getName()).isEqualTo("Current name");
        verify(sessions).updateLastActiveAt(eq(session.getId()), any(LocalDateTime.class), any(Instant.class));
        // Saving the old entity would overwrite kicked=true from a concurrent login.
        verify(sessions, never()).save(any(UserSession.class));
    }

    @Test
    void metadataUpdateCannotRestoreKickedFlagOrTrustForgedForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "attacker controlled value");
        request.addHeader("User-Agent", "Mozilla/5.0 Windows Chrome/100");
        service.refreshSessionMetadata("Bearer token", request);
        verify(sessions).updateMetadata(anyString(), eq("127.0.0.1"), anyString(),
                eq("Windows"), eq("Chrome"), any(Instant.class));
        verify(sessions, never()).save(any(UserSession.class));
    }

    @Test
    void issuingTokenRechecksDisabledFactoryUnderUserLock() {
        factory.setEnabled(false);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.issue(user)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(sessions, never()).kickActiveSessions(anyLong());
        verify(sessions, never()).save(any(UserSession.class));
    }

    private void assertUnauthorized() {
        assertThatThrownBy(() -> service.requireUser("Bearer token"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
