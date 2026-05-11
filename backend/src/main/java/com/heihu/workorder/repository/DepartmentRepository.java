package com.heihu.workorder.repository;

import com.heihu.workorder.model.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByFactoryIdAndCode(Long factoryId, String code);

    Optional<Department> findByFactoryIdAndName(Long factoryId, String name);

    Page<Department> findByFactoryId(Long factoryId, Pageable pageable);

    Page<Department> findByFactoryIdAndCodeContainingAndNameContaining(Long factoryId, String code, String name, Pageable pageable);
    
    Page<Department> findByFactoryIdAndCodeContaining(Long factoryId, String code, Pageable pageable);
    
    Page<Department> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);
}
