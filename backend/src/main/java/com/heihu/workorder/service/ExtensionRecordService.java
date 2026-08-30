package com.heihu.workorder.service;

import com.heihu.workorder.dto.ExtensionRecordRequest;
import com.heihu.workorder.dto.ExtensionRecordResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.ExtensionRecord;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.ExtensionRecordRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExtensionRecordService {

    public static final String MODULE_SOP = "SOP";
    public static final String MODULE_EQUIPMENT = "EQUIPMENT";
    public static final String MODULE_QUALITY = "QUALITY";
    public static final String MODULE_MESSAGE = "MESSAGE";
    public static final String MODULE_PAYROLL_RULE = "PAYROLL_RULE";
    public static final String MODULE_FIELD_EVENT = "FIELD_EVENT";

    private static final Set<String> ALLOWED_MODULES = new HashSet<>(Arrays.asList(
            MODULE_SOP,
            MODULE_EQUIPMENT,
            MODULE_QUALITY,
            MODULE_MESSAGE,
            MODULE_PAYROLL_RULE,
            MODULE_FIELD_EVENT
    ));
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExtensionRecordRepository recordRepository;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;

    public ExtensionRecordService(ExtensionRecordRepository recordRepository,
                                  FactoryRepository factoryRepository,
                                  UserRepository userRepository) {
        this.recordRepository = recordRepository;
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ExtensionRecordResponse> list(Long factoryId, String moduleName, String status, Pageable pageable) {
        String module = normalizeModule(moduleName);
        String normalizedStatus = normalize(status);
        Page<ExtensionRecord> page = normalizedStatus == null
                ? recordRepository.findByFactoryIdAndModuleName(factoryId, module, pageable)
                : recordRepository.findByFactoryIdAndModuleNameAndStatus(factoryId, module, normalizedStatus.toUpperCase(), pageable);
        return page.map(this::toResponse);
    }

    public ExtensionRecordResponse create(Long factoryId, Long userId, ExtensionRecordRequest request) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工厂不存在"));
        FactoryUser user = userRepository.findById(userId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
        ExtensionRecord record = new ExtensionRecord();
        record.setFactory(factory);
        record.setCreatedBy(user);
        record.setCreatedAt(LocalDateTime.now());
        apply(record, request, true);
        record.setUpdatedAt(LocalDateTime.now());
        return toResponse(recordRepository.save(record));
    }

    public ExtensionRecordResponse update(Long factoryId, Long id, ExtensionRecordRequest request) {
        ExtensionRecord record = recordRepository.findByIdAndFactoryId(id, factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "记录不存在"));
        apply(record, request, false);
        record.setUpdatedAt(LocalDateTime.now());
        return toResponse(recordRepository.save(record));
    }

    public void delete(Long factoryId, Long id) {
        ExtensionRecord record = recordRepository.findByIdAndFactoryId(id, factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "记录不存在"));
        recordRepository.delete(record);
    }

    private void apply(ExtensionRecord record, ExtensionRecordRequest request, boolean creating) {
        String title = normalize(request.getTitle());
        if (title == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        if (creating) {
            record.setModuleName(normalizeModule(request.getModuleName()));
        } else if (normalize(request.getModuleName()) != null) {
            record.setModuleName(normalizeModule(request.getModuleName()));
        }
        record.setTitle(title);
        record.setBusinessKey(limit(normalize(request.getBusinessKey()), 128));
        String status = normalize(request.getStatus());
        record.setStatus(status == null ? "ACTIVE" : limit(status.toUpperCase(), 32));
        record.setPayloadJson(limit(request.getPayloadJson(), 20000));
    }

    private ExtensionRecordResponse toResponse(ExtensionRecord record) {
        ExtensionRecordResponse response = new ExtensionRecordResponse();
        response.setId(record.getId());
        response.setModuleName(record.getModuleName());
        response.setBusinessKey(record.getBusinessKey());
        response.setTitle(record.getTitle());
        response.setStatus(record.getStatus());
        response.setPayloadJson(record.getPayloadJson());
        response.setCreatedById(record.getCreatedBy() != null ? record.getCreatedBy().getId() : null);
        response.setCreatedByName(record.getCreatedBy() != null ? record.getCreatedBy().getName() : null);
        response.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt().format(FMT) : null);
        return response;
    }

    private String normalizeModule(String value) {
        String module = normalize(value);
        if (module == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "模块不能为空");
        }
        module = module.toUpperCase();
        if (!ALLOWED_MODULES.contains(module)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的模块：" + module);
        }
        return module;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
