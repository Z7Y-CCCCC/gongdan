package com.heihu.workorder.service;

import com.heihu.workorder.dto.DefectItemRequest;
import com.heihu.workorder.dto.DefectItemResponse;
import com.heihu.workorder.model.DefectItem;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.DefectItemRepository;
import com.heihu.workorder.repository.FactoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class DefectItemService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DefectItemRepository defectItemRepository;
    private final FactoryRepository factoryRepository;

    public DefectItemService(DefectItemRepository defectItemRepository, FactoryRepository factoryRepository) {
        this.defectItemRepository = defectItemRepository;
        this.factoryRepository = factoryRepository;
    }

    private DefectItemResponse toResponse(DefectItem d) {
        DefectItemResponse r = new DefectItemResponse();
        r.setId(d.getId());
        r.setCode(d.getCode());
        r.setName(d.getName());
        r.setCreatedAt(d.getCreatedAt() != null ? d.getCreatedAt().format(FMT) : null);
        return r;
    }

    public Page<DefectItemResponse> list(Long factoryId, String code, String name, Pageable pageable) {
        Page<DefectItem> page;
        boolean hasCode = code != null && !code.trim().isEmpty();
        boolean hasName = name != null && !name.trim().isEmpty();
        if (hasCode && hasName) {
            page = defectItemRepository.findByFactoryIdAndCodeContainingAndNameContaining(factoryId, code, name, pageable);
        } else if (hasCode) {
            page = defectItemRepository.findByFactoryIdAndCodeContaining(factoryId, code, pageable);
        } else if (hasName) {
            page = defectItemRepository.findByFactoryIdAndNameContaining(factoryId, name, pageable);
        } else {
            page = defectItemRepository.findByFactoryId(factoryId, pageable);
        }
        return page.map(this::toResponse);
    }

    public DefectItemResponse create(Long factoryId, DefectItemRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("不良品项名称不能为空");
        }
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        String code = (req.getCode() != null && !req.getCode().trim().isEmpty())
                ? req.getCode().trim()
                : UUID.randomUUID().toString().substring(0, 8);
        defectItemRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(d -> {
            throw new IllegalArgumentException("不良品项编号已存在：" + code);
        });
        DefectItem item = new DefectItem();
        item.setFactory(factory);
        item.setCode(code);
        item.setName(req.getName().trim());
        item.setCreatedAt(LocalDateTime.now());
        return toResponse(defectItemRepository.save(item));
    }

    public DefectItemResponse update(Long factoryId, Long id, DefectItemRequest req) {
        DefectItem item = defectItemRepository.findById(id)
                .filter(d -> d.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("不良品项不存在"));
        if (req.getName() != null && !req.getName().trim().isEmpty()) {
            item.setName(req.getName().trim());
        }
        if (req.getCode() != null && !req.getCode().trim().isEmpty()) {
            defectItemRepository.findByFactoryIdAndCode(factoryId, req.getCode()).ifPresent(d -> {
                if (!d.getId().equals(id)) throw new IllegalArgumentException("不良品项编号已存在：" + req.getCode());
            });
            item.setCode(req.getCode().trim());
        }
        return toResponse(defectItemRepository.save(item));
    }

    public void delete(Long factoryId, Long id) {
        DefectItem item = defectItemRepository.findById(id)
                .filter(d -> d.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("不良品项不存在"));
        defectItemRepository.delete(item);
    }
}
