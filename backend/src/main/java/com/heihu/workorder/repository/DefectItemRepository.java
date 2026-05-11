package com.heihu.workorder.repository;

import com.heihu.workorder.model.DefectItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DefectItemRepository extends JpaRepository<DefectItem, Long> {

    Optional<DefectItem> findByFactoryIdAndCode(Long factoryId, String code);

    Page<DefectItem> findByFactoryId(Long factoryId, Pageable pageable);

    Page<DefectItem> findByFactoryIdAndCodeContainingAndNameContaining(Long factoryId, String code, String name, Pageable pageable);

    Page<DefectItem> findByFactoryIdAndCodeContaining(Long factoryId, String code, Pageable pageable);

    Page<DefectItem> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);
}
