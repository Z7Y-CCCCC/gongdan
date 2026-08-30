package com.heihu.workorder.controller;

import com.heihu.workorder.dto.ExtensionRecordRequest;
import com.heihu.workorder.dto.ExtensionRecordResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.ExtensionRecordService;
import com.heihu.workorder.service.TokenService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/field-events")
public class MobileFieldEventController {

    private final ExtensionRecordService recordService;
    private final TokenService tokenService;

    public MobileFieldEventController(ExtensionRecordService recordService, TokenService tokenService) {
        this.recordService = recordService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ExtensionRecordResponse> result = recordService.list(
                currentUser.getFactoryId(),
                ExtensionRecordService.MODULE_FIELD_EVENT,
                status,
                pageRequest
        );
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ExtensionRecordResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ExtensionRecordRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        request.setModuleName(ExtensionRecordService.MODULE_FIELD_EVENT);
        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            request.setStatus("OPEN");
        }
        return ResponseEntity.ok(recordService.create(currentUser.getFactoryId(), currentUser.getUserId(), request));
    }
}
