package com.heihu.workorder.repository;

import com.heihu.workorder.model.ProductOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProductOperationLogRepository extends JpaRepository<ProductOperationLog, Long> {

    Page<ProductOperationLog> findByFactoryId(Long factoryId, Pageable pageable);

    @Modifying
    @Query("delete from ProductOperationLog log where log.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
