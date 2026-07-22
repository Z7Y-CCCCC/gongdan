package com.heihu.workorder.service;

import com.heihu.workorder.dto.ProductCategoryRequest;
import com.heihu.workorder.dto.ProductCategoryResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.ProductCategory;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.ProductCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductCategoryService {

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private FactoryRepository factoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getCategories(Long factoryId) {
        return categoryRepository.findByFactoryIdOrderByCreatedAtDesc(factoryId).stream()
                .map(c -> {
                    ProductCategoryResponse res = new ProductCategoryResponse();
                    res.setId(c.getId());
                    res.setName(c.getName());
                    if (c.getCreator() != null) {
                        res.setCreatorId(c.getCreator().getId());
                        res.setCreatorName(c.getCreator().getName());
                    }
                    res.setCreatedAt(c.getCreatedAt());
                    return res;
                }).collect(Collectors.toList());
    }

    @Transactional
    public ProductCategoryResponse createCategory(Long factoryId, Long creatorId, ProductCategoryRequest request) {
        if (categoryRepository.findByFactoryIdAndName(factoryId, request.getName()).isPresent()) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        FactoryUser creator = userRepository.findById(creatorId).orElse(null);

        ProductCategory category = new ProductCategory();
        category.setFactory(factory);
        category.setName(request.getName());
        category.setCreator(creator);
        category.setCreatedAt(LocalDateTime.now());

        ProductCategory saved = categoryRepository.save(category);

        ProductCategoryResponse res = new ProductCategoryResponse();
        res.setId(saved.getId());
        res.setName(saved.getName());
        if (saved.getCreator() != null) {
            res.setCreatorId(saved.getCreator().getId());
            res.setCreatorName(saved.getCreator().getName());
        }
        res.setCreatedAt(saved.getCreatedAt());
        return res;
    }

    @Transactional
    public void deleteCategory(Long factoryId, Long id) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        if (!category.getFactory().getId().equals(factoryId)) {
            throw new IllegalArgumentException("无权操作");
        }
        categoryRepository.delete(category);
    }
}
