package com.heihu.workorder.service;

import com.heihu.workorder.dto.SupplierRequest;
import com.heihu.workorder.dto.SupplierResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Supplier;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SupplierService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SupplierRepository supplierRepository;
    private final FactoryRepository factoryRepository;

    public SupplierService(SupplierRepository supplierRepository, FactoryRepository factoryRepository) {
        this.supplierRepository = supplierRepository;
        this.factoryRepository = factoryRepository;
    }

    public Page<SupplierResponse> list(Long factoryId, String code, String name, Pageable pageable) {
        Page<Supplier> page;
        if (StringUtils.hasText(code)) {
            page = supplierRepository.findByFactoryIdAndCodeContaining(factoryId, code.trim(), pageable);
        } else if (StringUtils.hasText(name)) {
            page = supplierRepository.findByFactoryIdAndNameContaining(factoryId, name.trim(), pageable);
        } else {
            page = supplierRepository.findByFactoryId(factoryId, pageable);
        }
        return page.map(this::toResponse);
    }

    public SupplierResponse create(Long factoryId, SupplierRequest request) {
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
        Supplier supplier = new Supplier();
        supplier.setFactory(factory);
        supplier.setCode(code);
        applyRequest(supplier, request);
        supplier.setEnabled(request.getEnabled() == null || request.getEnabled());
        supplier.setCreatedAt(now);
        supplier.setUpdatedAt(now);
        return toResponse(supplierRepository.save(supplier));
    }

    public SupplierResponse update(Long factoryId, Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        requireName(request);
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = supplier.getCode();
        }
        ensureUniqueCode(factoryId, code, id);
        ensureUniqueName(factoryId, request.getName().trim(), id);

        supplier.setCode(code);
        applyRequest(supplier, request);
        if (request.getEnabled() != null) {
            supplier.setEnabled(request.getEnabled());
        }
        supplier.setUpdatedAt(LocalDateTime.now());
        return toResponse(supplierRepository.save(supplier));
    }

    public void delete(Long factoryId, Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        supplierRepository.delete(supplier);
    }

    private void applyRequest(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.getName().trim());
        supplier.setContactName(normalizeText(request.getContactName()));
        supplier.setPhone(normalizeText(request.getPhone()));
        supplier.setAddress(normalizeText(request.getAddress()));
        supplier.setRemark(normalizeText(request.getRemark()));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        SupplierResponse response = new SupplierResponse();
        response.setId(supplier.getId());
        response.setCode(supplier.getCode());
        response.setName(supplier.getName());
        response.setContactName(supplier.getContactName());
        response.setPhone(supplier.getPhone());
        response.setAddress(supplier.getAddress());
        response.setRemark(supplier.getRemark());
        response.setEnabled(supplier.isEnabled());
        response.setCreatedAt(supplier.getCreatedAt() != null ? supplier.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(supplier.getUpdatedAt() != null ? supplier.getUpdatedAt().format(FMT) : null);
        return response;
    }

    private void requireName(SupplierRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("供应商名称不能为空");
        }
    }

    private void ensureUniqueCode(Long factoryId, String code, Long currentId) {
        supplierRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("供应商编码已存在：" + code);
            }
        });
    }

    private void ensureUniqueName(Long factoryId, String name, Long currentId) {
        supplierRepository.findByFactoryIdAndName(factoryId, name).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("供应商名称已存在：" + name);
            }
        });
    }

    private String generateCode() {
        return "SUP-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
