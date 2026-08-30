package com.heihu.workorder.repository;

import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<FactoryUser, Long> {

    Optional<FactoryUser> findByFactoryIdAndUsername(Long factoryId, String username);

    Optional<FactoryUser> findByFactoryIdAndPhone(Long factoryId, String phone);

    Optional<FactoryUser> findByIdAndFactoryId(Long id, Long factoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from FactoryUser u join fetch u.factory where u.id = :id")
    Optional<FactoryUser> findByIdForUpdate(@Param("id") Long id);

    Optional<FactoryUser> findByWechatOpenid(String wechatOpenid);

    List<FactoryUser> findByFactoryIdOrderByCreatedAtAsc(Long factoryId);

    List<FactoryUser> findByFactoryId(Long factoryId);

    List<FactoryUser> findByFactoryIdAndEnabledTrue(Long factoryId);

    @EntityGraph(attributePaths = "department")
    List<FactoryUser> findByIdIn(Collection<Long> ids);

    List<FactoryUser> findByDepartmentId(Long departmentId);

    List<FactoryUser> findAllByPhone(String phone);

    List<FactoryUser> findByRoleAndVirtualUserFalseAndFactoryIdNotOrderByCreatedAtDesc(Role role, Long factoryId);
}
