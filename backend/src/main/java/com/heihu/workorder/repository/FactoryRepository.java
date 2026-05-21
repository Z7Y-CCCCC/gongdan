package com.heihu.workorder.repository;

import com.heihu.workorder.model.Factory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryRepository extends JpaRepository<Factory, Long> {

    Optional<Factory> findByCode(String code);

    Optional<Factory> findByName(String name);

    List<Factory> findByCodeNotOrderByCreatedAtDesc(String code);
}
