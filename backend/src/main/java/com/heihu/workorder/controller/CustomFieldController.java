package com.heihu.workorder.controller;

import com.heihu.workorder.dto.CustomFieldRequest;
import com.heihu.workorder.dto.CustomFieldResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.CustomFieldService;
import com.heihu.workorder.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/custom-fields")
@CrossOrigin(origins = "*")
public class CustomFieldController {

    private final CustomFieldService customFieldService;
    private final TokenService tokenService;

    public CustomFieldController(CustomFieldService customFieldService, TokenService tokenService) {
        this.customFieldService = customFieldService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "workorder") String scope
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        List<CustomFieldResponse> items = customFieldService.list(currentUser.getFactoryId(), scope);
        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", items.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CustomFieldResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CustomFieldRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(customFieldService.create(currentUser.getFactoryId(), currentUser.getUserId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomFieldResponse> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody CustomFieldRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(customFieldService.update(currentUser.getFactoryId(), currentUser.getUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        customFieldService.delete(currentUser.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
