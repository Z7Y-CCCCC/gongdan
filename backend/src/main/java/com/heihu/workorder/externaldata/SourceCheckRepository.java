package com.heihu.workorder.externaldata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
import java.time.Instant;
public interface SourceCheckRepository extends JpaRepository<SourceCheck, Long> {
    Page<SourceCheck> findByFactoryIdAndSourceIdOrderByIdDesc(Long factoryId, Long sourceId, Pageable pageable);
    Optional<SourceCheck> findFirstByFactoryIdAndSourceIdOrderByIdDesc(Long factoryId, Long sourceId);
    Optional<SourceCheck> findFirstByFactoryIdAndSourceIdAndConfigVersionOrderByIdDesc(Long factoryId, Long sourceId, Long configVersion);
    long deleteByStartedAtBefore(Instant cutoff);
}
