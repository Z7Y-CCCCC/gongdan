package com.heihu.workorder.repository;

import com.heihu.workorder.model.OutboundReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OutboundReceiptRepository extends JpaRepository<OutboundReceipt, Long> {

    Page<OutboundReceipt> findByFactoryId(Long factoryId, Pageable pageable);

    Optional<OutboundReceipt> findByFactoryIdAndCode(Long factoryId, String code);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM OutboundReceipt r JOIN r.items i " +
            "WHERE r.factory.id = :factoryId AND i.product.id = :productId")
    boolean existsByFactoryIdAndProductId(
            @Param("factoryId") Long factoryId,
            @Param("productId") Long productId
    );

    @Query("SELECT r FROM OutboundReceipt r WHERE r.factory.id = :factoryId" +
            " AND (:code IS NULL OR r.code LIKE %:code%)" +
            " AND (:outboundType IS NULL OR r.outboundType = :outboundType)" +
            " AND (:startTime IS NULL OR r.outboundTime >= :startTime)" +
            " AND (:endTime IS NULL OR r.outboundTime <= :endTime)")
    Page<OutboundReceipt> search(
            @Param("factoryId") Long factoryId,
            @Param("code") String code,
            @Param("outboundType") String outboundType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
}
