package com.heihu.workorder.service;

import com.heihu.workorder.dto.WorkOrderCategoryRequest;
import com.heihu.workorder.dto.WorkOrderCategoryResponse;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.WorkOrderCategory;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderCategoryRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkOrderCategoryService {

    @Autowired
    private WorkOrderCategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public List<WorkOrderCategoryResponse> getCategories(Long factoryId) {
        return categoryRepository.findByFactoryIdOrderByIdAsc(factoryId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkOrderCategoryResponse createCategory(Long factoryId, Long userId, WorkOrderCategoryRequest request) {
        FactoryUser user = userRepository.findByIdAndFactoryId(userId, factoryId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (categoryRepository.findByFactoryIdAndName(factoryId, request.getName()).isPresent()) {
            throw new RuntimeException("Category already exists");
        }

        WorkOrderCategory category = new WorkOrderCategory();
        category.setFactory(user.getFactory());
        category.setName(request.getName());
        category.setCreator(user);
        category.setCreatedAt(LocalDateTime.now());

        category = categoryRepository.save(category);
        return mapToResponse(category);
    }

    @Transactional
    public void deleteCategory(Long factoryId, Long categoryId) {
        WorkOrderCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        if (!category.getFactory().getId().equals(factoryId)) {
            throw new RuntimeException("Forbidden");
        }
        if (workOrderRepository.existsByFactoryIdAndCategoryId(factoryId, categoryId)) {
            throw new IllegalArgumentException("工单分类已被工单引用，不能删除");
        }
        categoryRepository.delete(category);
    }

    private WorkOrderCategoryResponse mapToResponse(WorkOrderCategory category) {
        WorkOrderCategoryResponse response = new WorkOrderCategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        if (category.getCreator() != null) {
            response.setCreatorId(category.getCreator().getId());
            response.setCreatorName(category.getCreator().getName());
        }
        response.setCreatedAt(category.getCreatedAt());
        return response;
    }
}
