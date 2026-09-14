package com.heihu.workorder.service;

import com.heihu.workorder.dto.PerformanceResponse;
import com.heihu.workorder.dto.ProcessCardItemResponse;
import com.heihu.workorder.enums.TimeRange;
import com.heihu.workorder.model.Process;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.repository.ProcessRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeDashboardServiceTest {
    @Mock private WorkOrderRepository orders;
    @Mock private WorkOrderReportRepository reports;
    @Mock private ProcessRepository processes;
    private HomeDashboardService service;

    @BeforeEach
    void setUp() { service = new HomeDashboardService(orders, reports, processes); }

    @Test
    void cycleDaysIncludeFractionalDaysAndTotalsUseLongValues() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 12, 0);
        WorkOrder order = new WorkOrder();
        order.setCreatedAt(now.minusHours(36));
        order.setCompletedAt(now);
        when(orders.findCompletedInRange(eq(1L), any(), any())).thenReturn(Collections.singletonList(order));
        when(orders.sumPlannedQuantityInRange(eq(1L), any(), any())).thenReturn(3_000_000_000L);
        when(reports.sumGoodQuantityInRange(eq(1L), any(), any())).thenReturn(3_000_000_000L);
        when(reports.sumDefectQuantityInRange(eq(1L), any(), any())).thenReturn(1_000_000_000L);
        PerformanceResponse response = service.getPerformance(1L, TimeRange.THIS_MONTH, now);
        assertThat(response.getPlannedQuantity()).isEqualTo(3_000_000_000L);
        assertThat(response.getCompletedQuantity()).isEqualTo(3_000_000_000L);
        assertThat(response.getDefectRatePercent()).isEqualTo(25.0);
        assertThat(response.getAverageCycleDays()).isEqualTo(1.5);
    }

    @Test
    void processCardsSkipDisabledAndNullTasksAndDoNotOverflow() {
        WorkOrder first = new WorkOrder();
        first.setTaskDataJson("[null,{\"processName\":\"Cut\",\"plannedQuantity\":2147483647},"
                + "{\"processName\":\"Cut\",\"plannedQuantity\":10,\"enabled\":false}]");
        WorkOrder second = new WorkOrder();
        second.setTaskDataJson("[{\"processName\":\"Cut\",\"plannedQuantity\":2147483647}]");
        WorkOrder legacyNull = new WorkOrder();
        legacyNull.setTaskDataJson("null");
        when(orders.findByFactoryIdAndCreatedAtInRange(eq(1L), any(), any())).thenReturn(Arrays.asList(first, second, legacyNull));
        Process process = new Process();
        process.setId(2L);
        process.setName("Cut");
        when(processes.findAllByFactoryId(1L)).thenReturn(Collections.singletonList(process));
        ProcessCardItemResponse card = service.listProcessCards(1L, TimeRange.TODAY, true).getItems().get(0);
        assertThat(card.getTaskCount()).isEqualTo(2);
        assertThat(card.getPlannedQuantity()).isEqualTo(2L * Integer.MAX_VALUE);
    }
}
