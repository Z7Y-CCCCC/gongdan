package com.heihu.workorder.repository;

import com.heihu.workorder.model.PluginCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginCatalogRepository extends JpaRepository<PluginCatalog, Long> {
    Optional<PluginCatalog> findByPluginKey(String pluginKey);
    List<PluginCatalog> findAllByOrderByNameAsc();
}
