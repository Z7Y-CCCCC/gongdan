package com.heihu.workorder.controller;

import com.heihu.workorder.dto.MonitorOnlineUserResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.exception.GlobalExceptionHandler;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.SystemMonitorService;
import com.heihu.workorder.service.TokenService;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MVC/controller/advice with private mocked sessions; no production or Redis connections. */
class SystemMonitorPaginationTest {
    private static final String AUTH = "Bearer local-fixture";
    private SystemMonitorService monitor;
    private TokenService tokens;
    private MockMvc http;

    @BeforeEach
    void setup() {
        monitor = mock(SystemMonitorService.class);
        tokens = mock(TokenService.class);
        when(tokens.requireAdmin(AUTH)).thenReturn(new AuthenticatedUser(11L, 7L, "PRIVATE", Role.ADMIN, "Fixture"));
        List<MonitorOnlineUserResponse> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) rows.add(row(i));
        when(monitor.listOnlineUsers(7L, null, null)).thenReturn(rows);
        http = MockMvcBuilders.standaloneSetup(new SystemMonitorController(monitor, tokens, 30))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"2147483647,200", "2147483647,2", "2147483647,2147483647"})
    void hugeValidIntPagesReturnAnEmptyPageInsteadOfOverflowing(int page, int size) throws Exception {
        http.perform(get("/api/monitor/online").header("Authorization", AUTH)
                        .param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void ordinaryPageRangesAndPagesPastTheEndRemainUnchanged() throws Exception {
        http.perform(get("/api/monitor/online").header("Authorization", AUTH).param("page", "2").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(3)).andExpect(jsonPath("$.items[1].id").value(4));
        http.perform(get("/api/monitor/online").header("Authorization", AUTH).param("page", "4").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "-2147483648,-2147483648"})
    void nonPositiveValuesKeepTheExistingFirstPageAndOneRowClamp(int page, int size) throws Exception {
        http.perform(get("/api/monitor/online").header("Authorization", AUTH)
                        .param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(1));
    }

    @Test
    void aHugeSizeStillUsesTheExistingTwoHundredRowCap() throws Exception {
        List<MonitorOnlineUserResponse> rows = new ArrayList<>();
        for (int i = 0; i < 205; i++) rows.add(row(i));
        when(monitor.listOnlineUsers(7L, null, null)).thenReturn(rows);
        http.perform(get("/api/monitor/online").header("Authorization", AUTH)
                        .param("page", "1").param("size", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(205))
                .andExpect(jsonPath("$.items.length()").value(200));
    }

    @Test
    void theUpperRangeAdditionCannotOverflowEither() throws Exception {
        // A lazy logical list tests arithmetic only; it does not allocate billions of sessions.
        when(monitor.listOnlineUsers(7L, null, null)).thenReturn(new AbstractList<MonitorOnlineUserResponse>() {
            @Override public int size() { return Integer.MAX_VALUE; }
            @Override public MonitorOnlineUserResponse get(int index) { return row(index); }
        });
        http.perform(get("/api/monitor/online").header("Authorization", AUTH)
                        .param("page", "10737419").param("size", "200"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.items.length()").value(47));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer no-session", "Bearer worker"})
    void extremePaginationDoesNotBypassAuthenticationOrAdminRole(String authorization) throws Exception {
        HttpStatus rejected = authorization.endsWith("worker") ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        when(tokens.requireAdmin(authorization)).thenThrow(new ApiException(rejected, "权限不足"));
        http.perform(get("/api/monitor/online").header("Authorization", authorization)
                        .param("page", "2147483647").param("size", "2147483647"))
                .andExpect(status().is(rejected.value()));
        verifyNoInteractions(monitor);
    }

    private MonitorOnlineUserResponse row(int index) {
        MonitorOnlineUserResponse row = new MonitorOnlineUserResponse();
        row.setId((long) index + 1L); row.setLoginName("Fixture " + index);
        return row;
    }
}
