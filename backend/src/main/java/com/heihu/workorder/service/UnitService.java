package com.heihu.workorder.service;

import com.heihu.workorder.dto.UnitRequest;
import com.heihu.workorder.dto.UnitResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UnitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UnitService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UnitRepository unitRepository;
    private final FactoryRepository factoryRepository;
    private final ProductRepository productRepository;

    public UnitService(UnitRepository unitRepository, FactoryRepository factoryRepository, ProductRepository productRepository) {
        this.unitRepository = unitRepository;
        this.factoryRepository = factoryRepository;
        this.productRepository = productRepository;
    }

    private UnitResponse toResponse(Unit u) {
        UnitResponse r = new UnitResponse();
        r.setId(u.getId());
        r.setName(u.getName());
        r.setRemark(u.getRemark());
        r.setCreatedAt(u.getCreatedAt() != null ? u.getCreatedAt().format(FMT) : null);
        return r;
    }

    @Cacheable(cacheNames = "units.list", key = "#factoryId + ':' + #name + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<UnitResponse> list(Long factoryId, String name, Pageable pageable) {
        Page<Unit> page;
        if (name != null && !name.trim().isEmpty()) {
            page = unitRepository.findByFactoryIdAndNameContaining(factoryId, name, pageable);
        } else {
            page = unitRepository.findByFactoryId(factoryId, pageable);
        }
        return page.map(this::toResponse);
    }

    @CacheEvict(cacheNames = {"units.list", "products.list"}, allEntries = true)
    public UnitResponse create(Long factoryId, UnitRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("单位名称不能为空");
        }
        unitRepository.findByFactoryIdAndName(factoryId, req.getName()).ifPresent(u -> {
            throw new IllegalArgumentException("单位名称已存在：" + req.getName());
        });
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        Unit unit = new Unit();
        unit.setFactory(factory);
        unit.setName(req.getName().trim());
        unit.setRemark(req.getRemark());
        unit.setCreatedAt(LocalDateTime.now());
        return toResponse(unitRepository.save(unit));
    }

    @CacheEvict(cacheNames = {"units.list", "products.list"}, allEntries = true)
    public UnitResponse update(Long factoryId, Long id, UnitRequest req) {
        Unit unit = unitRepository.findById(id)
                .filter(u -> u.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("单位不存在"));
        if (req.getName() != null && !req.getName().trim().isEmpty()) {
            unitRepository.findByFactoryIdAndName(factoryId, req.getName()).ifPresent(u -> {
                if (!u.getId().equals(id)) throw new IllegalArgumentException("单位名称已存在：" + req.getName());
            });
            unit.setName(req.getName().trim());
        }
        if (req.getRemark() != null) {
            unit.setRemark(req.getRemark());
        }
        return toResponse(unitRepository.save(unit));
    }

    @CacheEvict(cacheNames = {"units.list", "products.list"}, allEntries = true)
    public void delete(Long factoryId, Long id) {
        Unit unit = unitRepository.findById(id)
                .filter(u -> u.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("单位不存在"));
        if (productRepository.existsByFactoryIdAndUnitId(factoryId, id)) {
            throw new IllegalArgumentException("单位已被产品引用，不能删除");
        }
        unitRepository.delete(unit);
    }
}
