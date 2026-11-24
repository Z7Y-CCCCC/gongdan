package com.heihu.workorder.repository;

import com.heihu.workorder.model.NotificationEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
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

    // A capped snapshot is not an authoritative list of unfinished work. Resolve
    // only events whose source is actually no longer pending/overdue in this factory.
    @Query("select e from NotificationEvent e where e.factory.id = :factoryId and e.resolvedAt is null " +
            "and ((e.eventType = 'REPORT_PENDING_APPROVAL' and not exists " +
            "(select r.id from WorkOrderReport r where r.factory.id = :factoryId and r.id = e.sourceId " +
            "and r.status = com.heihu.workorder.model.ReportStatus.PENDING)) " +
            "or (e.eventType = 'WORK_ORDER_OVERDUE' and not exists " +
            "(select w.id from WorkOrder w where w.factory.id = :factoryId and w.id = e.sourceId " +
            "and w.status not in ('COMPLETED', 'FINISHED', 'CLOSED', 'CANCELED', 'CANCELLED') " +
            "and w.planEnd < :now)))")
    List<NotificationEvent> findResolvableSnapshotEvents(@Param("factoryId") Long factoryId,
                                                        @Param("now") LocalDateTime now);
}
