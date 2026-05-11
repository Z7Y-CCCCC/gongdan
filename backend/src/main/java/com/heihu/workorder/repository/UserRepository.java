package com.heihu.workorder.repository;

import com.heihu.workorder.model.FactoryUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<FactoryUser, Long> {

    Optional<FactoryUser> findByFactoryIdAndUsername(Long factoryId, String username);

    Optional<FactoryUser> findByFactoryIdAndPhone(Long factoryId, String phone);

    Optional<FactoryUser> findByIdAndFactoryId(Long id, Long factoryId);

    List<FactoryUser> findByFactoryIdOrderByCreatedAtAsc(Long factoryId);

    List<FactoryUser> findByDepartmentId(Long departmentId);
}
