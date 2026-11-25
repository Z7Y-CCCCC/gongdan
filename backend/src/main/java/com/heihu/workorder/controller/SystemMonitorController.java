package com.heihu.workorder.controller;

import com.heihu.workorder.dto.MonitorCacheEntryResponse;
import com.heihu.workorder.dto.MonitorCacheMetricsResponse;
import com.heihu.workorder.dto.MonitorCacheNameResponse;
import com.heihu.workorder.dto.MonitorCacheRecoveryResponse;
import com.heihu.workorder.dto.MonitorOnlineUserResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.SystemMonitorService;
import com.heihu.workorder.service.TokenService;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/monitor")
public class SystemMonitorController {

    private final SystemMonitorService monitorService;
    private final TokenService tokenService;
    private final long recoveryWindowMinutes;

    public SystemMonitorController(
            SystemMonitorService monitorService,
            TokenService tokenService,
            @Value("${app.cache.recovery-ttl-minutes:30}") long recoveryWindowMinutes
    ) {
        this.monitorService = monitorService;
        this.tokenService = tokenService;
        this.recoveryWindowMinutes = recoveryWindowMinutes;
    }

    @GetMapping("/online")
    public ResponseEntity<Map<String, Object>> listOnlineUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String loginName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        tokenService.refreshSessionMetadata(authorization, request);
        List<MonitorOnlineUserResponse> all = monitorService.listOnlineUsers(user.getFactoryId(), ipAddress, loginName);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(200, size));
        // Bound in long before narrowing: valid int page/size values must not wrap negative.
        int from = (int) Math.min((long) (safePage - 1) * safeSize, all.size());
        int to = (int) Math.min((long) from + safeSize, all.size());
        Map<String, Object> response = new HashMap<>();
        response.put("items", all.subList(from, to));
        response.put("total", all.size());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/online/{id}")
    public ResponseEntity<Void> forceLogout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        monitorService.forceLogout(user.getFactoryId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cache")
    public ResponseEntity<MonitorCacheMetricsResponse> getCacheMetrics(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(monitorService.getMetrics(user.getFactoryId()));
    }

    @GetMapping("/cache/names")
    public ResponseEntity<List<MonitorCacheNameResponse>> listCacheNames(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(monitorService.listCacheNames(user.getFactoryId()));
    }

    @GetMapping("/cache/keys")
    public ResponseEntity<List<String>> listCacheKeys(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String cacheName
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(monitorService.listCacheKeys(user.getFactoryId(), cacheName));
    }

    @GetMapping("/cache/value")
    public ResponseEntity<MonitorCacheEntryResponse> getCacheValue(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String cacheName,
            @RequestParam String cacheKey
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(monitorService.getCacheValue(user.getFactoryId(), cacheName, cacheKey));
    }

    @DeleteMapping("/cache/name")
    public ResponseEntity<Map<String, Object>> clearCacheName(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String cacheName
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(clearResponse(monitorService.clearCacheName(user.getFactoryId(), cacheName)));
    }

    @DeleteMapping("/cache/key")
    public ResponseEntity<Map<String, Object>> clearCacheKey(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String cacheName,
            @RequestParam String cacheKey
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(clearResponse(monitorService.clearCacheKey(user.getFactoryId(), cacheName, cacheKey)));
    }

    @DeleteMapping("/cache/all")
    public ResponseEntity<Map<String, Object>> clearAll(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(clearResponse(monitorService.clearAll(user.getFactoryId())));
    }

    @GetMapping("/cache/recovery")
    public ResponseEntity<List<MonitorCacheRecoveryResponse>> listRecoveries(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(monitorService.listRecoveries(user.getFactoryId()));
    }

    @PostMapping("/cache/recovery/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser user = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(clearResponse(monitorService.restore(user.getFactoryId(), id)));
    }

    private Map<String, Object> clearResponse(int count) {
        Map<String, Object> response = new HashMap<>();
        response.put("affected", count);
        response.put("recoveryWindowMinutes", recoveryWindowMinutes);
        return response;
    }
}
