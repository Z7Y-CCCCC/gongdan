package com.heihu.workorder.controller;

import com.heihu.workorder.dto.InventoryDetailResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.InventoryDetailService;
import com.heihu.workorder.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory-details")
@CrossOrigin(origins = "*")
public class InventoryDetailController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InventoryDetailService inventoryDetailService;
    private final TokenService tokenService;

    public InventoryDetailController(InventoryDetailService inventoryDetailService, TokenService tokenService) {
        this.inventoryDetailService = inventoryDetailService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String receiptCode,
            @RequestParam(required = false) String receiptType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productSpec,
            @RequestParam(required = false) String productAttribute,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        Long factoryId = currentUser.getFactoryId();

        LocalDateTime start = null;
        LocalDateTime end = null;
        if (startTime != null && !startTime.trim().isEmpty()) {
            start = LocalDateTime.parse(startTime.trim(), FMT);
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            end = LocalDateTime.parse(endTime.trim(), FMT);
        }

        long total = inventoryDetailService.count(factoryId, receiptCode, receiptType,
                sourceType, remark, productCode, productName, productSpec, productAttribute,
                start, end);

        List<InventoryDetailResponse> items = inventoryDetailService.list(factoryId, receiptCode, receiptType,
                sourceType, remark, productCode, productName, productSpec, productAttribute,
                start, end, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", total);
        return ResponseEntity.ok(response);
    }
}
