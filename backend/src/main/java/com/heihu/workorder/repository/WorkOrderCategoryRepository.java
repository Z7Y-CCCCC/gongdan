package com.heihu.workorder.repository;

import com.heihu.workorder.model.WorkOrderCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderCategoryRepository extends JpaRepository<WorkOrderCategory, Long> {
    List<WorkOrderCategory> findByFactoryIdOrderByIdAsc(Long factoryId);
    Optional<WorkOrderCategory> findByFactoryIdAndName(Long factoryId, String name);
}
