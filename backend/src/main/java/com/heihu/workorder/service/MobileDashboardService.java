package com.heihu.workorder.service;

import com.heihu.workorder.dto.MobileDashboardResponse;
import com.heihu.workorder.repository.WorkOrderRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MobileDashboardService {
    private final WorkOrderRepository orders;
    private final WorkOrderReportRepository reports;
    private final WorkOrderService orderService;

    public MobileDashboardService(WorkOrderRepository orders, WorkOrderReportRepository reports,
                                  WorkOrderService orderService) {
        this.orders = orders;
        this.reports = reports;
        this.orderService = orderService;
    }

    public MobileDashboardResponse get(Long factoryId, Long userId, String dayValue) {
        LocalDate day = parseDay(dayValue);
        boolean lastRepresentableDay = day.equals(LocalDate.of(9999, 12, 31));
        // MySQL DATETIME(6) cannot bind year 10000. On its final representable
        // day use an inclusive microsecond upper bound instead of overflowing.
        LocalDateTime dayEnd = lastRepresentableDay
                ? day.atTime(LocalTime.of(23, 59, 59, 999_999_000)) : day.plusDays(1).atStartOfDay();
        WorkOrderRepository.MobileTotals orderTotals = orders.aggregateMobileTotals(factoryId);
        WorkOrderReportRepository.MobileTotals reportTotals = reports.aggregateMobileTotals(
                factoryId, userId, day.atStartOfDay(), dayEnd, lastRepresentableDay);
        return new MobileDashboardResponse(orderTotals.getWorkOrderTotal(), orderTotals.getTotalPlanned(),
                orderTotals.getTotalGood(), orderTotals.getActiveOrderCount(), reportTotals.getReportTotal(),
                reportTotals.getPendingReportCount(), reportTotals.getTodayReportCount(), reportTotals.getTotalDefect(),
                reportTotals.getTodayGood(), reportTotals.getTodayDefect(), reportTotals.getTodayPending(),
                orderService.recentMobile(factoryId),
                reports.rankMobileApprovedGood(factoryId, userId, PageRequest.of(0, 5)).stream()
                        .map(value -> new MobileDashboardResponse.Ranking(value.getName(), value.getValue()))
                        .collect(Collectors.toList()));
    }

    private LocalDate parseDay(String value) {
        if (value == null) return LocalDate.now();
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("统计日期格式不正确，请填写有效的YYYY-MM-DD日期");
        }
        try {
            LocalDate day = LocalDate.parse(value);
            if (day.getYear() < 1000 || day.getYear() > 9999) {
                throw new IllegalArgumentException("统计日期年份须在1000到9999之间");
            }
            return day;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("统计日期格式不正确，请填写有效的YYYY-MM-DD日期", exception);
        }
    }
}
