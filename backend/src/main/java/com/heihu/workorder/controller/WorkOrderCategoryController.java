package com.heihu.workorder.controller;

import com.heihu.workorder.dto.WorkOrderCategoryRequest;
import com.heihu.workorder.dto.WorkOrderCategoryResponse;
import com.heihu.workorder.service.WorkOrderCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/work-orders/categories")
public class WorkOrderCategoryController {

    @Autowired
    private WorkOrderCategoryService categoryService;

    @Autowired
    private com.heihu.workorder.service.TokenService tokenService;

    @GetMapping
    public ResponseEntity<List<WorkOrderCategoryResponse>> getCategories(@RequestHeader(value = "Authorization", required = false) String authorization) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(categoryService.getCategories(currentUser.getFactoryId()));
    }

    @PostMapping
    public ResponseEntity<WorkOrderCategoryResponse> createCategory(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody WorkOrderCategoryRequest request) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(categoryService.createCategory(currentUser.getFactoryId(), currentUser.getUserId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Long id) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        categoryService.deleteCategory(currentUser.getFactoryId(), id);
        return ResponseEntity.noContent().build();
    }
}
