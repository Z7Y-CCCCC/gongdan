package com.heihu.workorder.externaldata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface ExternalSourceRepository extends JpaRepository<ExternalSource, Long> {
    Optional<ExternalSource> findByIdAndFactoryId(Long id, Long factoryId);
    Page<ExternalSource> findByFactoryIdAndArchivedOrderByIdDesc(Long factoryId, boolean archived, Pageable pageable);
    long countByFactoryIdAndArchivedFalse(Long factoryId);
    boolean existsByFactoryIdAndCode(Long factoryId, String code);
}
