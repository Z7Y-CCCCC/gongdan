package com.heihu.workorder.repository;

import com.heihu.workorder.model.ReportStatus;
import com.heihu.workorder.model.WorkOrderReport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderReportRepository extends JpaRepository<WorkOrderReport, Long> {

    String MOBILE_DAY_PREDICATE = "r.createdAt >= :dayStart and ((:inclusiveEnd = false and r.createdAt < :dayEnd) " +
            "or (:inclusiveEnd = true and r.createdAt <= :dayEnd))";

    @Query("select count(r.id) as reportTotal, " +
            "coalesce(sum(case when r.status = com.heihu.workorder.model.ReportStatus.PENDING then 1 else 0 end), 0) as pendingReportCount, " +
            "coalesce(sum(case when " + MOBILE_DAY_PREDICATE + " then 1 else 0 end), 0) as todayReportCount, " +
            "coalesce(sum(case when r.status = com.heihu.workorder.model.ReportStatus.APPROVED then r.defectQuantity else 0 end), 0) as totalDefect, " +
            "coalesce(sum(case when r.status = com.heihu.workorder.model.ReportStatus.APPROVED and " + MOBILE_DAY_PREDICATE + " then r.goodQuantity else 0 end), 0) as todayGood, " +
            "coalesce(sum(case when r.status = com.heihu.workorder.model.ReportStatus.APPROVED and " + MOBILE_DAY_PREDICATE + " then r.defectQuantity else 0 end), 0) as todayDefect, " +
            "coalesce(sum(case when r.status = com.heihu.workorder.model.ReportStatus.PENDING and " + MOBILE_DAY_PREDICATE + " then 1 else 0 end), 0) as todayPending " +
            "from WorkOrderReport r where r.factory.id = :factoryId and r.reporter.id = :userId")
    MobileTotals aggregateMobileTotals(@Param("factoryId") Long factoryId, @Param("userId") Long userId,
                                       @Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd,
                                       @Param("inclusiveEnd") boolean inclusiveEnd);

    @Query("select coalesce(nullif(trim(r.processName), ''), '未命名工序') as name, sum(r.goodQuantity) as value " +
            "from WorkOrderReport r where r.factory.id = :factoryId and r.reporter.id = :userId " +
            "and r.status = com.heihu.workorder.model.ReportStatus.APPROVED " +
            "group by coalesce(nullif(trim(r.processName), ''), '未命名工序') " +
            "order by sum(r.goodQuantity) desc, coalesce(nullif(trim(r.processName), ''), '未命名工序') asc")
    List<MobileRanking> rankMobileApprovedGood(@Param("factoryId") Long factoryId,
                                              @Param("userId") Long userId, Pageable pageable);

    interface MobileTotals {
        long getReportTotal();
        long getPendingReportCount();
        long getTodayReportCount();
        long getTotalDefect();
        long getTodayGood();
        long getTodayDefect();
        long getTodayPending();
    }

    interface MobileRanking {
        String getName();
        long getValue();
    }

    boolean existsByFactoryIdAndWorkOrderId(Long factoryId, Long workOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WorkOrderReport r where r.id = :id and r.factory.id = :factoryId")
    Optional<WorkOrderReport> findByIdAndFactoryIdForUpdate(@Param("id") Long id, @Param("factoryId") Long factoryId);

    default Page<WorkOrderReport> search(Long factoryId, ReportStatus status, String workOrderCode,
                                        String reporterName, LocalDateTime startAt, Pageable pageable) {
        return search(factoryId, status, workOrderCode, reporterName, startAt, null, pageable);
    }

    @Query("select r from WorkOrderReport r where r.factory.id = :factoryId " +
            "and (:status is null or r.status = :status) " +
            "and (:workOrderCode = '' or r.workOrderCode like concat('%', :workOrderCode, '%')) " +
            "and (:reporterName = '' or r.reporterName like concat('%', :reporterName, '%')) " +
            "and (:startAt is null or r.createdAt >= :startAt) " +
            "and (:endAt is null or r.createdAt < :endAt) " +
            "order by r.createdAt desc, r.id desc")
    Page<WorkOrderReport> search(
            @Param("factoryId") Long factoryId,
            @Param("status") ReportStatus status,
            @Param("workOrderCode") String workOrderCode,
            @Param("reporterName") String reporterName,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            Pageable pageable
    );

    @Query("select r from WorkOrderReport r where r.factory.id = :factoryId " +
            "and (r.reporter.id = :userId or (:includeSubmitted = true and r.submitterId = :userId)) " +
            "and (:workOrderId is null or r.workOrder.id = :workOrderId) " +
            "and (:status is null or r.status = :status) " +
            "and (:startAt is null or r.createdAt >= :startAt) " +
            "and (:endAt is null or r.createdAt < :endAt) " +
            "order by r.createdAt desc, r.id desc")
    Page<WorkOrderReport> searchMobile(
            @Param("factoryId") Long factoryId,
            @Param("userId") Long userId,
            @Param("includeSubmitted") boolean includeSubmitted,
            @Param("workOrderId") Long workOrderId,
            @Param("status") ReportStatus status,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            Pageable pageable
    );

    @Query("select r from WorkOrderReport r where r.factory.id = :factoryId " +
            "and r.createdAt > :since " +
            "order by r.createdAt desc")
    List<WorkOrderReport> findRecentSince(
            @Param("factoryId") Long factoryId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    @Query("select r from WorkOrderReport r where r.factory.id = :factoryId " +
            "order by r.createdAt desc")
    List<WorkOrderReport> findRecent(
            @Param("factoryId") Long factoryId,
            Pageable pageable
    );

    // --- Home Dashboard: Performance queries ---

    /**
     * 完成数量（良品数）：只统计已审批报工。
     */
    @Query("SELECT COALESCE(SUM(r.goodQuantity), 0) FROM WorkOrderReport r " +
            "WHERE r.factory.id = :factoryId " +
            "AND r.status = com.heihu.workorder.model.ReportStatus.APPROVED " +
            "AND r.createdAt >= :start AND r.createdAt < :end")
    long sumGoodQuantityInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 不良品数：只统计已审批报工。
     */
    @Query("SELECT COALESCE(SUM(r.defectQuantity), 0) FROM WorkOrderReport r " +
            "WHERE r.factory.id = :factoryId " +
            "AND r.status = com.heihu.workorder.model.ReportStatus.APPROVED " +
            "AND r.createdAt >= :start AND r.createdAt < :end")
    long sumDefectQuantityInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
