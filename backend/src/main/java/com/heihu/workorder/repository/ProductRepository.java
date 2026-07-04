package com.heihu.workorder.repository;

import com.heihu.workorder.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByFactoryId(Long factoryId, Pageable pageable);

    Page<Product> findByFactoryIdAndCodeContainingAndNameContainingAndSpecContaining(
            Long factoryId, String code, String name, String spec, Pageable pageable
    );

    Optional<Product> findByFactoryIdAndCode(Long factoryId, String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.factory.id = :factoryId")
    Optional<Product> findByIdAndFactoryIdForUpdate(@Param("id") Long id, @Param("factoryId") Long factoryId);
}
