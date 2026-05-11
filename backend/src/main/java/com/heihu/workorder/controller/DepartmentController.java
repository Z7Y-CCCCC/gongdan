package com.heihu.workorder.controller;

import com.heihu.workorder.dto.CreateDepartmentRequest;
import com.heihu.workorder.dto.DepartmentResponse;
import com.heihu.workorder.dto.UpdateDepartmentRequest;
import com.heihu.workorder.service.DepartmentService;
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
@CrossOrigin(origins = "*")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    private Long extractFactoryId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String idStr = token.substring(7);
            try {
                return Long.parseLong(idStr);
            } catch (NumberFormatException ignored) {}
        }
        return 1L; // Fallback to 1L for simplicity if token is invalid
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listDepartments(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long factoryId = extractFactoryId(token);
        // Page index starts from 0 in Spring Data
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DepartmentResponse> departments = departmentService.listDepartments(factoryId, code, name, pageRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("items", departments.getContent());
        response.put("total", departments.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody CreateDepartmentRequest request
    ) {
        Long factoryId = extractFactoryId(token);
        String creatorName = "管理员"; // In a real app, extract from token
        DepartmentResponse response = departmentService.createDepartment(factoryId, creatorName, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        Long factoryId = extractFactoryId(token);
        DepartmentResponse response = departmentService.updateDepartment(factoryId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id
    ) {
        Long factoryId = extractFactoryId(token);
        departmentService.deleteDepartment(factoryId, id);
        return ResponseEntity.ok().build();
    }
}
