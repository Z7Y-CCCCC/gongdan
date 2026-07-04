package com.heihu.workorder.repository;

import com.heihu.workorder.model.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByFactoryIdAndCode(Long factoryId, String code);

    Optional<Warehouse> findByFactoryIdAndName(Long factoryId, String name);

    Page<Warehouse> findByFactoryId(Long factoryId, Pageable pageable);

    Page<Warehouse> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);

    Page<Warehouse> findByFactoryIdAndCodeContaining(Long factoryId, String code, Pageable pageable);
}
