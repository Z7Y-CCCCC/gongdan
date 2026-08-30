package com.heihu.workorder.repository;

import com.heihu.workorder.model.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Page<WorkOrder> findByFactoryId(Long factoryId, Pageable pageable);

    Page<WorkOrder> findByFactoryIdAndCodeContainingAndProductNameContaining(Long factoryId, String code, String productName, Pageable pageable);

    Optional<WorkOrder> findByFactoryIdAndCode(Long factoryId, String code);

    boolean existsByFactoryIdAndProductId(Long factoryId, Long productId);

    boolean existsByFactoryIdAndCategoryId(Long factoryId, Long categoryId);

    // --- Home Dashboard: Work Order Progress Report queries ---

    @Query("SELECT w FROM WorkOrder w WHERE w.factory.id = :factoryId AND w.status = 'NOT_STARTED' ORDER BY w.createdAt DESC")
    List<WorkOrder> findNotStarted(@Param("factoryId") Long factoryId, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.factory.id = :factoryId AND w.status = 'IN_PROGRESS' ORDER BY w.createdAt DESC")
    List<WorkOrder> findInProgress(@Param("factoryId") Long factoryId, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.factory.id = :factoryId AND w.status <> 'COMPLETED' AND w.planEnd >= :now AND w.planEnd <= :endOfThirdDay ORDER BY w.planEnd ASC")
    List<WorkOrder> findDueWithin3Days(@Param("factoryId") Long factoryId, @Param("now") LocalDateTime now, @Param("endOfThirdDay") LocalDateTime endOfThirdDay, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.factory.id = :factoryId AND w.status <> 'COMPLETED' AND w.planEnd < :now ORDER BY w.planEnd ASC")
    List<WorkOrder> findOverdueUnfinished(@Param("factoryId") Long factoryId, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.factory.id = :factoryId AND w.status = 'COMPLETED' AND w.completedAt >= :monthStart AND w.completedAt < :monthEnd AND w.completedAt > w.planEnd ORDER BY w.completedAt DESC")
    List<WorkOrder> findOverdueFinishedThisMonth(@Param("factoryId") Long factoryId, @Param("monthStart") LocalDateTime monthStart, @Param("monthEnd") LocalDateTime monthEnd, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.factory.id = :factoryId AND w.status = 'COMPLETED' AND w.completedAt >= :todayStart AND w.completedAt < :todayEnd ORDER BY w.completedAt DESC")
    List<WorkOrder> findFinishedToday(@Param("factoryId") Long factoryId, @Param("todayStart") LocalDateTime todayStart, @Param("todayEnd") LocalDateTime todayEnd, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.factory.id = :factoryId AND w.status = 'COMPLETED' AND w.completedAt >= :monthStart AND w.completedAt < :monthEnd ORDER BY w.completedAt DESC")
    List<WorkOrder> findFinishedThisMonth(@Param("factoryId") Long factoryId, @Param("monthStart") LocalDateTime monthStart, @Param("monthEnd") LocalDateTime monthEnd, Pageable pageable);

    // --- Home Dashboard: Performance queries ---

    /**
     * 计划数：SUM(plannedQuantity) WHERE createdAt in [start, end) AND factoryId matches
     */
    @Query("SELECT COALESCE(SUM(w.plannedQuantity), 0) FROM WorkOrder w " +
            "WHERE w.factory.id = :factoryId AND w.createdAt >= :start AND w.createdAt < :end")
    int sumPlannedQuantityInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 工单数量：COUNT(WorkOrder) WHERE createdAt in [start, end) AND factoryId matches
     */
    @Query("SELECT COUNT(w) FROM WorkOrder w " +
            "WHERE w.factory.id = :factoryId AND w.createdAt >= :start AND w.createdAt < :end")
    int countInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 超期未完成工单：COUNT(WorkOrder) WHERE status != 'COMPLETED' AND planEnd < now AND factoryId matches
     * 不受 range 影响
     */
    @Query("SELECT COUNT(w) FROM WorkOrder w " +
            "WHERE w.factory.id = :factoryId AND w.status <> 'COMPLETED' AND w.planEnd < :now")
    int countOverdueUnfinishedByFactory(
            @Param("factoryId") Long factoryId,
            @Param("now") LocalDateTime now);

    /**
     * 查询已完成工单用于计算平均生产周期：status='COMPLETED' AND createdAt in [start, end) AND completedAt IS NOT NULL
     */
    @Query("SELECT w FROM WorkOrder w " +
            "WHERE w.factory.id = :factoryId AND w.status = 'COMPLETED' " +
            "AND w.createdAt >= :start AND w.createdAt < :end AND w.completedAt IS NOT NULL")
    List<WorkOrder> findCompletedInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 工序卡片：查询指定时间范围内的工单（用于展开 taskDataJson 按工序聚合）
     */
    @Query("SELECT w FROM WorkOrder w " +
            "WHERE w.factory.id = :factoryId AND w.createdAt >= :start AND w.createdAt < :end")
    List<WorkOrder> findByFactoryIdAndCreatedAtInRange(
            @Param("factoryId") Long factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
