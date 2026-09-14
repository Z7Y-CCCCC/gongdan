package com.heihu.workorder.service;

import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.UserSession;
import com.heihu.workorder.repository.UserSessionRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.show-sql=false"})
@ActiveProfiles("test")
class UserSessionPersistenceTest {

    @Autowired private UserSessionRepository sessions;
    @Autowired private EntityManager entityManager;

    @Test
    void staleActivityAndMetadataUpdatesCannotUndoAConcurrentKick() {
        UserSession session = persistSession();
        Long id = session.getId();
        entityManager.clear();
        sessions.kickActiveSessions(11L);
        entityManager.clear();

        assertThat(sessions.updateLastActiveAt(id, LocalDateTime.now(), Instant.now())).isZero();
        assertThat(sessions.updateMetadata("test-token-hash", "new-ip", "new-location", "new-os", "new-browser",
                Instant.now())).isZero();
        entityManager.clear();
        UserSession actual = sessions.findById(id).get();
        assertThat(actual.isKicked()).isTrue();
        assertThat(actual.getIpAddress()).isEqualTo("old-ip");
    }

    @Test
    void currentSessionActivityAndMetadataCanBeUpdatedWithoutReplacingAuthFields() {
        UserSession session = persistSession();
        Long id = session.getId();
        entityManager.clear();
        assertThat(sessions.updateLastActiveAt(id, LocalDateTime.now(), Instant.now())).isEqualTo(1);
        assertThat(sessions.updateMetadata("test-token-hash", "new-ip", "new-location", "new-os", "new-browser",
                Instant.now())).isEqualTo(1);
        entityManager.clear();
        UserSession actual = sessions.findById(id).get();
        assertThat(actual.isKicked()).isFalse();
        assertThat(actual.getFactoryId()).isEqualTo(7L);
        assertThat(actual.getRole()).isEqualTo(Role.ADMIN);
        assertThat(actual.getIpAddress()).isEqualTo("new-ip");
    }

    private UserSession persistSession() {
        UserSession session = new UserSession();
        session.setTokenHash("test-token-hash");
        session.setUserId(11L);
        session.setFactoryId(7L);
        session.setFactoryCode("FACTORY");
        session.setRole(Role.ADMIN);
        session.setDisplayName("Admin");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        session.setLastActiveAt(LocalDateTime.now().minusMinutes(1));
        session.setIpAddress("old-ip");
        return sessions.saveAndFlush(session);
    }
}
