package com.heihu.workorder.repository;

import com.heihu.workorder.model.CustomField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomFieldRepository extends JpaRepository<CustomField, Long> {

    List<CustomField> findByFactoryIdAndScopeOrderBySortOrderAscCreatedAtAsc(Long factoryId, String scope);

    Optional<CustomField> findByFactoryIdAndScopeAndName(Long factoryId, String scope, String name);
}
