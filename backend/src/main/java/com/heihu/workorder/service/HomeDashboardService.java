package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.PerformanceResponse;
import com.heihu.workorder.dto.ProcessCardItemResponse;
import com.heihu.workorder.dto.ProcessCardListResponse;
import com.heihu.workorder.dto.RecentReportItemResponse;
import com.heihu.workorder.dto.RecentReportListResponse;
import com.heihu.workorder.dto.WorkOrderProgressItemResponse;
import com.heihu.workorder.dto.WorkOrderProgressListResponse;
import com.heihu.workorder.dto.WorkOrderRequest;
import com.heihu.workorder.enums.TimeRange;
import com.heihu.workorder.enums.WorkOrderStatusTab;
import com.heihu.workorder.model.Process;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.model.WorkOrderReport;
import com.heihu.workorder.repository.ProcessRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HomeDashboardService {

    private static final int MAX_WORK_ORDER_ROWS = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderReportRepository workOrderReportRepository;
    private final ProcessRepository processRepository;

    public HomeDashboardService(WorkOrderRepository workOrderRepository,
                                WorkOrderReportRepository workOrderReportRepository,
                                ProcessRepository processRepository) {
        this.workOrderRepository = workOrderRepository;
        this.workOrderReportRepository = workOrderReportRepository;
        this.processRepository = processRepository;
    }

    // ==================== 工序卡片数据查询 ====================

    /**
     * 工序卡片数据查询。
     * <p>
     * 按 range 过滤工单（createdAt 落入区间），展开 task_data_json 按工序名聚合统计。
     * 当 includeNotStarted=false 时排除 completedQuantity==0 且 lastReportTime==null 的任务。
     * 工序列表来自 processes 表中按 factoryId + name 匹配。
     */
    public ProcessCardListResponse listProcessCards(Long factoryId, TimeRange range, boolean includeNotStarted) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime[] interval = range.toInterval(now);
        LocalDateTime start = interval[0];
        LocalDateTime end = interval[1];

        // 查询时间范围内的工单
        List<WorkOrder> workOrders = workOrderRepository.findByFactoryIdAndCreatedAtInRange(factoryId, start, end);

        // 按工序名聚合统计
        Map<String, int[]> aggregation = new HashMap<>();
        // int[0]=taskCount, int[1]=plannedQuantity, int[2]=goodQuantity, int[3]=defectQuantity

        for (WorkOrder wo : workOrders) {
            List<WorkOrderRequest.TaskItem> tasks = readTasks(wo.getTaskDataJson());
            for (WorkOrderRequest.TaskItem task : tasks) {
                if (task.getProcessName() == null || task.getProcessName().trim().isEmpty()) {
                    continue;
                }

                // 当 includeNotStarted=false 时排除未开始的任务
                if (!includeNotStarted) {
                    int completedQty = task.getCompletedQuantity() != null ? task.getCompletedQuantity() : 0;
                    String lastReportTime = task.getLastReportTime();
                    if (completedQty == 0 && (lastReportTime == null || lastReportTime.trim().isEmpty())) {
                        continue;
                    }
                }

                String processName = task.getProcessName().trim();
                int[] stats = aggregation.computeIfAbsent(processName, k -> new int[4]);
                stats[0] += 1;
                stats[1] += task.getPlannedQuantity() != null ? Math.max(task.getPlannedQuantity(), 0) : 0;
                stats[2] += task.getApprovedQuantity() != null ? Math.max(task.getApprovedQuantity(), 0) : 0;
                stats[3] += task.getDefectQuantity() != null ? Math.max(task.getDefectQuantity(), 0) : 0;
            }
        }

        // 获取该工厂的所有工序，按 name 建立映射
        List<Process> processes = processRepository.findAllByFactoryId(factoryId);
        Map<String, Process> processMap = new HashMap<>();
        for (Process p : processes) {
            processMap.put(p.getName(), p);
        }

        // 构建结果：只返回在 processes 表中能匹配到的工序
        List<ProcessCardItemResponse> items = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : aggregation.entrySet()) {
            String processName = entry.getKey();
            Process process = processMap.get(processName);
            if (process == null) {
                continue;
            }
            int[] stats = entry.getValue();
            items.add(new ProcessCardItemResponse(
                    process.getId(),
                    processName,
                    stats[0],
                    stats[1],
                    stats[2],
                    stats[3]
            ));
        }

        // 按工序名排序保证稳定输出
        items.sort(Comparator.comparing(ProcessCardItemResponse::getProcessName));

        return new ProcessCardListResponse(items);
    }

    // ==================== 工单进度报表查询 ====================

    /**
     * 按状态标签查询工单进度报表，返回最多 500 行。
     *
     * @param factoryId 工厂 ID
     * @param tab       状态标签
     * @param now       当前时间
     * @return 工单进度列表响应
     */
    public WorkOrderProgressListResponse listWorkOrders(Long factoryId, WorkOrderStatusTab tab, LocalDateTime now) {
        Pageable limit = PageRequest.of(0, MAX_WORK_ORDER_ROWS);
        List<WorkOrder> workOrders;

        switch (tab) {
            case NOT_STARTED:
                workOrders = workOrderRepository.findNotStarted(factoryId, limit);
                break;
            case IN_PROGRESS:
                workOrders = workOrderRepository.findInProgress(factoryId, limit);
                break;
            case DUE_WITHIN_3_DAYS:
                // planEnd >= now AND planEnd <= date(now)+3日 23:59:59.999
                LocalDateTime endOfThirdDay = now.toLocalDate().plusDays(3).atTime(LocalTime.of(23, 59, 59, 999_000_000));
                workOrders = workOrderRepository.findDueWithin3Days(factoryId, now, endOfThirdDay, limit);
                break;
            case OVERDUE_UNFINISHED:
                // status != 'COMPLETED' AND planEnd < now
                workOrders = workOrderRepository.findOverdueUnfinished(factoryId, now, limit);
                break;
            case OVERDUE_FINISHED_THIS_MONTH:
                // status = 'COMPLETED' AND completedAt in this_month AND completedAt > planEnd
                LocalDateTime[] monthInterval = getThisMonthInterval(now);
                workOrders = workOrderRepository.findOverdueFinishedThisMonth(factoryId, monthInterval[0], monthInterval[1], limit);
                break;
            case FINISHED_TODAY:
                // status = 'COMPLETED' AND completedAt in today
                LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
                LocalDateTime todayEnd = now.toLocalDate().plusDays(1).atStartOfDay();
                workOrders = workOrderRepository.findFinishedToday(factoryId, todayStart, todayEnd, limit);
                break;
            case FINISHED_THIS_MONTH:
                // status = 'COMPLETED' AND completedAt in this_month
                LocalDateTime[] monthRange = getThisMonthInterval(now);
                workOrders = workOrderRepository.findFinishedThisMonth(factoryId, monthRange[0], monthRange[1], limit);
                break;
            default:
                throw new IllegalArgumentException("Unknown status tab: " + tab);
        }

        List<WorkOrderProgressItemResponse> items = workOrders.stream()
                .map(wo -> toProgressItem(wo, tab))
                .collect(Collectors.toList());

        return new WorkOrderProgressListResponse(items);
    }

    // ==================== 绩效指标查询 ====================

    /**
     * 绩效指标查询。
     * 计算 6 项指标：计划数、完成数量、工单数量、不良品率、平均工单生产周期、超期未完成工单。
     */
    public PerformanceResponse getPerformance(Long factoryId, TimeRange range, LocalDateTime now) {
        LocalDateTime[] interval = range.toInterval(now);
        LocalDateTime start = interval[0];
        LocalDateTime end = interval[1];

        // 计划数
        int plannedQuantity = workOrderRepository.sumPlannedQuantityInRange(factoryId, start, end);

        // 完成数量（良品数）
        int completedQuantity = workOrderReportRepository.sumGoodQuantityInRange(factoryId, start, end);

        // 工单数量
        int workOrderCount = workOrderRepository.countInRange(factoryId, start, end);

        // 不良品率
        int goodQty = workOrderReportRepository.sumGoodQuantityInRange(factoryId, start, end);
        int defectQty = workOrderReportRepository.sumDefectQuantityInRange(factoryId, start, end);
        double defectRatePercent;
        long denominator = (long) goodQty + defectQty;
        if (denominator == 0) {
            defectRatePercent = 0.00;
        } else {
            defectRatePercent = BigDecimal.valueOf(defectQty)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // 平均工单生产周期
        List<WorkOrder> completedOrders = workOrderRepository.findCompletedInRange(factoryId, start, end);
        double averageCycleDays;
        if (completedOrders.isEmpty()) {
            averageCycleDays = 0.0;
        } else {
            double totalDays = 0;
            for (WorkOrder wo : completedOrders) {
                long days = ChronoUnit.DAYS.between(wo.getCreatedAt(), wo.getCompletedAt());
                totalDays += days;
            }
            averageCycleDays = BigDecimal.valueOf(totalDays / completedOrders.size())
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // 超期未完成工单（不受 range 影响）
        int overdueUnfinishedCount = workOrderRepository.countOverdueUnfinishedByFactory(factoryId, now);

        return new PerformanceResponse(
                plannedQuantity,
                completedQuantity,
                workOrderCount,
                defectRatePercent,
                averageCycleDays,
                overdueUnfinishedCount
        );
    }

    // ==================== 实时播报查询 ====================

    /**
     * 实时播报查询。
     * 按 createdAt 倒序返回报工记录，支持 since 参数用于增量拉取。
     *
     * @param factoryId 工厂 ID
     * @param since     可选，仅返回 createdAt > since 的记录；为 null 时返回最新 limit 条
     * @param limit     返回条数上限
     * @return 最近报工记录列表
     */
    public RecentReportListResponse listRecentReports(Long factoryId, LocalDateTime since, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<WorkOrderReport> reports;

        if (since != null) {
            reports = workOrderReportRepository.findRecentSince(factoryId, since, pageable);
        } else {
            reports = workOrderReportRepository.findRecent(factoryId, pageable);
        }

        List<RecentReportItemResponse> items = reports.stream()
                .map(this::toRecentReportItem)
                .collect(Collectors.toList());

        return new RecentReportListResponse(items);
    }

    private RecentReportItemResponse toRecentReportItem(WorkOrderReport report) {
        return new RecentReportItemResponse(
                report.getId(),
                report.getWorkOrder().getId(),
                report.getWorkOrderCode(),
                report.getReporterName(),
                report.getProcessName(),
                report.getGoodQuantity() != null ? report.getGoodQuantity() : 0,
                report.getDefectQuantity() != null ? report.getDefectQuantity() : 0,
                report.getCreatedAt() != null ? report.getCreatedAt().format(DATETIME_FORMATTER) : ""
        );
    }

    // ==================== Private helpers ====================

    private WorkOrderProgressItemResponse toProgressItem(WorkOrder wo, WorkOrderStatusTab tab) {
        return new WorkOrderProgressItemResponse(
                wo.getId(),
                wo.getCode(),
                wo.getProductCode(),
                wo.getProductName(),
                wo.getProductSpec(),
                wo.getStatus(),
                getStatusLabel(tab)
        );
    }

    /**
     * 获取状态标签对应的中文显示文字。
     */
    private String getStatusLabel(WorkOrderStatusTab tab) {
        switch (tab) {
            case NOT_STARTED:
                return "未开始";
            case IN_PROGRESS:
                return "执行中";
            case DUE_WITHIN_3_DAYS:
                return "3天内到期";
            case OVERDUE_UNFINISHED:
                return "超期未完工";
            case OVERDUE_FINISHED_THIS_MONTH:
                return "本月超期完成";
            case FINISHED_TODAY:
                return "今日完成";
            case FINISHED_THIS_MONTH:
                return "本月完成";
            default:
                return "";
        }
    }

    /**
     * 获取本月的半开区间 [firstDayOfMonth 00:00, firstDayOfNextMonth 00:00)。
     */
    private LocalDateTime[] getThisMonthInterval(LocalDateTime now) {
        LocalDate firstDayOfMonth = now.toLocalDate().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate firstDayOfNextMonth = now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth());
        return new LocalDateTime[]{
                firstDayOfMonth.atStartOfDay(),
                firstDayOfNextMonth.atStartOfDay()
        };
    }

    private List<WorkOrderRequest.TaskItem> readTasks(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkOrderRequest.TaskItem>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
