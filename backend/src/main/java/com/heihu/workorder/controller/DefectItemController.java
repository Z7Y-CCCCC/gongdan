package com.heihu.workorder.controller;

import com.heihu.workorder.dto.DefectItemRequest;
import com.heihu.workorder.dto.DefectItemResponse;
import com.heihu.workorder.service.DefectItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/defect-items")
@CrossOrigin(origins = "*")
public class DefectItemController {

    private final DefectItemService defectItemService;

    public DefectItemController(DefectItemService defectItemService) {
        this.defectItemService = defectItemService;
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
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        Long factoryId = extractFactoryId(token);
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DefectItemResponse> result = defectItemService.list(factoryId, code, name, pr);
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", result.getContent());
        resp.put("total", result.getTotalElements());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<DefectItemResponse> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody DefectItemRequest req
    ) {
        return ResponseEntity.ok(defectItemService.create(extractFactoryId(token), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefectItemResponse> update(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody DefectItemRequest req
    ) {
        return ResponseEntity.ok(defectItemService.update(extractFactoryId(token), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id
    ) {
        defectItemService.delete(extractFactoryId(token), id);
        return ResponseEntity.ok().build();
    }
}
