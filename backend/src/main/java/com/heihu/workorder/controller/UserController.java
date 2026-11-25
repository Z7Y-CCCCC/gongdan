package com.heihu.workorder.controller;

import com.heihu.workorder.dto.CreateUserRequest;
import com.heihu.workorder.dto.ResetPasswordResponse;
import com.heihu.workorder.dto.ResetPasswordRequest;
import com.heihu.workorder.dto.UpdatePermissionRequest;
import com.heihu.workorder.dto.UpdateStatusRequest;
import com.heihu.workorder.dto.UpdateUserRequest;
import com.heihu.workorder.dto.UserResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.service.UserService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;

    public UserController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public List<UserResponse> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return userService.listUsers(currentUser.getFactoryId());
    }

    @PostMapping
    public UserResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateUserRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return userService.createUser(currentUser.getFactoryId(), request);
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return userService.updateUser(currentUser.getFactoryId(), currentUser.getUserId(), id, request);
    }

    @PatchMapping("/{id}/password/reset")
    public ResetPasswordResponse resetPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ResetPasswordRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        String password = request == null ? null : request.getPassword();
        return userService.resetPassword(currentUser.getFactoryId(), id, password);
    }

    @PatchMapping("/{id}/phone/unbind")
    public UserResponse unbindPhone(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return userService.unbindPhone(currentUser.getFactoryId(), id);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return userService.updateStatus(currentUser.getFactoryId(), currentUser.getUserId(), id, request.getEnabled());
    }

    @PatchMapping("/{id}/permission")
    public UserResponse updatePermission(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePermissionRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return userService.updatePermission(currentUser.getFactoryId(), currentUser.getUserId(), id, request.getRole());
    }

    @GetMapping("/export")
    public void exportUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "true") boolean includePasswordHash,
            HttpServletResponse response
    ) throws IOException {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        String csv = userService.exportUsersCsv(currentUser.getFactoryId(), includePasswordHash);
        response.setContentType("text/csv; charset=UTF-8");
        // The explicit backup may contain password hashes; never let an intermediary cache it.
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=users_export.csv");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    @PostMapping("/import")
    public UserService.ImportResult importUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (csvContent.startsWith("\uFEFF")) {
            csvContent = csvContent.substring(1);
        }
        return userService.importUsersCsv(currentUser.getFactoryId(), csvContent);
    }
}
