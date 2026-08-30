package com.heihu.workorder.dto;

import com.heihu.workorder.model.CustomFieldAttachment;
import java.time.LocalDateTime;

public class CustomFieldAttachmentResponse {
    private Long id;
    private String originalFileName;
    private String accessPath;
    private Long fileSize;
    private String contentType;
    private LocalDateTime createdAt;

    public static CustomFieldAttachmentResponse from(CustomFieldAttachment attachment) {
        CustomFieldAttachmentResponse response = new CustomFieldAttachmentResponse();
        response.setId(attachment.getId());
        response.setOriginalFileName(attachment.getOriginalFileName());
        response.setAccessPath(attachment.getAccessPath());
        response.setFileSize(attachment.getFileSize());
        response.setContentType(attachment.getContentType());
        response.setCreatedAt(attachment.getCreatedAt());
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
