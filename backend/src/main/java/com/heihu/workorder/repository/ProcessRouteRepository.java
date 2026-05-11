package com.heihu.workorder.repository;

import com.heihu.workorder.model.ProcessRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessRouteRepository extends JpaRepository<ProcessRoute, Long> {

    Page<ProcessRoute> findByFactoryId(Long factoryId, Pageable pageable);

    Page<ProcessRoute> findByFactoryIdAndCodeContaining(Long factoryId, String code, Pageable pageable);

    Page<ProcessRoute> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);

    Page<ProcessRoute> findByFactoryIdAndCodeContainingAndNameContaining(Long factoryId, String code, String name, Pageable pageable);

    Optional<ProcessRoute> findByFactoryIdAndCode(Long factoryId, String code);
}
