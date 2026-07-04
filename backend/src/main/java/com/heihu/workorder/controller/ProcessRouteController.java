package com.heihu.workorder.controller;

import com.heihu.workorder.dto.ProcessRouteRequest;
import com.heihu.workorder.dto.ProcessRouteResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.ProcessRouteService;
import com.heihu.workorder.service.TokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/process-routes")
public class ProcessRouteController {

    private final ProcessRouteService routeService;
    private final TokenService tokenService;

    public ProcessRouteController(ProcessRouteService routeService, TokenService tokenService) {
        this.routeService = routeService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser cu = tokenService.requireUser(authorization);
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProcessRouteResponse> result = routeService.list(cu.getFactoryId(), code, name, pr);
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", result.getContent());
        resp.put("total", result.getTotalElements());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<ProcessRouteResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProcessRouteRequest req
    ) {
        AuthenticatedUser cu = tokenService.requireUser(authorization);
        return ResponseEntity.ok(routeService.create(cu.getFactoryId(), cu.getUserId(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProcessRouteResponse> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody ProcessRouteRequest req
    ) {
        AuthenticatedUser cu = tokenService.requireUser(authorization);
        return ResponseEntity.ok(routeService.update(cu.getFactoryId(), cu.getUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser cu = tokenService.requireUser(authorization);
        routeService.delete(cu.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
