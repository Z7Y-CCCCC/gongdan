package com.heihu.plugins.quality;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
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
 * iframe mounting, health checks and rollback. Replace this in a real customer
 * plugin with its own database and domain services.
 */
@RestController
@RequestMapping("/api/plugin/quality-management")
public class QualityManagementController {

    private final AtomicLong sequence = new AtomicLong(1000);
    private final Map<Long, QualityRecord> records = new ConcurrentHashMap<>();

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
        List<QualityRecord> result = new ArrayList<>();
        for (QualityRecord record : records.values()) {
            if (factoryId.equals(record.getFactoryId())) result.add(record);
        }
        result.sort(Comparator.comparing(QualityRecord::getCreatedAt).reversed());
        return result;
    }

    @PostMapping("/records")
    public ResponseEntity<QualityRecord> create(
            @RequestHeader("X-Plugin-Factory-Id") String factoryId,
            @RequestHeader("X-Plugin-User-Id") String userId,
            @Valid @RequestBody QualityRecordRequest request) {
        QualityRecord record = new QualityRecord();
        record.setId(sequence.incrementAndGet());
        record.setFactoryId(factoryId);
        record.setOperatorUserId(userId);
        record.setIssue(request.getIssue().trim());
        record.setStatus(request.getStatus().trim());
        record.setRemark(request.getRemark() == null ? "" : request.getRemark().trim());
        record.setCreatedAt(LocalDateTime.now().toString());
        records.put(record.getId(), record);
        return ResponseEntity.status(HttpStatus.CREATED).body(record);
    }

    public static class QualityRecordRequest {
        @NotBlank(message = "质量问题不能为空")
        private String issue;

        @NotBlank(message = "状态不能为空")
        private String status;

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
