package com.heihu.workorder.controller;

import com.heihu.workorder.dto.UnitRequest;
import com.heihu.workorder.dto.UnitResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.service.UnitService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService unitService;
    private final TokenService tokenService;

    public UnitController(UnitService unitService, TokenService tokenService) {
        this.unitService = unitService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UnitResponse> result = unitService.list(currentUser.getFactoryId(), name, pr);
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", result.getContent());
        resp.put("total", result.getTotalElements());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<UnitResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UnitRequest req
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(unitService.create(currentUser.getFactoryId(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnitResponse> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody UnitRequest req
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(unitService.update(currentUser.getFactoryId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        unitService.delete(currentUser.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
