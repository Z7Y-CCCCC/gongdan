package com.heihu.workorder.dto;

import com.heihu.workorder.model.WorkOrderReportImage;
import java.time.LocalDateTime;

public class WorkOrderReportImageResponse {
    private Long id;
    private String originalFileName;
    private String accessPath;
    private Long fileSize;
    private String contentType;
    private LocalDateTime createdAt;

    public static WorkOrderReportImageResponse from(WorkOrderReportImage image) {
        WorkOrderReportImageResponse response = new WorkOrderReportImageResponse();
        response.setId(image.getId());
        response.setOriginalFileName(image.getOriginalFileName());
        response.setAccessPath(image.getAccessPath());
        response.setFileSize(image.getFileSize());
        response.setContentType(image.getContentType());
        response.setCreatedAt(image.getCreatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getAccessPath() { return accessPath; }
    public void setAccessPath(String accessPath) { this.accessPath = accessPath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
