package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.CustomFieldRequest;
import com.heihu.workorder.dto.CustomFieldResponse;
import com.heihu.workorder.model.CustomField;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.CustomFieldRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CustomFieldService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CustomFieldRepository customFieldRepository;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;

    public CustomFieldService(CustomFieldRepository customFieldRepository,
                              FactoryRepository factoryRepository,
                              UserRepository userRepository) {
        this.customFieldRepository = customFieldRepository;
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
    }

    public List<CustomFieldResponse> list(Long factoryId, String scope) {
        String scopeName = normalize(scope) != null ? scope.trim() : "workorder";
        return customFieldRepository.findByFactoryIdAndScopeOrderBySortOrderAscCreatedAtAsc(factoryId, scopeName)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CustomFieldResponse create(Long factoryId, Long userId, CustomFieldRequest request) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        String scope = normalize(request.getScope()) != null ? request.getScope().trim() : "workorder";
        validate(factoryId, null, scope, request);

        CustomField field = new CustomField();
        field.setFactory(factory);
        field.setScope(scope);
        applyRequest(field, request);
        field.setCreatedAt(LocalDateTime.now());
        field.setUpdatedAt(LocalDateTime.now());
        if (userId != null) {
            userRepository.findById(userId).ifPresent(field::setCreator);
        }
        return toResponse(customFieldRepository.save(field));
    }

    public CustomFieldResponse update(Long factoryId, Long userId, Long id, CustomFieldRequest request) {
        CustomField field = customFieldRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("自定义字段不存在"));
        String scope = normalize(request.getScope()) != null ? request.getScope().trim() : field.getScope();
        validate(factoryId, id, scope, request);
        field.setScope(scope);
        applyRequest(field, request);
        field.setUpdatedAt(LocalDateTime.now());
        return toResponse(customFieldRepository.save(field));
    }

    public void delete(Long factoryId, Long id) {
        CustomField field = customFieldRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("自定义字段不存在"));
        customFieldRepository.delete(field);
    }

    private void validate(Long factoryId, Long id, String scope, CustomFieldRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("字段名称不能为空");
        }
        if (request.getFieldType() == null || request.getFieldType().trim().isEmpty()) {
            throw new IllegalArgumentException("字段类型不能为空");
        }
        customFieldRepository.findByFactoryIdAndScopeAndName(factoryId, scope, request.getName().trim())
                .ifPresent(found -> {
                    if (id == null || !found.getId().equals(id)) {
                        throw new IllegalArgumentException("字段名称已存在：" + request.getName().trim());
                    }
                });
    }

    private void applyRequest(CustomField field, CustomFieldRequest request) {
        field.setName(request.getName().trim());
        field.setFieldType(request.getFieldType().trim());
        field.setPlaceholder(normalize(request.getPlaceholder()));
        field.setDisplayMode(normalize(request.getDisplayMode()));
        field.setRequired(request.getRequired() != null ? request.getRequired() : Boolean.FALSE);
        field.setAllowUserOptions(request.getAllowUserOptions() != null ? request.getAllowUserOptions() : Boolean.FALSE);
        field.setHelperText(normalize(request.getHelperText()));
        field.setDescription(normalize(request.getDescription()));
        field.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        field.setOptionsJson(writeJson(request.getOptions()));
        field.setDefaultValueJson(writeJson(request.getDefaultValues()));
    }

    private CustomFieldResponse toResponse(CustomField field) {
        CustomFieldResponse response = new CustomFieldResponse();
        response.setId(field.getId());
        response.setScope(field.getScope());
        response.setName(field.getName());
        response.setFieldType(field.getFieldType());
        response.setOptions(readOptions(field.getOptionsJson()));
        response.setDefaultValues(readDefaultValues(field.getDefaultValueJson()));
        response.setPlaceholder(field.getPlaceholder());
        response.setDisplayMode(field.getDisplayMode());
        response.setRequired(field.getRequired());
        response.setAllowUserOptions(field.getAllowUserOptions());
        response.setHelperText(field.getHelperText());
        response.setDescription(field.getDescription());
        response.setSortOrder(field.getSortOrder());
        response.setCreator(field.getCreator() != null ? field.getCreator().getName() : null);
        response.setCreatedAt(field.getCreatedAt() != null ? field.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(field.getUpdatedAt() != null ? field.getUpdatedAt().format(FMT) : null);
        return response;
    }

    private String writeJson(Object value) {
        if (value == null) return "[]";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<CustomFieldRequest.OptionItem> readOptions(String json) {
        if (normalize(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<CustomFieldRequest.OptionItem>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private List<String> readDefaultValues(String json) {
        if (normalize(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
