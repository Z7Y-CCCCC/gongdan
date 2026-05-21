package com.heihu.workorder.controller;

import com.heihu.workorder.dto.AuthCodeListItem;
import com.heihu.workorder.dto.AuthCodeResponse;
import com.heihu.workorder.dto.CreateFactoryAdminRequest;
import com.heihu.workorder.dto.CreateFactoryRequest;
import com.heihu.workorder.dto.CreateSuperAdminRequest;
import com.heihu.workorder.dto.FactoryAdminListItem;
import com.heihu.workorder.dto.FactoryAdminResponse;
import com.heihu.workorder.dto.FactoryListItem;
import com.heihu.workorder.dto.FactoryResponse;
import com.heihu.workorder.dto.GenerateAuthCodeRequest;
import com.heihu.workorder.dto.ManagementListResponse;
import com.heihu.workorder.service.SuperAdminService;
import com.heihu.workorder.service.TokenService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {

    private final TokenService tokenService;
    private final SuperAdminService superAdminService;

    public SuperAdminController(TokenService tokenService, SuperAdminService superAdminService) {
        this.tokenService = tokenService;
        this.superAdminService = superAdminService;
    }

    // ===== 工厂管理 =====

    @PostMapping("/factories")
    public ResponseEntity<FactoryResponse> createFactory(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateFactoryRequest request
    ) {
        tokenService.requireSuperAdmin(authorization);
        FactoryResponse response = superAdminService.createFactory(
                request.getFactoryCode(), request.getFactoryName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/factories")
    public List<FactoryListItem> listFactories(
            @RequestHeader("Authorization") String authorization
    ) {
        tokenService.requireSuperAdmin(authorization);
        ManagementListResponse managementList = superAdminService.getManagementList();
        return managementList.getFactories();
    }

    @PatchMapping("/factories/{code}/disable")
    public ResponseEntity<Void> disableFactory(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String code
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.disableFactory(code);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/factories/{code}/enable")
    public ResponseEntity<Void> enableFactory(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String code
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.enableFactory(code);
        return ResponseEntity.ok().build();
    }

    // ===== 工厂管理员账号 =====

    @PostMapping("/factory-admins")
    public ResponseEntity<FactoryAdminResponse> createFactoryAdmin(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateFactoryAdminRequest request
    ) {
        tokenService.requireSuperAdmin(authorization);
        FactoryAdminResponse response = superAdminService.createFactoryAdmin(
                request.getFactoryCode(), request.getUsername(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/factory-admins")
    public List<FactoryAdminListItem> listFactoryAdmins(
            @RequestHeader("Authorization") String authorization
    ) {
        tokenService.requireSuperAdmin(authorization);
        ManagementListResponse managementList = superAdminService.getManagementList();
        return managementList.getFactoryAdmins();
    }

    @PatchMapping("/factory-admins/{id}/disable")
    public ResponseEntity<Void> disableFactoryAdmin(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.disableFactoryAdmin(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/factory-admins/{id}/enable")
    public ResponseEntity<Void> enableFactoryAdmin(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.enableFactoryAdmin(id);
        return ResponseEntity.ok().build();
    }

    // ===== 授权码 =====

    @PostMapping("/auth-codes")
    public ResponseEntity<AuthCodeResponse> generateAuthCode(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody GenerateAuthCodeRequest request
    ) {
        tokenService.requireSuperAdmin(authorization);
        AuthCodeResponse response = superAdminService.generateAuthCode(
                request.getFactoryCode(), request.getExpirationMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/auth-codes")
    public List<AuthCodeListItem> listAuthCodes(
            @RequestHeader("Authorization") String authorization
    ) {
        tokenService.requireSuperAdmin(authorization);
        ManagementListResponse managementList = superAdminService.getManagementList();
        return managementList.getAuthCodes();
    }

    @PatchMapping("/auth-codes/{id}/disable")
    public ResponseEntity<Void> disableAuthCode(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.disableAuthCode(id);
        return ResponseEntity.ok().build();
    }

    // ===== 超级管理员账号 =====

    @PostMapping("/admins")
    public ResponseEntity<Void> createSuperAdmin(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateSuperAdminRequest request
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.createSuperAdmin(request.getUsername(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/super-admins")
    public List<FactoryAdminListItem> listSuperAdmins(
            @RequestHeader("Authorization") String authorization
    ) {
        tokenService.requireSuperAdmin(authorization);
        return superAdminService.listSuperAdmins();
    }

    @PostMapping("/users/{userId}/reset-password")
    public Map<String, String> resetUserPassword(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        tokenService.requireSuperAdmin(authorization);
        String newPassword = (body != null) ? body.get("password") : null;
        String password = superAdminService.resetUserPassword(userId, newPassword);
        Map<String, String> result = new HashMap<>();
        result.put("password", password);
        return result;
    }

    // ===== 综合管理列表 =====

    @GetMapping("/management")
    public ManagementListResponse getManagementList(
            @RequestHeader("Authorization") String authorization
    ) {
        tokenService.requireSuperAdmin(authorization);
        return superAdminService.getManagementList();
    }

    @DeleteMapping("/factory-admins/{id}")
    public ResponseEntity<Void> deleteFactoryAdmin(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.deleteFactoryAdmin(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/auth-codes/{id}")
    public ResponseEntity<Void> deleteAuthCode(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.deleteAuthCode(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/super-admins/{id}")
    public ResponseEntity<Void> deleteSuperAdmin(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        tokenService.requireSuperAdmin(authorization);
        superAdminService.deleteSuperAdmin(id);
        return ResponseEntity.ok().build();
    }
}
