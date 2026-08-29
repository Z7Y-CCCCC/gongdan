package com.heihu.workorder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.PluginActivationRequest;
import com.heihu.workorder.dto.PluginCatalogResponse;
import com.heihu.workorder.dto.PluginCreateRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryPlugin;
import com.heihu.workorder.model.PluginCatalog;
import com.heihu.workorder.model.PluginVersion;
import com.heihu.workorder.repository.FactoryPluginRepository;
import com.heihu.workorder.repository.PluginCatalogRepository;
import com.heihu.workorder.repository.PluginVersionRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginControlServiceTest {

    private PluginCatalogRepository catalogRepository;
    private PluginVersionRepository versionRepository;
    private FactoryPluginRepository factoryPluginRepository;
    private PluginControlService service;

    @BeforeEach
    void setUp() {
        catalogRepository = mock(PluginCatalogRepository.class);
        versionRepository = mock(PluginVersionRepository.class);
        factoryPluginRepository = mock(FactoryPluginRepository.class);
        service = new PluginControlService(
                catalogRepository,
                versionRepository,
                factoryPluginRepository,
                new ObjectMapper(),
                mock(SystemLogService.class)
        );
    }

    @Test
    void createRejectsUnsafePluginKey() {
        PluginCreateRequest request = new PluginCreateRequest();
        request.setPluginKey("Quality Management");
        request.setName("质量管理");

        assertThrows(ApiException.class, () -> service.create(1L, "admin", request));
    }

    @Test
    void createAndEnableKeepsFactoryStateSeparate() {
        PluginCreateRequest request = new PluginCreateRequest();
        request.setPluginKey("quality-management");
        request.setName("质量管理");
        request.setRuntimeMode("IFRAME");
        request.setManifestJson("{\"frontendEntry\":\"http://127.0.0.1:9101/\"}");

        when(catalogRepository.findByPluginKey("quality-management")).thenReturn(Optional.empty());
        when(catalogRepository.save(any(PluginCatalog.class))).thenAnswer(invocation -> {
            PluginCatalog catalog = invocation.getArgument(0);
            catalog.setId(10L);
            return catalog;
        });
        when(versionRepository.findByPluginIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());

        PluginCatalogResponse created = service.create(1L, "admin", request);
        assertEquals("NOT_INSTALLED", created.getFactoryStatus());

        PluginVersion version = new PluginVersion();
        version.setId(20L);
        version.setPluginId(10L);
        version.setVersion("1.0.0");
        version.setStatus("READY");
        version.setManifestJson(request.getManifestJson());
        when(versionRepository.findByPluginIdAndVersion(10L, "1.0.0")).thenReturn(Optional.of(version));

        PluginCatalog catalog = new PluginCatalog();
        catalog.setId(10L);
        catalog.setPluginKey("quality-management");
        catalog.setName("质量管理");
        catalog.setRuntimeMode("IFRAME");
        catalog.setStatus("ACTIVE");
        catalog.setCurrentStableVersion("1.0.0");
        when(catalogRepository.findByPluginKey("quality-management")).thenReturn(Optional.of(catalog));
        when(factoryPluginRepository.findByFactoryIdAndPluginId(1L, 10L)).thenReturn(Optional.empty());
        when(factoryPluginRepository.save(any(FactoryPlugin.class))).thenAnswer(invocation -> {
            FactoryPlugin binding = invocation.getArgument(0);
            binding.setId(30L);
            return binding;
        });
        when(versionRepository.findByPluginIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.singletonList(version));

        PluginActivationRequest activation = new PluginActivationRequest();
        activation.setVersion("1.0.0");
        PluginCatalogResponse enabled = service.enable(1L, "admin", "quality-management", activation);
        assertEquals("ENABLED", enabled.getFactoryStatus());
        assertEquals("1.0.0", enabled.getEnabledVersion());
    }
}
