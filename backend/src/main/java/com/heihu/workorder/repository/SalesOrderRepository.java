package com.heihu.workorder.repository;

import com.heihu.workorder.model.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findByFactoryIdAndCode(Long factoryId, String code);

    @Query("select count(i) > 0 from SalesOrderItem i where i.salesOrder.factory.id = :factoryId and i.product.id = :productId")
    boolean existsItemByFactoryIdAndProductId(@Param("factoryId") Long factoryId, @Param("productId") Long productId);

    @Query("select s from SalesOrder s " +
            "where s.factory.id = :factoryId " +
            "and (:code = '' or s.code like concat('%', :code, '%')) " +
            "and (:customerName = '' or s.customerName like concat('%', :customerName, '%')) " +
            "and (:status = '' or s.status = :status)")
    Page<SalesOrder> search(
            @Param("factoryId") Long factoryId,
            @Param("code") String code,
            @Param("customerName") String customerName,
            @Param("status") String status,
            Pageable pageable
    );
}
