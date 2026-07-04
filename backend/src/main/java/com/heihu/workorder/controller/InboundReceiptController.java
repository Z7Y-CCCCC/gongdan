package com.heihu.workorder.controller;

import com.heihu.workorder.dto.InboundReceiptRequest;
import com.heihu.workorder.dto.InboundReceiptResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.InboundReceiptService;
import com.heihu.workorder.service.TokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inbound-receipts")
public class InboundReceiptController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InboundReceiptService inboundReceiptService;
    private final TokenService tokenService;

    public InboundReceiptController(InboundReceiptService inboundReceiptService, TokenService tokenService) {
        this.inboundReceiptService = inboundReceiptService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String inboundType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        LocalDateTime start = null;
        LocalDateTime end = null;
        if (startTime != null && !startTime.trim().isEmpty()) {
            start = LocalDateTime.parse(startTime.trim(), FMT);
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            end = LocalDateTime.parse(endTime.trim(), FMT);
        }

        Page<InboundReceiptResponse> result = inboundReceiptService.list(
                currentUser.getFactoryId(), code, inboundType, start, end, pageRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InboundReceiptResponse> getById(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(inboundReceiptService.getById(currentUser.getFactoryId(), id));
    }

    @PostMapping
    public ResponseEntity<InboundReceiptResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody InboundReceiptRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(inboundReceiptService.create(
                currentUser.getFactoryId(),
                currentUser.getUserId(),
                request
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        inboundReceiptService.delete(currentUser.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
