package com.heihu.workorder.controller;

import com.heihu.workorder.dto.PerformanceResponse;
import com.heihu.workorder.dto.ProcessCardListResponse;
import com.heihu.workorder.dto.RecentReportListResponse;
import com.heihu.workorder.dto.WorkOrderProgressListResponse;
import com.heihu.workorder.enums.TimeRange;
import com.heihu.workorder.enums.WorkOrderStatusTab;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.HomeDashboardService;
import com.heihu.workorder.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/home-dashboard")
public class HomeDashboardController {

    private final HomeDashboardService homeDashboardService;
    private final TokenService tokenService;

    public HomeDashboardController(HomeDashboardService homeDashboardService, TokenService tokenService) {
        this.homeDashboardService = homeDashboardService;
        this.tokenService = tokenService;
    }

    /**
     * 工序任务卡片数据查询。
     *
     * @param authorization Bearer token
     * @param range         时间范围（today/yesterday/this_month），默认 today
     * @param includeNotStarted 是否包含未开始的任务，默认 false
     */
    @GetMapping("/process-cards")
    public ResponseEntity<ProcessCardListResponse> getProcessCards(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "range", required = false) String range,
            @RequestParam(value = "includeNotStarted", defaultValue = "false") boolean includeNotStarted
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        TimeRange timeRange = TimeRange.fromParam(range);
        ProcessCardListResponse response = homeDashboardService.listProcessCards(
                currentUser.getFactoryId(), timeRange, includeNotStarted);
        return ResponseEntity.ok(response);
    }

    /**
     * 工单进度报表查询。
     *
     * @param authorization Bearer token
     * @param status        状态标签（not_started/in_progress/due_within_3_days/overdue_unfinished/overdue_finished_this_month/finished_today/finished_this_month）
     */
    @GetMapping("/work-orders")
    public ResponseEntity<WorkOrderProgressListResponse> getWorkOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "status") String status
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        WorkOrderStatusTab tab = WorkOrderStatusTab.fromParam(status);
        LocalDateTime now = LocalDateTime.now();
        WorkOrderProgressListResponse response = homeDashboardService.listWorkOrders(
                currentUser.getFactoryId(), tab, now);
        return ResponseEntity.ok(response);
    }

    /**
     * 绩效指标查询。
     *
     * @param authorization Bearer token
     * @param range         时间范围（today/yesterday/this_month），默认 today
     */
    @GetMapping("/performance")
    public ResponseEntity<PerformanceResponse> getPerformance(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "range", required = false) String range
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        TimeRange timeRange = TimeRange.fromParam(range);
        LocalDateTime now = LocalDateTime.now();
        PerformanceResponse response = homeDashboardService.getPerformance(
                currentUser.getFactoryId(), timeRange, now);
        return ResponseEntity.ok(response);
    }

    /**
     * 生产实时播报查询。
     *
     * @param authorization Bearer token
     * @param since         可选 ISO 时间字符串，仅返回该时间之后的记录
     * @param limit         返回条数上限，默认 20，最大 50
     */
    @GetMapping("/reports/recent")
    public ResponseEntity<RecentReportListResponse> getRecentReports(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "since", required = false) String since,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);

        // 校验 limit 参数
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }

        // 解析 since 参数
        LocalDateTime sinceDateTime = null;
        if (since != null && !since.trim().isEmpty()) {
            try {
                sinceDateTime = LocalDateTime.parse(since);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid 'since' parameter: " + since);
            }
        }

        RecentReportListResponse response = homeDashboardService.listRecentReports(
                currentUser.getFactoryId(), sinceDateTime, limit);
        return ResponseEntity.ok(response);
    }
}
