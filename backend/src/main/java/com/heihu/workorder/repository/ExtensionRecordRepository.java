package com.heihu.workorder.repository;

import com.heihu.workorder.model.ExtensionRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionRecordRepository extends JpaRepository<ExtensionRecord, Long> {

    Page<ExtensionRecord> findByFactoryIdAndModuleName(Long factoryId, String moduleName, Pageable pageable);

    Page<ExtensionRecord> findByFactoryIdAndModuleNameAndStatus(Long factoryId, String moduleName, String status, Pageable pageable);

    Optional<ExtensionRecord> findByIdAndFactoryId(Long id, Long factoryId);
}
