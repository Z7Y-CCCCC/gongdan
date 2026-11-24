package com.heihu.workorder.service;

import com.heihu.workorder.dto.MobileDashboardResponse;
import com.heihu.workorder.repository.WorkOrderRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileDashboardServiceTest {
    private final WorkOrderRepository orders = mock(WorkOrderRepository.class);
    private final WorkOrderReportRepository reports = mock(WorkOrderReportRepository.class);
    private final WorkOrderService orderService = mock(WorkOrderService.class);
    private final WorkOrderRepository.MobileTotals orderTotals = mock(WorkOrderRepository.MobileTotals.class);
    private final WorkOrderReportRepository.MobileTotals reportTotals = mock(WorkOrderReportRepository.MobileTotals.class);
    private final MobileDashboardService service = new MobileDashboardService(orders, reports, orderService);

    @BeforeEach
    void setup() {
        when(orders.aggregateMobileTotals(1L)).thenReturn(orderTotals);
        when(reports.aggregateMobileTotals(eq(1L), eq(7L), any(), any(), anyBoolean())).thenReturn(reportTotals);
        when(reports.rankMobileApprovedGood(eq(1L), eq(7L), any())).thenReturn(Collections.emptyList());
        when(orderService.recentMobile(1L)).thenReturn(Collections.emptyList());
    }

    @Test
    void scalarTotalsBeyondOldDownloadLimitAndIntegerRangeAreReturnedWithoutTruncation() {
        when(orderTotals.getWorkOrderTotal()).thenReturn(10_001L);
        when(orderTotals.getTotalPlanned()).thenReturn(9_000_000_000L);
        when(orderTotals.getTotalGood()).thenReturn(8_000_000_000L);
        when(orderTotals.getActiveOrderCount()).thenReturn(6_001L);
        when(reportTotals.getReportTotal()).thenReturn(8_001L);
        when(reportTotals.getPendingReportCount()).thenReturn(6_001L);
        when(reportTotals.getTodayReportCount()).thenReturn(7_001L);
        when(reportTotals.getTotalDefect()).thenReturn(3_000_000_000L);
        when(reportTotals.getTodayGood()).thenReturn(4_000_000_000L);
        when(reportTotals.getTodayDefect()).thenReturn(2_500_000_000L);
        when(reportTotals.getTodayPending()).thenReturn(5_001L);
        MobileDashboardResponse result = service.get(1L, 7L, "2026-11-24");
        assertThat(result.getWorkOrderTotal()).isEqualTo(10_001L);
        assertThat(result.getTotalPlanned()).isEqualTo(9_000_000_000L);
        assertThat(result.getTotalGood()).isEqualTo(8_000_000_000L);
        assertThat(result.getActiveOrderCount()).isEqualTo(6_001L);
        assertThat(result.getReportTotal()).isEqualTo(8_001L);
        assertThat(result.getPendingReportCount()).isEqualTo(6_001L);
        assertThat(result.getTodayReportCount()).isEqualTo(7_001L);
        assertThat(result.getTotalDefect()).isEqualTo(3_000_000_000L);
        assertThat(result.getTodayGood()).isEqualTo(4_000_000_000L);
        assertThat(result.getTodayDefect()).isEqualTo(2_500_000_000L);
        assertThat(result.getTodayPending()).isEqualTo(5_001L);
        verify(reports).aggregateMobileTotals(1L, 7L, LocalDate.of(2026, 11, 24).atStartOfDay(),
                LocalDate.of(2026, 11, 25).atStartOfDay(), false);
        verify(reports).rankMobileApprovedGood(eq(1L), eq(7L), argThat(page -> page.getPageSize() == 5 && page.getPageNumber() == 0));
        verify(orders, never()).findByFactoryId(any(), any(Pageable.class));
        verify(reports, never()).findAll();
    }

    @Test
    void defaultDayUsesServerTodayAndFinalMysqlDayHasSafeInclusiveUpperBound() {
        service.get(1L, 7L, null);
        verify(reports).aggregateMobileTotals(1L, 7L, LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay(), false);
        service.get(1L, 7L, "9999-12-31");
        verify(reports).aggregateMobileTotals(1L, 7L, LocalDateTime.of(9999, 12, 31, 0, 0),
                LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000), true);
    }

    @Test
    void malformedAndOutOfRangeDaysAreRejectedBeforeDatabaseAccess() {
        for (String day : new String[]{"", "2026-2-01", "2026-02-30", "0999-01-01", "10000-01-01", "0000-01-01", "2026-11-24T00:00:00"}) {
            assertThatThrownBy(() -> service.get(1L, 7L, day)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(orderTotals, reportTotals);
        verify(orders, never()).aggregateMobileTotals(anyLong());
    }
}
