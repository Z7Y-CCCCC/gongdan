package com.heihu.workorder.repository;

import com.heihu.workorder.model.SystemOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemOperationLogRepository extends JpaRepository<SystemOperationLog, Long> {
    Page<SystemOperationLog> findByFactoryIdAndModule(Long factoryId, String module, Pageable pageable);
}
