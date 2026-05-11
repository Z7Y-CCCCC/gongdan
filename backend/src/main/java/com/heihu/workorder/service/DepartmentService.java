package com.heihu.workorder.service;

import com.heihu.workorder.dto.CreateDepartmentRequest;
import com.heihu.workorder.dto.DepartmentResponse;
import com.heihu.workorder.dto.UpdateDepartmentRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Department;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.DepartmentRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;

    public DepartmentService(DepartmentRepository departmentRepository, FactoryRepository factoryRepository, UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<DepartmentResponse> listDepartments(Long factoryId, String code, String name, Pageable pageable) {
        Page<Department> departments;
        boolean hasCode = StringUtils.hasText(code);
        boolean hasName = StringUtils.hasText(name);

        if (hasCode && hasName) {
            departments = departmentRepository.findByFactoryIdAndCodeContainingAndNameContaining(factoryId, code.trim(), name.trim(), pageable);
        } else if (hasCode) {
            departments = departmentRepository.findByFactoryIdAndCodeContaining(factoryId, code.trim(), pageable);
        } else if (hasName) {
            departments = departmentRepository.findByFactoryIdAndNameContaining(factoryId, name.trim(), pageable);
        } else {
            departments = departmentRepository.findByFactoryId(factoryId, pageable);
        }

        return departments.map(this::toResponse);
    }

    @Transactional
    public DepartmentResponse createDepartment(Long factoryId, String creatorName, CreateDepartmentRequest request) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工厂不存在"));

        String name = request.getName().trim();
        if (departmentRepository.findByFactoryIdAndName(factoryId, name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该部门名称已存在");
        }

        String code = request.getCode();
        if (!StringUtils.hasText(code)) {
            // Auto generate code if not provided
            code = "DEPT-" + System.currentTimeMillis();
        } else {
            code = code.trim();
            if (departmentRepository.findByFactoryIdAndCode(factoryId, code).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "该部门编码已存在");
            }
        }

        Department department = new Department();
        department.setFactory(factory);
        department.setCode(code);
        department.setName(name);
        department.setCreatedBy(creatorName);
        department.setCreatedAt(LocalDateTime.now());
        
        Department saved = departmentRepository.save(department);

        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            updateUsersDepartment(factoryId, saved, request.getUserIds());
        }

        return toResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long factoryId, Long id, UpdateDepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .filter(d -> d.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "部门不存在"));

        String name = request.getName().trim();
        if (!department.getName().equals(name) && departmentRepository.findByFactoryIdAndName(factoryId, name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该部门名称已存在");
        }

        String code = request.getCode();
        if (StringUtils.hasText(code)) {
            code = code.trim();
            if (!department.getCode().equals(code) && departmentRepository.findByFactoryIdAndCode(factoryId, code).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "该部门编码已存在");
            }
            department.setCode(code);
        }

        department.setName(name);
        Department saved = departmentRepository.save(department);

        if (request.getUserIds() != null) {
            updateUsersDepartment(factoryId, saved, request.getUserIds());
        }

        return toResponse(saved);
    }

    @Transactional
    public void deleteDepartment(Long factoryId, Long id) {
        Department department = departmentRepository.findById(id)
                .filter(d -> d.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "部门不存在"));

        // Clear department from users
        List<FactoryUser> users = userRepository.findByDepartmentId(id);
        for (FactoryUser user : users) {
            user.setDepartment(null);
            userRepository.save(user);
        }

        departmentRepository.delete(department);
    }

    private void updateUsersDepartment(Long factoryId, Department department, List<Long> userIds) {
        // Clear existing users from this department
        List<FactoryUser> existingUsers = userRepository.findByDepartmentId(department.getId());
        for (FactoryUser user : existingUsers) {
            user.setDepartment(null);
            userRepository.save(user);
        }

        // Add new users
        if (!userIds.isEmpty()) {
            for (Long userId : userIds) {
                userRepository.findByIdAndFactoryId(userId, factoryId).ifPresent(user -> {
                    user.setDepartment(department);
                    userRepository.save(user);
                });
            }
        }
    }

    private DepartmentResponse toResponse(Department department) {
        DepartmentResponse res = new DepartmentResponse();
        res.setId(department.getId());
        res.setCode(department.getCode());
        res.setName(department.getName());
        res.setCreatedBy(department.getCreatedBy());
        res.setCreatedAt(department.getCreatedAt().toString());

        List<FactoryUser> users = userRepository.findByDepartmentId(department.getId());
        String userNames = users.stream().map(FactoryUser::getName).collect(Collectors.joining("  "));
        res.setUserNames(userNames);

        return res;
    }
}
