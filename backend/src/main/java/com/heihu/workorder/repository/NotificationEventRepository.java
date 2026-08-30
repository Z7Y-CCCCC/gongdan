package com.heihu.workorder.repository;

import com.heihu.workorder.model.NotificationEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    Optional<NotificationEvent> findByFactoryIdAndDedupeKey(Long factoryId, String dedupeKey);

    @Query("select e from NotificationEvent e where e.factory.id = :factoryId " +
            "and e.eventType in :eventTypes and e.resolvedAt is null")
    List<NotificationEvent> findOpenByFactoryIdAndEventTypeIn(
            @Param("factoryId") Long factoryId,
            @Param("eventTypes") Collection<String> eventTypes
    );
}
