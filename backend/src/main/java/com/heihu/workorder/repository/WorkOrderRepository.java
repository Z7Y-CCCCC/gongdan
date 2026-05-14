package com.heihu.workorder.repository;

import com.heihu.workorder.model.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Page<WorkOrder> findByFactoryId(Long factoryId, Pageable pageable);

    Page<WorkOrder> findByFactoryIdAndCodeContainingAndProductNameContaining(Long factoryId, String code, String productName, Pageable pageable);

    Optional<WorkOrder> findByFactoryIdAndCode(Long factoryId, String code);
}
