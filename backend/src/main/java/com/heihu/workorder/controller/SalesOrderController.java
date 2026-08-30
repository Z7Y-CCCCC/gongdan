package com.heihu.workorder.controller;

import com.heihu.workorder.dto.SalesOrderRequest;
import com.heihu.workorder.dto.SalesOrderResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.SalesOrderService;
import com.heihu.workorder.service.TokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final TokenService tokenService;

    public SalesOrderController(SalesOrderService salesOrderService, TokenService tokenService) {
        this.salesOrderService = salesOrderService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SalesOrderResponse> result = salesOrderService.list(
                currentUser.getFactoryId(), code, customerName, status, pageRequest
        );
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public SalesOrderResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SalesOrderRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return salesOrderService.create(currentUser.getFactoryId(), currentUser.getUserId(), request);
    }

    @PutMapping("/{id}")
    public SalesOrderResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody SalesOrderRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return salesOrderService.update(currentUser.getFactoryId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        salesOrderService.delete(currentUser.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
