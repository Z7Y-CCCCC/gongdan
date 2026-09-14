package com.heihu.workorder.controller;

import com.heihu.workorder.dto.WorkOrderResponse;
import com.heihu.workorder.dto.WorkOrderReporterResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.service.WorkOrderService;
import com.heihu.workorder.service.WorkOrderReportService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/work-orders")
public class MobileWorkOrderController {

    private final WorkOrderService workOrderService;
    private final TokenService tokenService;
    private final WorkOrderReportService reportService;

    public MobileWorkOrderController(WorkOrderService workOrderService, TokenService tokenService, WorkOrderReportService reportService) {
        this.workOrderService = workOrderService;
        this.tokenService = tokenService;
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WorkOrderResponse> result = workOrderService.list(currentUser.getFactoryId(), code, productName, pageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public WorkOrderResponse get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return workOrderService.get(currentUser.getFactoryId(), id);
    }

    @GetMapping("/by-code/{code}")
    public WorkOrderResponse getByCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String code
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return workOrderService.getByCode(currentUser.getFactoryId(), code);
    }

    @GetMapping("/{id}/reporters")
    public List<WorkOrderReporterResponse> getReporters(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestParam(required = false) Integer processSequenceNo
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return reportService.listReporters(currentUser.getFactoryId(), id, processSequenceNo);
    }
}
