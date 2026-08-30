package com.heihu.workorder.controller;

import com.heihu.workorder.dto.NotificationListResponse;
import com.heihu.workorder.dto.NotificationResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.NotificationService;
import com.heihu.workorder.service.TokenService;
import java.util.Collections;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final TokenService tokenService;

    public NotificationController(NotificationService notificationService, TokenService tokenService) {
        this.notificationService = notificationService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public NotificationListResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "active") String scope,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "") String eventType,
            @RequestParam(defaultValue = "") String priority,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return notificationService.list(
                tokenService.requireUser(authorization),
                scope,
                unreadOnly,
                eventType,
                priority,
                keyword,
                page,
                size
        );
    }

    @GetMapping("/unread-count")
    public long unreadCount(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return notificationService.unreadCount(tokenService.requireUser(authorization));
    }

    @PutMapping("/{id}/read")
    public NotificationResponse read(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        return notificationService.markRead(tokenService.requireUser(authorization), id);
    }

    @PutMapping("/{id}/unread")
    public NotificationResponse unread(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        return notificationService.markUnread(tokenService.requireUser(authorization), id);
    }

    @PutMapping("/{id}/archive")
    public NotificationResponse archive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        return notificationService.archive(tokenService.requireUser(authorization), id);
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> readAll(@RequestHeader(value = "Authorization", required = false) String authorization) {
        int updated = notificationService.markAllRead(tokenService.requireUser(authorization));
        return ResponseEntity.ok(Collections.singletonMap("updated", updated));
    }
}
