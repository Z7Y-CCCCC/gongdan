package com.heihu.workorder.repository;

import com.heihu.workorder.model.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    List<UserSession> findByUserIdAndKickedFalse(Long userId);

    List<UserSession> findByFactoryIdAndKickedFalseAndExpiresAtAfterOrderByCreatedAtDesc(Long factoryId, Instant now);

    void deleteByExpiresAtBefore(Instant now);
}
