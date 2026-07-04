package com.heihu.workorder.repository;

import com.heihu.workorder.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByFactoryIdAndCode(Long factoryId, String code);

    Optional<Customer> findByFactoryIdAndName(Long factoryId, String name);

    Page<Customer> findByFactoryId(Long factoryId, Pageable pageable);

    Page<Customer> findByFactoryIdAndCodeContaining(Long factoryId, String code, Pageable pageable);

    Page<Customer> findByFactoryIdAndNameContaining(Long factoryId, String name, Pageable pageable);
}
