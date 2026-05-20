package com.heihu.workorder.repository;

import com.heihu.workorder.model.InboundReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, Long> {

    Page<InboundReceipt> findByFactoryId(Long factoryId, Pageable pageable);

    Optional<InboundReceipt> findByFactoryIdAndCode(Long factoryId, String code);

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
