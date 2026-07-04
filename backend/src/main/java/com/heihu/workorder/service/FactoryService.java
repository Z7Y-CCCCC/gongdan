package com.heihu.workorder.service;

import com.heihu.workorder.dto.CurrentFactoryResponse;
import com.heihu.workorder.dto.UpdateFactoryRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.FactoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FactoryService {

    private final FactoryRepository factoryRepository;

    public FactoryService(FactoryRepository factoryRepository) {
        this.factoryRepository = factoryRepository;
    }

    @Transactional(readOnly = true)
    public CurrentFactoryResponse getCurrent(Long factoryId) {
        return toResponse(loadFactory(factoryId));
    }

    @Transactional
    public CurrentFactoryResponse updateCurrent(Long factoryId, UpdateFactoryRequest request) {
        Factory factory = loadFactory(factoryId);
        factory.setName(request.getName().trim());
        return toResponse(factoryRepository.save(factory));
    }

    private Factory loadFactory(Long factoryId) {
        return factoryRepository.findById(factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工厂不存在"));
    }

    private CurrentFactoryResponse toResponse(Factory factory) {
        CurrentFactoryResponse response = new CurrentFactoryResponse();
        response.setId(factory.getId());
        response.setCode(factory.getCode());
        response.setName(factory.getName());
        response.setEnabled(factory.isEnabled());
        response.setCreatedAt(factory.getCreatedAt() == null ? null : factory.getCreatedAt().toString());
        return response;
    }
}
