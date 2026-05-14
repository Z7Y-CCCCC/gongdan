package com.heihu.workorder.repository;

import com.heihu.workorder.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByFactoryId(Long factoryId, Pageable pageable);

    Page<Product> findByFactoryIdAndCodeContainingAndNameContainingAndSpecContaining(
            Long factoryId, String code, String name, String spec, Pageable pageable
    );

    Optional<Product> findByFactoryIdAndCode(Long factoryId, String code);
}
