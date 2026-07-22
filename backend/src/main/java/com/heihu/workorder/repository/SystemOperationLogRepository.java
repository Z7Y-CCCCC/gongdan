package com.heihu.workorder.repository;

import com.heihu.workorder.model.SystemOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SystemOperationLogRepository extends JpaRepository<SystemOperationLog, Long> {
    Page<SystemOperationLog> findByFactoryIdAndModule(Long factoryId, String module, Pageable pageable);

    @Modifying
    @Query("delete from SystemOperationLog log where log.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
