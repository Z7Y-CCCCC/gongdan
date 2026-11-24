package com.heihu.workorder.controller;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.exception.GlobalExceptionHandler;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.WorkOrderRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileDashboardControllerTest {
    private final TokenService tokens = mock(TokenService.class);
    private final WorkOrderService orders = mock(WorkOrderService.class);
    private final WorkOrderReportService reports = mock(WorkOrderReportService.class);
    private final WorkOrderRepository orderRepository = mock(WorkOrderRepository.class);
    private final WorkOrderReportRepository reportRepository = mock(WorkOrderReportRepository.class);
    private MockMvc mvc;
    @BeforeEach
    void setup() {
        when(tokens.requireUser("Bearer worker")).thenReturn(new AuthenticatedUser(7L, 1L, "TEST", Role.PRODUCTION, "Worker"));
        when(tokens.requireUser(null)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "请先登录"));
        mvc = MockMvcBuilders.standaloneSetup(
                new MobileDashboardController(new MobileDashboardService(orderRepository, reportRepository, orders), tokens),
                new MobileWorkOrderController(orders, tokens, reports)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test
    void missingSessionIsUnauthorizedAndInvalidDaysAreBadRequests() throws Exception {
        mvc.perform(get("/api/mobile/dashboard")).andExpect(status().isUnauthorized());
        for (String day : new String[]{"2026-02-30", "0999-01-01", "10000-01-01", "bad"}) {
            mvc.perform(get("/api/mobile/dashboard").header("Authorization", "Bearer worker").param("day", day))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orderRepository, reportRepository, orders);
    }
    @Test
    void reportableFilterCannotBeCombinedWithExplicitStatus() throws Exception {
        mvc.perform(get("/api/mobile/work-orders").header("Authorization", "Bearer worker")
                .param("reportableOnly", "true").param("status", "ALL")).andExpect(status().isBadRequest());
        verify(orders, never()).searchMobile(any(), any(), any(), any(), any(), anyBoolean(), any());
    }
}
