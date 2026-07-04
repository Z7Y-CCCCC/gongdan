package com.heihu.workorder.controller;

import com.heihu.workorder.dto.BomRequest;
import com.heihu.workorder.dto.BomResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.BomService;
import com.heihu.workorder.service.TokenService;
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
@RequestMapping("/api/boms")
public class BomController {

    private final BomService bomService;
    private final TokenService tokenService;

    public BomController(BomService bomService, TokenService tokenService) {
        this.bomService = bomService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BomResponse> result = bomService.list(currentUser.getFactoryId(), code, productName, pageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public BomResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody BomRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return bomService.create(currentUser.getFactoryId(), request);
    }

    @PutMapping("/{id}")
    public BomResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody BomRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return bomService.update(currentUser.getFactoryId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        bomService.delete(currentUser.getFactoryId(), id);
        return ResponseEntity.ok().build();
    }
}
