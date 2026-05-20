package com.heihu.workorder.repository;

import com.heihu.workorder.model.Process;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessRepository extends JpaRepository<Process, Long> {

    Page<Process> findByFactoryId(Long factoryId, Pageable pageable);

    List<Process> findAllByFactoryId(Long factoryId);

    Page<Process> findByFactoryIdAndCodeContaining(Long factoryId, String code, Pageable pageable);

    Page<Process> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);

    Page<Process> findByFactoryIdAndCodeContainingAndNameContaining(Long factoryId, String code, String name, Pageable pageable);

    java.util.Optional<Process> findByFactoryIdAndCode(Long factoryId, String code);
}
