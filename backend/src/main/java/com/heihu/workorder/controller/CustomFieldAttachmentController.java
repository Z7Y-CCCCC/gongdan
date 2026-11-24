package com.heihu.workorder.controller;

import com.heihu.workorder.dto.CustomFieldAttachmentResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.CustomFieldAttachmentService;
import com.heihu.workorder.service.CustomFieldAttachmentService.AttachmentDownload;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.util.AttachmentMediaTypes;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/custom-field-attachments")
public class CustomFieldAttachmentController {

    private final CustomFieldAttachmentService attachmentService;
    private final TokenService tokenService;

    public CustomFieldAttachmentController(CustomFieldAttachmentService attachmentService, TokenService tokenService) {
        this.attachmentService = attachmentService;
        this.tokenService = tokenService;
    }

    @PostMapping
    public CustomFieldAttachmentResponse upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) String fieldType
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return attachmentService.upload(
                currentUser.getFactoryId(),
                currentUser.getUserId(),
                scope,
                fieldId,
                fieldType,
                file
        );
    }

    @GetMapping("/{attachmentId}/file")
    public ResponseEntity<Resource> getFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long attachmentId
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        AttachmentDownload download = attachmentService.getDownload(currentUser.getFactoryId(), attachmentId);
        return ResponseEntity.ok()
                .contentType(AttachmentMediaTypes.forDownload(download.getAttachment().getContentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ("image".equals(download.getAttachment().getFieldType())
                        ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(download.getAttachment().getOriginalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.getResource());
    }
}
