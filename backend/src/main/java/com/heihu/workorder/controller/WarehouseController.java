package com.heihu.workorder.controller;

import com.heihu.workorder.dto.WarehouseRequest;
import com.heihu.workorder.dto.WarehouseResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.service.WarehouseService;
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
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final TokenService tokenService;

    public WarehouseController(WarehouseService warehouseService, TokenService tokenService) {
        this.warehouseService = warehouseService;
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
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WarehouseResponse> result = warehouseService.list(currentUser.getFactoryId(), code, name, pageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public WarehouseResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody WarehouseRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return warehouseService.create(currentUser.getFactoryId(), request);
    }

    @PutMapping("/{id}")
    public WarehouseResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody WarehouseRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return warehouseService.update(currentUser.getFactoryId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        warehouseService.delete(currentUser.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
