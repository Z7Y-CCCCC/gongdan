package com.heihu.workorder.repository;

import com.heihu.workorder.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByFactoryIdAndCode(Long factoryId, String code);

    Optional<Supplier> findByFactoryIdAndName(Long factoryId, String name);

    Page<Supplier> findByFactoryId(Long factoryId, Pageable pageable);

    Page<Supplier> findByFactoryIdAndCodeContaining(Long factoryId, String code, Pageable pageable);

    Page<Supplier> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);
}
