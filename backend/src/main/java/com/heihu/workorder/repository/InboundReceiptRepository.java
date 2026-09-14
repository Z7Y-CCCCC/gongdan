package com.heihu.workorder.repository;

import com.heihu.workorder.model.InboundReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import javax.persistence.LockModeType;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, Long> {

    Page<InboundReceipt> findByFactoryId(Long factoryId, Pageable pageable);

    Optional<InboundReceipt> findByFactoryIdAndCode(Long factoryId, String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InboundReceipt r where r.id = :id and r.factory.id = :factoryId")
    Optional<InboundReceipt> findByIdAndFactoryIdForUpdate(@Param("id") Long id, @Param("factoryId") Long factoryId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM InboundReceipt r JOIN r.items i " +
            "WHERE r.factory.id = :factoryId AND i.product.id = :productId")
    boolean existsByFactoryIdAndProductId(
            @Param("factoryId") Long factoryId,
            @Param("productId") Long productId
    );

    @Query("SELECT r FROM InboundReceipt r WHERE r.factory.id = :factoryId" +
            " AND (:code IS NULL OR r.code LIKE %:code%)" +
            " AND (:inboundType IS NULL OR r.inboundType = :inboundType)" +
            " AND (:startTime IS NULL OR r.inboundTime >= :startTime)" +
            " AND (:endTime IS NULL OR r.inboundTime <= :endTime)")
    Page<InboundReceipt> search(
            @Param("factoryId") Long factoryId,
            @Param("code") String code,
            @Param("inboundType") String inboundType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
}
