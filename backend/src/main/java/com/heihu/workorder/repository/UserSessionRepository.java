package com.heihu.workorder.repository;

import com.heihu.workorder.model.UserSession;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from UserSession s where s.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    List<UserSession> findByUserIdAndKickedFalse(Long userId);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update UserSession s set s.kicked = true where s.userId = :userId and s.kicked = false")
    int kickActiveSessions(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("update UserSession s set s.lastActiveAt = :lastActiveAt "
            + "where s.id = :id and s.kicked = false and s.expiresAt > :now")
    int updateLastActiveAt(@Param("id") Long id, @Param("lastActiveAt") LocalDateTime lastActiveAt,
                          @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("update UserSession s set s.ipAddress = :ipAddress, s.loginLocation = :loginLocation, "
            + "s.operatingSystem = :operatingSystem, s.browser = :browser "
            + "where s.tokenHash = :tokenHash and s.kicked = false and s.expiresAt > :now")
    int updateMetadata(@Param("tokenHash") String tokenHash, @Param("ipAddress") String ipAddress,
                       @Param("loginLocation") String loginLocation, @Param("operatingSystem") String operatingSystem,
                       @Param("browser") String browser, @Param("now") Instant now);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserSession> findByFactoryIdAndKickedFalseAndExpiresAtAfterOrderByCreatedAtDesc(Long factoryId, Instant now);

    void deleteByExpiresAtBefore(Instant now);
}
