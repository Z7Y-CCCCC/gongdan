package com.heihu.workorder.controller;

import com.heihu.workorder.dto.CurrentFactoryResponse;
import com.heihu.workorder.dto.UpdateFactoryRequest;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.FactoryService;
import com.heihu.workorder.service.TokenService;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/factory")
public class FactoryController {

    private final FactoryService factoryService;
    private final TokenService tokenService;

    public FactoryController(FactoryService factoryService, TokenService tokenService) {
        this.factoryService = factoryService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<CurrentFactoryResponse> getCurrent(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(factoryService.getCurrent(currentUser.getFactoryId()));
    }

    @PutMapping
    public ResponseEntity<CurrentFactoryResponse> updateCurrent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateFactoryRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(factoryService.updateCurrent(currentUser.getFactoryId(), request));
    }
}
