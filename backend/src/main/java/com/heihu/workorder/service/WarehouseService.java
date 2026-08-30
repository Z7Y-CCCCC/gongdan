package com.heihu.workorder.service;

import com.heihu.workorder.dto.WarehouseRequest;
import com.heihu.workorder.dto.WarehouseResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Warehouse;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WarehouseService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WarehouseRepository warehouseRepository;
    private final FactoryRepository factoryRepository;

    public WarehouseService(WarehouseRepository warehouseRepository, FactoryRepository factoryRepository) {
        this.warehouseRepository = warehouseRepository;
        this.factoryRepository = factoryRepository;
    }

    @Cacheable(cacheNames = "warehouses.list", key = "#factoryId + ':' + #code + ':' + #name + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<WarehouseResponse> list(Long factoryId, String code, String name, Pageable pageable) {
        Page<Warehouse> page;
        if (StringUtils.hasText(code)) {
            page = warehouseRepository.findByFactoryIdAndCodeContaining(factoryId, code.trim(), pageable);
        } else if (StringUtils.hasText(name)) {
            page = warehouseRepository.findByFactoryIdAndNameContaining(factoryId, name.trim(), pageable);
        } else {
            page = warehouseRepository.findByFactoryId(factoryId, pageable);
        }
        return page.map(this::toResponse);
    }

    @CacheEvict(cacheNames = {"warehouses.list", "inbound.receipts.list", "outbound.receipts.list"}, allEntries = true)
    public WarehouseResponse create(Long factoryId, WarehouseRequest request) {
        requireName(request);
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = generateCode();
        }
        ensureUniqueCode(factoryId, code, null);
        ensureUniqueName(factoryId, request.getName().trim(), null);

        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        LocalDateTime now = LocalDateTime.now();
        Warehouse warehouse = new Warehouse();
        warehouse.setFactory(factory);
        warehouse.setCode(code);
        warehouse.setName(request.getName().trim());
        warehouse.setLocation(normalizeText(request.getLocation()));
        warehouse.setRemark(normalizeText(request.getRemark()));
        warehouse.setEnabled(request.getEnabled() == null || request.getEnabled());
        warehouse.setCreatedAt(now);
        warehouse.setUpdatedAt(now);
        return toResponse(warehouseRepository.save(warehouse));
    }

    @CacheEvict(cacheNames = {"warehouses.list", "inbound.receipts.list", "outbound.receipts.list"}, allEntries = true)
    public WarehouseResponse update(Long factoryId, Long id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("仓库不存在"));
        requireName(request);
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = warehouse.getCode();
        }
        ensureUniqueCode(factoryId, code, id);
        ensureUniqueName(factoryId, request.getName().trim(), id);

        warehouse.setCode(code);
        warehouse.setName(request.getName().trim());
        warehouse.setLocation(normalizeText(request.getLocation()));
        warehouse.setRemark(normalizeText(request.getRemark()));
        if (request.getEnabled() != null) {
            warehouse.setEnabled(request.getEnabled());
        }
        warehouse.setUpdatedAt(LocalDateTime.now());
        return toResponse(warehouseRepository.save(warehouse));
    }

    @CacheEvict(cacheNames = {"warehouses.list", "inbound.receipts.list", "outbound.receipts.list"}, allEntries = true)
    public void delete(Long factoryId, Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("仓库不存在"));
        warehouseRepository.delete(warehouse);
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        WarehouseResponse response = new WarehouseResponse();
        response.setId(warehouse.getId());
        response.setCode(warehouse.getCode());
        response.setName(warehouse.getName());
        response.setLocation(warehouse.getLocation());
        response.setRemark(warehouse.getRemark());
        response.setEnabled(warehouse.isEnabled());
        response.setCreatedAt(warehouse.getCreatedAt() != null ? warehouse.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(warehouse.getUpdatedAt() != null ? warehouse.getUpdatedAt().format(FMT) : null);
        return response;
    }

    private void requireName(WarehouseRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("仓库名称不能为空");
        }
    }

    private void ensureUniqueCode(Long factoryId, String code, Long currentId) {
        warehouseRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("仓库编码已存在：" + code);
            }
        });
    }

    private void ensureUniqueName(Long factoryId, String name, Long currentId) {
        warehouseRepository.findByFactoryIdAndName(factoryId, name).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("仓库名称已存在：" + name);
            }
        });
    }

    private String generateCode() {
        return "WH-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
