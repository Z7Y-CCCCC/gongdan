package com.heihu.workorder.repository;

import com.heihu.workorder.model.PluginVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginVersionRepository extends JpaRepository<PluginVersion, Long> {
    Optional<PluginVersion> findByPluginIdAndVersion(Long pluginId, String version);
    List<PluginVersion> findByPluginIdOrderByCreatedAtDesc(Long pluginId);
}
