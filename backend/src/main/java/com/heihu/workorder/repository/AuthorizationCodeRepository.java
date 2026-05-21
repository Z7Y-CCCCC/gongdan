package com.heihu.workorder.repository;

import com.heihu.workorder.model.AuthorizationCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, Long> {

    Optional<AuthorizationCode> findByCode(String code);

    List<AuthorizationCode> findByFactoryIdOrderByCreatedAtDesc(Long factoryId);

    List<AuthorizationCode> findByFactoryIdAndConsumedFalseAndDisabledFalse(Long factoryId);

    List<AuthorizationCode> findAllByOrderByCreatedAtDesc();
}
