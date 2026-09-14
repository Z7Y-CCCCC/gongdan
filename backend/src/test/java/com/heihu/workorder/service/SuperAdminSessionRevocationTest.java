package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SuperAdminSessionRevocationTest {

    private final FactoryRepository factories = mock(FactoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SuperAdminService service = new SuperAdminService(factories, users,
            mock(AuthorizationCodeRepository.class), encoder, sessions);

    @Test
    void passwordResetRevokesOldSessions() {
        FactoryUser user = new FactoryUser();
        user.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(encoder.encode(anyString())).thenReturn("encoded");
        service.resetUserPassword(1L, "new-password");
        verify(sessions).kickActiveSessions(1L);
    }

    @Test
    void disablingAdministratorRevokesOldSessions() {
        FactoryUser user = new FactoryUser();
        user.setId(1L);
        user.setEnabled(true);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        service.disableFactoryAdmin(1L);
        verify(sessions).kickActiveSessions(1L);
    }

    @Test
    void disablingFactoryRevokesSessionsEvenForAlreadyDisabledUsers() {
        Factory factory = new Factory();
        factory.setId(7L);
        factory.setEnabled(true);
        FactoryUser user = new FactoryUser();
        user.setId(1L);
        user.setFactory(factory);
        user.setEnabled(false);
        when(factories.findByCode("FACTORY")).thenReturn(Optional.of(factory));
        when(users.findByFactoryId(7L)).thenReturn(Collections.singletonList(user));
        service.disableFactory("FACTORY");
        verify(sessions).kickActiveSessions(1L);
    }
}
