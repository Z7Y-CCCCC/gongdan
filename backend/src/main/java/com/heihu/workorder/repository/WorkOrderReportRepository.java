package com.heihu.workorder.repository;

import com.heihu.workorder.model.ReportStatus;
import com.heihu.workorder.model.WorkOrderReport;
import java.time.LocalDateTime;
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
}
