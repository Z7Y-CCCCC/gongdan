package com.heihu.workorder.controller;

import com.heihu.workorder.dto.ProductCategoryRequest;
import com.heihu.workorder.dto.ProductCategoryResponse;
import com.heihu.workorder.service.ProductCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products/categories")
public class ProductCategoryController {

    @Autowired
    private ProductCategoryService categoryService;

    @Autowired
    private com.heihu.workorder.service.TokenService tokenService;

    @GetMapping
    public ResponseEntity<List<ProductCategoryResponse>> getCategories(@RequestHeader(value = "Authorization", required = false) String authorization) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(categoryService.getCategories(currentUser.getFactoryId()));
    }

    @PostMapping
    public ResponseEntity<ProductCategoryResponse> createCategory(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody ProductCategoryRequest request) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(categoryService.createCategory(currentUser.getFactoryId(), currentUser.getUserId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Long id) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        categoryService.deleteCategory(currentUser.getFactoryId(), id);
        return ResponseEntity.noContent().build();
    }
}
