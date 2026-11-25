package com.heihu.workorder.controller;

import com.heihu.workorder.dto.WorkOrderReportImageResponse;
import com.heihu.workorder.dto.WorkOrderReportRequest;
import com.heihu.workorder.dto.WorkOrderReportResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.service.WorkOrderReportService;
import com.heihu.workorder.service.WorkOrderReportService.ReportImageDownload;
import com.heihu.workorder.util.AttachmentMediaTypes;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mobile/reports")
public class MobileReportController {

    private final WorkOrderReportService reportService;
    private final TokenService tokenService;

    public MobileReportController(WorkOrderReportService reportService, TokenService tokenService) {
        this.reportService = reportService;
        this.tokenService = tokenService;
    }

    @PostMapping("/images")
    public WorkOrderReportImageResponse uploadImage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return reportService.uploadImage(currentUser.getFactoryId(), currentUser.getUserId(), file);
    }

    @GetMapping("/images/{imageId}/file")
    public ResponseEntity<Resource> getImage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long imageId
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        ReportImageDownload download = reportService.getImageDownload(currentUser.getFactoryId(), currentUser.getUserId(), imageId);
        return ResponseEntity.ok()
                .contentType(AttachmentMediaTypes.forDownload(download.getImage().getContentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.getImage().getOriginalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.getResource());
    }

    @PostMapping
    public WorkOrderReportResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody WorkOrderReportRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return reportService.createReport(currentUser.getFactoryId(), currentUser.getUserId(), request);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "false") boolean includeSubmitted,
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        Page<WorkOrderReportResponse> result = reportService.listMobile(
                currentUser.getFactoryId(),
                currentUser.getUserId(),
                includeSubmitted,
                workOrderId,
                status,
                period,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }
}
