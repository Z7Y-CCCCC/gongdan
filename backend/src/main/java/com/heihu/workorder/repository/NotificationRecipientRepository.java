package com.heihu.workorder.repository;

import com.heihu.workorder.model.NotificationRecipient;
import com.heihu.workorder.model.NotificationRecipientState;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    Optional<NotificationRecipient> findByIdAndUserIdAndEventFactoryId(
            Long id,
            Long userId,
            Long factoryId
    );

    @Query(value = "select r from NotificationRecipient r join fetch r.event e " +
             "where r.user.id = :userId and e.factory.id = :factoryId " +
             "and (:includeHistory = true or (e.resolvedAt is null and (e.expiresAt is null or e.expiresAt > :now))) " +
             "and (:includeArchived = true or r.archivedAt is null) " +
             "and (:archivedOnly = false or r.archivedAt is not null) " +
             "and (:unreadOnly = false or r.readAt is null) " +
            "and (:eventType = '' or e.eventType = :eventType) " +
            "and (:priority = '' or e.priority = :priority) " +
            "and (:keyword = '' or lower(e.title) like lower(concat('%', :keyword, '%')) " +
            "or lower(e.content) like lower(concat('%', :keyword, '%'))) " +
            "order by e.createdAt desc, r.id desc",
            countQuery = "select count(r.id) from NotificationRecipient r join r.event e " +
                     "where r.user.id = :userId and e.factory.id = :factoryId " +
                     "and (:includeHistory = true or (e.resolvedAt is null and (e.expiresAt is null or e.expiresAt > :now))) " +
                     "and (:includeArchived = true or r.archivedAt is null) " +
                     "and (:archivedOnly = false or r.archivedAt is not null) " +
                     "and (:unreadOnly = false or r.readAt is null) " +
                    "and (:eventType = '' or e.eventType = :eventType) " +
                    "and (:priority = '' or e.priority = :priority) " +
                    "and (:keyword = '' or lower(e.title) like lower(concat('%', :keyword, '%')) " +
                    "or lower(e.content) like lower(concat('%', :keyword, '%')))" )
    Page<NotificationRecipient> search(
            @Param("userId") Long userId,
            @Param("factoryId") Long factoryId,
            @Param("includeHistory") boolean includeHistory,
            @Param("includeArchived") boolean includeArchived,
            @Param("archivedOnly") boolean archivedOnly,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("eventType") String eventType,
            @Param("priority") String priority,
            @Param("keyword") String keyword,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("select count(r.id) from NotificationRecipient r join r.event e " +
            "where r.user.id = :userId and e.factory.id = :factoryId " +
            "and r.readAt is null and r.archivedAt is null " +
            "and e.resolvedAt is null and (e.expiresAt is null or e.expiresAt > :now)")
    long countUnread(
            @Param("userId") Long userId,
            @Param("factoryId") Long factoryId,
            @Param("now") LocalDateTime now
    );

    @Query("select r from NotificationRecipient r join fetch r.event e " +
            "where r.user.id = :userId and r.archivedAt is null and r.readAt is null " +
            "and e.factory.id = :factoryId and e.resolvedAt is null " +
            "and (e.expiresAt is null or e.expiresAt > :now)")
    List<NotificationRecipient> findActiveUnread(
            @Param("userId") Long userId,
            @Param("factoryId") Long factoryId,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("update NotificationRecipient r set r.readAt = coalesce(r.readAt, :now), " +
            "r.handledAt = :now, r.state = :state, r.updatedAt = :now " +
            "where r.event.id = :eventId and r.archivedAt is null")
    int markHandledByEventId(
            @Param("eventId") Long eventId,
            @Param("now") LocalDateTime now,
            @Param("state") NotificationRecipientState state
    );
}
