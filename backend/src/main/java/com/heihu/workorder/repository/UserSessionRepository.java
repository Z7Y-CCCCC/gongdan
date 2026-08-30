package com.heihu.workorder.repository;

import com.heihu.workorder.model.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    List<UserSession> findByUserIdAndKickedFalse(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update UserSession s set s.kicked = true where s.userId = :userId and s.kicked = false")
    int kickActiveSessions(@Param("userId") Long userId);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserSession> findByFactoryIdAndKickedFalseAndExpiresAtAfterOrderByCreatedAtDesc(Long factoryId, Instant now);

    void deleteByExpiresAtBefore(Instant now);
}
