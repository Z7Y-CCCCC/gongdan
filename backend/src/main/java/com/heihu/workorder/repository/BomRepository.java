package com.heihu.workorder.repository;

import com.heihu.workorder.model.BillOfMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BomRepository extends JpaRepository<BillOfMaterial, Long> {

    Optional<BillOfMaterial> findByFactoryIdAndCode(Long factoryId, String code);

    Optional<BillOfMaterial> findByFactoryIdAndProductId(Long factoryId, Long productId);

    boolean existsByFactoryIdAndProductId(Long factoryId, Long productId);

    @Query("select count(i) > 0 from BillOfMaterialItem i where i.bom.factory.id = :factoryId and i.product.id = :productId")
    boolean existsItemByFactoryIdAndProductId(@Param("factoryId") Long factoryId, @Param("productId") Long productId);

    @Query("select b from BillOfMaterial b " +
            "where b.factory.id = :factoryId " +
            "and (:code = '' or b.code like concat('%', :code, '%')) " +
            "and (:productName = '' or b.product.name like concat('%', :productName, '%'))")
    Page<BillOfMaterial> search(
            @Param("factoryId") Long factoryId,
            @Param("code") String code,
            @Param("productName") String productName,
            Pageable pageable
    );
}
