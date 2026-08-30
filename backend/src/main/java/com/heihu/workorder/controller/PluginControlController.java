package com.heihu.workorder.controller;

import com.heihu.workorder.dto.PluginActivationRequest;
import com.heihu.workorder.dto.PluginCatalogResponse;
import com.heihu.workorder.dto.PluginConfigRequest;
import com.heihu.workorder.dto.PluginCreateRequest;
import com.heihu.workorder.dto.PluginRuntimeManifestResponse;
import com.heihu.workorder.dto.PluginVersionRequest;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.PluginControlService;
import com.heihu.workorder.service.TokenService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/plugins")
public class PluginControlController {

    private final PluginControlService pluginControlService;
    private final TokenService tokenService;

    public PluginControlController(PluginControlService pluginControlService, TokenService tokenService) {
        this.pluginControlService = pluginControlService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<List<PluginCatalogResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.list(currentUser.getFactoryId()));
    }

    @PostMapping
    public ResponseEntity<PluginCatalogResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PluginCreateRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireSuperAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.create(
                currentUser.getFactoryId(), currentUser.getName(), request));
    }

    @PostMapping("/{pluginKey}/versions")
    public ResponseEntity<PluginCatalogResponse> registerVersion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey,
            @RequestBody PluginVersionRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireSuperAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.registerVersion(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey, request));
    }

    @PostMapping("/{pluginKey}/enable")
    public ResponseEntity<PluginCatalogResponse> enable(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey,
            @RequestBody(required = false) PluginActivationRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.enable(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey, request));
    }

    @PostMapping("/{pluginKey}/install")
    public ResponseEntity<PluginCatalogResponse> install(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.install(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey));
    }

    @DeleteMapping("/{pluginKey}/install")
    public ResponseEntity<PluginCatalogResponse> uninstall(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.uninstall(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey));
    }

    @PostMapping("/{pluginKey}/disable")
    public ResponseEntity<PluginCatalogResponse> disable(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.disable(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey));
    }

    @PostMapping("/{pluginKey}/versions/{version}/activate")
    public ResponseEntity<PluginCatalogResponse> activateVersion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey,
            @PathVariable String version
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.activateVersion(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey, version));
    }

    @PostMapping("/{pluginKey}/rollback")
    public ResponseEntity<PluginCatalogResponse> rollback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.rollback(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey));
    }

    @PutMapping("/{pluginKey}/config")
    public ResponseEntity<PluginCatalogResponse> updateConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey,
            @RequestBody PluginConfigRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.updateConfig(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey, request));
    }

    @PostMapping("/{pluginKey}/health/refresh")
    public ResponseEntity<PluginCatalogResponse> refreshHealth(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String pluginKey
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(pluginControlService.refreshHealth(
                currentUser.getFactoryId(), currentUser.getName(), pluginKey));
    }

    @GetMapping("/runtime-manifest")
    public ResponseEntity<List<PluginRuntimeManifestResponse>> runtimeManifest(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(pluginControlService.runtimeManifest(
                currentUser.getFactoryId(), currentUser.getRole().name(), currentUser.getUserId()));
    }
}
