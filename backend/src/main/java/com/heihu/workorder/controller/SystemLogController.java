package com.heihu.workorder.controller;

import com.heihu.workorder.dto.*;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.SystemLogService;
import com.heihu.workorder.service.TokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system-logs")
public class SystemLogController {

    private final SystemLogService systemLogService;
    private final TokenService tokenService;

    public SystemLogController(SystemLogService systemLogService, TokenService tokenService) {
        this.systemLogService = systemLogService;
        this.tokenService = tokenService;
    }

    @GetMapping("/imports")
    public ResponseEntity<Map<String, Object>> listImports(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String module,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SystemImportLogResponse> result = systemLogService.listImportLogs(currentUser.getFactoryId(), module, pageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/imports")
    public ResponseEntity<Void> recordImport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SystemImportLogRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        systemLogService.recordImport(currentUser.getFactoryId(), currentUser.getName(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/operations")
    public ResponseEntity<Map<String, Object>> listOperations(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String module,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SystemOperationLogResponse> result = systemLogService.listOperationLogs(currentUser.getFactoryId(), module, pageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/operations")
    public ResponseEntity<Void> recordOperation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SystemOperationLogRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        systemLogService.recordOperation(currentUser.getFactoryId(), currentUser.getName(), request);
        return ResponseEntity.ok().build();
    }
}
