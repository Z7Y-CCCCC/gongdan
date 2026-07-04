package com.heihu.workorder.controller;

import com.heihu.workorder.dto.CreateDepartmentRequest;
import com.heihu.workorder.dto.DepartmentResponse;
import com.heihu.workorder.dto.UpdateDepartmentRequest;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.DepartmentService;
import com.heihu.workorder.service.TokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final TokenService tokenService;

    public DepartmentController(DepartmentService departmentService, TokenService tokenService) {
        this.departmentService = departmentService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listDepartments(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        // Page index starts from 0 in Spring Data
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DepartmentResponse> departments = departmentService.listDepartments(currentUser.getFactoryId(), code, name, pageRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("items", departments.getContent());
        response.put("total", departments.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateDepartmentRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        Long factoryId = currentUser.getFactoryId();
        String creatorName = currentUser.getName();
        DepartmentResponse response = departmentService.createDepartment(factoryId, creatorName, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        Long factoryId = currentUser.getFactoryId();
        DepartmentResponse response = departmentService.updateDepartment(factoryId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        Long factoryId = currentUser.getFactoryId();
        departmentService.deleteDepartment(factoryId, id);
        return ResponseEntity.ok().build();
    }
}
