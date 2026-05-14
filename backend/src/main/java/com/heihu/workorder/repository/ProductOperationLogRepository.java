package com.heihu.workorder.repository;

import com.heihu.workorder.model.ProductOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOperationLogRepository extends JpaRepository<ProductOperationLog, Long> {

    Page<ProductOperationLog> findByFactoryId(Long factoryId, Pageable pageable);
}
