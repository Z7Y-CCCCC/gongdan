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

    @Query("select p from Product p where p.factory.id = :factoryId " +
            "and (:code = '' or locate(:code, p.code) > 0) " +
            "and (:name = '' or locate(:name, p.name) > 0) " +
            "and (:spec = '' or locate(:spec, p.spec) > 0)")
    Page<Product> search(@Param("factoryId") Long factoryId, @Param("code") String code,
            @Param("name") String name, @Param("spec") String spec, Pageable pageable
    );

    Optional<Product> findByFactoryIdAndCode(Long factoryId, String code);

    boolean existsByFactoryIdAndUnitId(Long factoryId, Long unitId);

    boolean existsByFactoryIdAndProcessRouteId(Long factoryId, Long routeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.factory.id = :factoryId")
    Optional<Product> findByIdAndFactoryIdForUpdate(@Param("id") Long id, @Param("factoryId") Long factoryId);
}
