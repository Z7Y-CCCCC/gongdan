package com.heihu.workorder.repository;

import com.heihu.workorder.model.ReportStatus;
import com.heihu.workorder.model.WorkOrderReport;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderReportRepository extends JpaRepository<WorkOrderReport, Long> {

    @Query("select r from WorkOrderReport r where r.factory.id = :factoryId " +
            "and (:status is null or r.status = :status) " +
            "and (:workOrderCode = '' or r.workOrderCode like concat('%', :workOrderCode, '%')) " +
            "and (:reporterName = '' or r.reporterName like concat('%', :reporterName, '%')) " +
            "and (:startAt is null or r.createdAt >= :startAt) " +
            "order by r.createdAt desc")
    Page<WorkOrderReport> search(
            @Param("factoryId") Long factoryId,
            @Param("status") ReportStatus status,
            @Param("workOrderCode") String workOrderCode,
            @Param("reporterName") String reporterName,
            @Param("startAt") LocalDateTime startAt,
            Pageable pageable
    );

    @Query("select r from WorkOrderReport r where r.factory.id = :factoryId " +
            "and r.reporter.id = :reporterId " +
            "and (:status is null or r.status = :status) " +
            "and (:startAt is null or r.createdAt >= :startAt) " +
            "order by r.createdAt desc")
    Page<WorkOrderReport> searchMobile(
            @Param("factoryId") Long factoryId,
            @Param("reporterId") Long reporterId,
            @Param("status") ReportStatus status,
            @Param("startAt") LocalDateTime startAt,
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
     * 完成数量（良品数）：SUM(goodQuantity) WHERE createdAt in [start, end) AND factoryId matches
     */
    @Query("SELECT COALESCE(SUM(r.goodQuantity), 0) FROM WorkOrderReport r " +
            "WHERE r.factory.id = :factoryId AND r.createdAt >= :start AND r.createdAt < :end")
    int sumGoodQuantityInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 不良品数：SUM(defectQuantity) WHERE createdAt in [start, end) AND factoryId matches
     */
    @Query("SELECT COALESCE(SUM(r.defectQuantity), 0) FROM WorkOrderReport r " +
            "WHERE r.factory.id = :factoryId AND r.createdAt >= :start AND r.createdAt < :end")
    int sumDefectQuantityInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
