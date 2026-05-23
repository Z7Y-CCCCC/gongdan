package com.heihu.workorder.repository;

import com.heihu.workorder.model.SystemImportLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemImportLogRepository extends JpaRepository<SystemImportLog, Long> {
    Page<SystemImportLog> findByFactoryIdAndModule(Long factoryId, String module, Pageable pageable);
}
