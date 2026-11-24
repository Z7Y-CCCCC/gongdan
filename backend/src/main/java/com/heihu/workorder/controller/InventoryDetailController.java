package com.heihu.workorder.controller;

import com.heihu.workorder.dto.InventoryDetailPageResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.InventoryDetailService;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.util.DateTimeParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/inventory-details")
public class InventoryDetailController {

    private final InventoryDetailService inventoryDetailService;
    private final TokenService tokenService;

    public InventoryDetailController(InventoryDetailService inventoryDetailService, TokenService tokenService) {
        this.inventoryDetailService = inventoryDetailService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<InventoryDetailPageResponse> list(
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
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        Long factoryId = currentUser.getFactoryId();

        LocalDateTime start = null;
        LocalDateTime end = null;
        if (startTime != null && !startTime.trim().isEmpty()) {
            start = DateTimeParser.parse(startTime);
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            end = DateTimeParser.parse(endTime);
        }

        return ResponseEntity.ok(inventoryDetailService.list(factoryId, receiptCode, receiptType,
                sourceType, remark, productCode, productName, productSpec, productAttribute,
                start, end, page, size));
    }
}
