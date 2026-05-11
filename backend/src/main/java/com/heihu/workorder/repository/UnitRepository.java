package com.heihu.workorder.repository;

import com.heihu.workorder.model.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    Optional<Unit> findByFactoryIdAndName(Long factoryId, String name);

    Page<Unit> findByFactoryId(Long factoryId, Pageable pageable);

    Page<Unit> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);
}
