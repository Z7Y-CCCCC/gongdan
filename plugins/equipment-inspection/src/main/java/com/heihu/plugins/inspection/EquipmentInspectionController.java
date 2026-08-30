package com.heihu.plugins.inspection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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

@RestController
@RequestMapping("/api/plugin/equipment-inspection")
public class EquipmentInspectionController {

    private final AtomicLong sequence = new AtomicLong(2000);
    private final Map<Long, InspectionRecord> records = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public EquipmentInspectionController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping(
            @RequestHeader("X-Plugin-Factory-Id") String factoryId,
            @RequestHeader("X-Plugin-User-Id") String userId,
            @RequestHeader("X-Plugin-Role") String role,
            @RequestHeader(value = "X-Plugin-Config-Base64", required = false) String configBase64
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pluginKey", "equipment-inspection");
        response.put("version", "1.0.0");
        response.put("status", "UP");
        response.put("factoryId", Long.valueOf(factoryId));
        response.put("userId", Long.valueOf(userId));
        response.put("role", role);
        response.put("config", decodeConfig(configBase64));
        response.put("time", LocalDateTime.now().toString());
        return response;
    }

    @GetMapping("/inspections")
    public List<InspectionRecord> list(@RequestHeader("X-Plugin-Factory-Id") String factoryId) {
        List<InspectionRecord> result = new ArrayList<>();
        for (InspectionRecord record : records.values()) {
            if (factoryId.equals(record.getFactoryId())) result.add(record);
        }
        result.sort(Comparator.comparing(InspectionRecord::getCreatedAt).reversed());
        return result;
    }

    @PostMapping("/inspections")
    public ResponseEntity<InspectionRecord> create(
            @RequestHeader("X-Plugin-Factory-Id") String factoryId,
            @RequestHeader("X-Plugin-User-Id") String userId,
            @Valid @RequestBody InspectionRequest request
    ) {
        InspectionRecord record = new InspectionRecord();
        record.setId(sequence.incrementAndGet());
        record.setFactoryId(factoryId);
        record.setOperatorUserId(userId);
        record.setEquipmentCode(request.getEquipmentCode().trim());
        record.setResult(request.getResult().trim());
        record.setRemark(request.getRemark() == null ? "" : request.getRemark().trim());
        record.setCreatedAt(LocalDateTime.now().toString());
        records.put(record.getId(), record);
        return ResponseEntity.status(HttpStatus.CREATED).body(record);
    }

    private Map<String, Object> decodeConfig(String configBase64) {
        if (configBase64 == null || configBase64.trim().isEmpty()) return new LinkedHashMap<>();
        try {
            byte[] decoded = Base64.getDecoder().decode(configBase64);
            return objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            Map<String, Object> invalid = new LinkedHashMap<>();
            invalid.put("invalid", true);
            return invalid;
        }
    }

    public static class InspectionRequest {
        @NotBlank(message = "设备编号不能为空")
        @Size(max = 64, message = "设备编号不能超过 64 个字符")
        private String equipmentCode;

        @NotBlank(message = "点检结果不能为空")
        @Size(max = 32, message = "点检结果不能超过 32 个字符")
        private String result;

        @Size(max = 500, message = "备注不能超过 500 个字符")
        private String remark;

        public String getEquipmentCode() { return equipmentCode; }
        public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class InspectionRecord {
        private Long id;
        private String factoryId;
        private String operatorUserId;
        private String equipmentCode;
        private String result;
        private String remark;
        private String createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFactoryId() { return factoryId; }
        public void setFactoryId(String factoryId) { this.factoryId = factoryId; }
        public String getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getEquipmentCode() { return equipmentCode; }
        public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
}
