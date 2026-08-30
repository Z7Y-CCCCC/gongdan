package com.heihu.workorder.service;

import com.heihu.workorder.model.FactoryPlugin;
import com.heihu.workorder.model.PluginCatalog;
import com.heihu.workorder.repository.FactoryPluginRepository;
import com.heihu.workorder.repository.PluginCatalogRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically probes enabled extensions without writing an audit row per probe. */
@Component
public class PluginHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(PluginHealthScheduler.class);

    private final FactoryPluginRepository factoryPluginRepository;
    private final PluginCatalogRepository pluginCatalogRepository;
    private final PluginControlService pluginControlService;

    public PluginHealthScheduler(
            FactoryPluginRepository factoryPluginRepository,
            PluginCatalogRepository pluginCatalogRepository,
            PluginControlService pluginControlService
    ) {
        this.factoryPluginRepository = factoryPluginRepository;
        this.pluginCatalogRepository = pluginCatalogRepository;
        this.pluginControlService = pluginControlService;
    }

    @Scheduled(
            fixedDelayString = "${app.plugin.health-check-ms:60000}",
            initialDelayString = "${app.plugin.health-check-initial-delay-ms:30000}"
    )
    public void refreshEnabledPlugins() {
        List<FactoryPlugin> bindings = factoryPluginRepository.findAll();
        for (FactoryPlugin binding : bindings) {
            if (!"ENABLED".equals(binding.getStatus())) continue;
            PluginCatalog catalog = pluginCatalogRepository.findById(binding.getPluginId()).orElse(null);
            if (catalog == null) continue;
            try {
                pluginControlService.refreshHealthSystem(binding.getFactoryId(), catalog.getPluginKey());
            } catch (RuntimeException ex) {
                log.warn("插件健康巡检失败 pluginKey={}, factoryId={}, reason={}",
                        catalog.getPluginKey(), binding.getFactoryId(), ex.getMessage());
            }
        }
    }
}
