package com.heihu.workorder.repository;

import com.heihu.workorder.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findByFactoryIdOrderByCreatedAtDesc(Long factoryId);
    Optional<ProductCategory> findByFactoryIdAndName(Long factoryId, String name);
}
