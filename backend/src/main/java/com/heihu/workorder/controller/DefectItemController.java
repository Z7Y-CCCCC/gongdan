package com.heihu.workorder.controller;

import com.heihu.workorder.dto.DefectItemRequest;
import com.heihu.workorder.dto.DefectItemResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.DefectItemService;
import com.heihu.workorder.service.TokenService;
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
    private final TokenService tokenService;

    public DefectItemController(DefectItemService defectItemService, TokenService tokenService) {
        this.defectItemService = defectItemService;
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
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DefectItemResponse> result = defectItemService.list(currentUser.getFactoryId(), code, name, pr);
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", result.getContent());
        resp.put("total", result.getTotalElements());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<DefectItemResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DefectItemRequest req
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(defectItemService.create(currentUser.getFactoryId(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefectItemResponse> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody DefectItemRequest req
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(defectItemService.update(currentUser.getFactoryId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        defectItemService.delete(currentUser.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
