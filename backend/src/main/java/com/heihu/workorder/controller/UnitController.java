package com.heihu.workorder.controller;

import com.heihu.workorder.dto.UnitRequest;
import com.heihu.workorder.dto.UnitResponse;
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
@CrossOrigin(origins = "*")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    private Long extractFactoryId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            try { return Long.parseLong(token.substring(7)); } catch (NumberFormatException ignored) {}
        }
        return 1L;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        Long factoryId = extractFactoryId(token);
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UnitResponse> result = unitService.list(factoryId, name, pr);
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", result.getContent());
        resp.put("total", result.getTotalElements());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<UnitResponse> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody UnitRequest req
    ) {
        return ResponseEntity.ok(unitService.create(extractFactoryId(token), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnitResponse> update(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody UnitRequest req
    ) {
        return ResponseEntity.ok(unitService.update(extractFactoryId(token), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id
    ) {
        unitService.delete(extractFactoryId(token), id);
        return ResponseEntity.ok().build();
    }
}
