package com.heihu.workorder.repository;

import com.heihu.workorder.model.FactoryPlugin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryPluginRepository extends JpaRepository<FactoryPlugin, Long> {
    Optional<FactoryPlugin> findByFactoryIdAndPluginId(Long factoryId, Long pluginId);
    List<FactoryPlugin> findByFactoryId(Long factoryId);
}
