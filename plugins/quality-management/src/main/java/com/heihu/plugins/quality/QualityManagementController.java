package com.heihu.plugins.quality;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A deliberately small standalone plugin used to verify registration, routing,
 * iframe mounting, health checks and rollback. Records are durably stored in the
 * plugin's own database; customer-specific domain workflows remain separate.
 */
@RestController
@RequestMapping("/api/plugin/quality-management")
public class QualityManagementController {

    private final QualityRecordRepository records;

    public QualityManagementController(QualityRecordRepository records) {
        this.records = records;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pluginKey", "quality-management");
        response.put("version", "1.0.0");
        response.put("status", "UP");
        response.put("time", LocalDateTime.now().toString());
        return response;
    }

    @GetMapping("/records")
    public List<QualityRecord> list(@RequestHeader("X-Plugin-Factory-Id") String factoryId) {
        return records.list(factoryId);
    }

    @PostMapping("/records")
    public ResponseEntity<QualityRecord> create(
            @RequestHeader("X-Plugin-Factory-Id") String factoryId,
            @RequestHeader("X-Plugin-User-Id") String userId,
            @Valid @RequestBody QualityRecordRequest request) {
        QualityRecord record = new QualityRecord();
        record.setFactoryId(factoryId);
        record.setOperatorUserId(userId);
        record.setIssue(request.getIssue().trim());
        record.setStatus(request.getStatus().trim());
        record.setRemark(request.getRemark() == null ? "" : request.getRemark().trim());
        record.setCreatedAt(LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(records.save(record));
    }

    public static class QualityRecordRequest {
        @NotBlank(message = "质量问题不能为空")
        @Size(max = 500, message = "质量问题不能超过 500 个字符")
        private String issue;

        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态不能超过 32 个字符")
        private String status;

        @Size(max = 500, message = "备注不能超过 500 个字符")
        private String remark;

        public String getIssue() { return issue; }
        public void setIssue(String issue) { this.issue = issue; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class QualityRecord {
        private Long id;
        private String factoryId;
        private String operatorUserId;
        private String issue;
        private String status;
        private String remark;
        private String createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFactoryId() { return factoryId; }
        public void setFactoryId(String factoryId) { this.factoryId = factoryId; }
        public String getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getIssue() { return issue; }
        public void setIssue(String issue) { this.issue = issue; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
}
