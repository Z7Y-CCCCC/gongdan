package com.heihu.workorder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.PluginActivationRequest;
import com.heihu.workorder.dto.PluginCatalogResponse;
import com.heihu.workorder.dto.PluginConfigRequest;
import com.heihu.workorder.dto.PluginCreateRequest;
import com.heihu.workorder.dto.PluginRuntimeManifestResponse;
import com.heihu.workorder.dto.PluginVersionRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryPlugin;
import com.heihu.workorder.model.PluginCatalog;
import com.heihu.workorder.model.PluginVersion;
import com.heihu.workorder.repository.FactoryPluginRepository;
import com.heihu.workorder.repository.PluginCatalogRepository;
import com.heihu.workorder.repository.PluginVersionRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
    void createRejectsUnsafePluginKeyAndBackendHost() {
        PluginCreateRequest badKey = createRequest("Quality Management", "{}");
        assertThrows(ApiException.class, () -> service.create(1L, "admin", badKey));

        PluginCreateRequest badHost = createRequest("unsafe-plugin",
                "{\"backendUrl\":\"http://169.254.169.254/latest/meta-data\"}");
        when(catalogRepository.findByPluginKey("unsafe-plugin")).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.create(1L, "admin", badHost));
    }

    @Test
    void installEnableAndUninstallFollowExplicitLifecycle() {
        PluginCatalog catalog = catalog("quality-management", "IFRAME");
        PluginVersion version = version("1.0.0", catalog.getId(),
                "{\"frontendEntry\":\"http://127.0.0.1:9101/\"}");
        AtomicReference<FactoryPlugin> binding = new AtomicReference<>();
        stubCatalogVersionAndBinding(catalog, version, binding);

        PluginActivationRequest activation = new PluginActivationRequest();
        activation.setVersion("1.0.0");
        assertThrows(ApiException.class,
                () -> service.enable(1L, "admin", catalog.getPluginKey(), activation));

        PluginCatalogResponse installed = service.install(1L, "admin", catalog.getPluginKey());
        assertEquals("DISABLED", installed.getFactoryStatus());

        PluginCatalogResponse enabled = service.enable(1L, "admin", catalog.getPluginKey(), activation);
        assertEquals("ENABLED", enabled.getFactoryStatus());
        assertEquals("1.0.0", enabled.getEnabledVersion());

        PluginCatalogResponse removed = service.uninstall(1L, "admin", catalog.getPluginKey());
        assertEquals("NOT_INSTALLED", removed.getFactoryStatus());
        verify(factoryPluginRepository).delete(binding.get());
    }

    @Test
    void configRequiresInstallAndReachesRuntimeManifest() {
        PluginCatalog catalog = catalog("quality-management", "IFRAME");
        PluginVersion version = version("1.0.0", catalog.getId(),
                "{\"frontendEntry\":\"http://127.0.0.1:9101/\",\"backendUrl\":\"http://quality-plugin:9101\",\"healthUrl\":\"/actuator/health\",\"requiredRoles\":[\"ADMIN\"]}");
        AtomicReference<FactoryPlugin> binding = new AtomicReference<>();
        stubCatalogVersionAndBinding(catalog, version, binding);

        PluginConfigRequest config = new PluginConfigRequest();
        config.setConfigJson("{\"inspectionIntervalDays\":7}");
        assertThrows(ApiException.class,
                () -> service.updateConfig(1L, "admin", catalog.getPluginKey(), config));

        service.install(1L, "admin", catalog.getPluginKey());
        service.updateConfig(1L, "admin", catalog.getPluginKey(), config);
        FactoryPlugin saved = binding.get();
        saved.setEnabledVersion("1.0.0");
        saved.setStatus("ENABLED");
        saved.setHealthStatus("UP");
        when(factoryPluginRepository.findByFactoryId(1L)).thenReturn(Collections.singletonList(saved));

        List<PluginRuntimeManifestResponse> adminRuntime = service.runtimeManifest(1L, "ADMIN", 9L);
        assertEquals(1, adminRuntime.size());
        assertTrue(adminRuntime.get(0).getConfig().isEmpty());
        assertFalse(adminRuntime.get(0).getManifest().has("backendUrl"));
        assertFalse(adminRuntime.get(0).getManifest().has("healthUrl"));
        assertTrue(service.runtimeManifest(1L, "PRODUCTION", 9L).isEmpty());
    }

    @Test
    void configIsValidatedAgainstPluginSchema() {
        PluginCatalog catalog = catalog("equipment-inspection", "IFRAME");
        catalog.setManifestJson("{\"configSchema\":{\"inspectionIntervalDays\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":365},\"reminderText\":{\"type\":\"string\",\"maxLength\":5}}}");
        AtomicReference<FactoryPlugin> binding = new AtomicReference<>();
        when(catalogRepository.findByPluginKey(catalog.getPluginKey())).thenReturn(Optional.of(catalog));
        when(factoryPluginRepository.findByFactoryIdAndPluginId(1L, catalog.getId()))
                .thenAnswer(invocation -> Optional.ofNullable(binding.get()));
        when(factoryPluginRepository.save(any(FactoryPlugin.class))).thenAnswer(invocation -> {
            FactoryPlugin saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(30L);
            binding.set(saved);
            return saved;
        });

        service.install(1L, "admin", catalog.getPluginKey());

        PluginConfigRequest valid = new PluginConfigRequest();
        valid.setConfigJson("{\"inspectionIntervalDays\":7,\"reminderText\":\"每日\"}");
        service.updateConfig(1L, "admin", catalog.getPluginKey(), valid);

        PluginConfigRequest invalid = new PluginConfigRequest();
        invalid.setConfigJson("{\"inspectionIntervalDays\":0,\"unknown\":true}");
        assertThrows(ApiException.class,
                () -> service.updateConfig(1L, "admin", catalog.getPluginKey(), invalid));
    }

    @Test
    void nonHealthyRemotePluginIsExcludedFromRuntime() {
        PluginCatalog catalog = catalog("quality-management", "IFRAME");
        PluginVersion version = version("1.0.0", catalog.getId(),
                "{\"frontendEntry\":\"http://127.0.0.1:9101/\"}");
        FactoryPlugin binding = installedBinding(catalog.getId());
        binding.setStatus("ENABLED");
        binding.setEnabledVersion("1.0.0");
        binding.setHealthStatus("UNKNOWN");
        when(factoryPluginRepository.findByFactoryId(1L)).thenReturn(Collections.singletonList(binding));
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(versionRepository.findByPluginIdAndVersion(catalog.getId(), "1.0.0")).thenReturn(Optional.of(version));

        assertTrue(service.runtimeManifest(1L, "ADMIN", 9L).isEmpty());
        binding.setHealthStatus("UP");
        assertFalse(service.runtimeManifest(1L, "ADMIN", 9L).isEmpty());
    }

    @Test
    void rollbackOnlySelectsAnOlderReadyVersion() {
        PluginCatalog catalog = catalog("quality-management", "IFRAME");
        FactoryPlugin binding = installedBinding(catalog.getId());
        binding.setStatus("ENABLED");
        binding.setEnabledVersion("2.0.0");
        PluginVersion newer = version("3.0.0", catalog.getId(), "{}");
        PluginVersion current = version("2.0.0", catalog.getId(), "{}");
        PluginVersion older = version("1.0.0", catalog.getId(), "{}");
        when(catalogRepository.findByPluginKey(catalog.getPluginKey())).thenReturn(Optional.of(catalog));
        when(factoryPluginRepository.findByFactoryIdAndPluginId(1L, catalog.getId())).thenReturn(Optional.of(binding));
        when(versionRepository.findByPluginIdOrderByCreatedAtDesc(catalog.getId()))
                .thenReturn(Arrays.asList(newer, current, older));

        PluginCatalogResponse rolledBack = service.rollback(1L, "admin", catalog.getPluginKey());
        assertEquals("1.0.0", rolledBack.getEnabledVersion());
        assertEquals("1.0.0", catalog.getCurrentStableVersion());
    }

    @Test
    void versionRegistrationRejectsMalformedChecksum() {
        PluginCatalog catalog = catalog("quality-management", "IFRAME");
        when(catalogRepository.findByPluginKey(catalog.getPluginKey())).thenReturn(Optional.of(catalog));
        PluginVersionRequest request = new PluginVersionRequest();
        request.setVersion("1.0.0");
        request.setChecksum("not-a-sha256");
        when(versionRepository.findByPluginIdAndVersion(catalog.getId(), "1.0.0")).thenReturn(Optional.empty());

        assertThrows(ApiException.class,
                () -> service.registerVersion(1L, "admin", catalog.getPluginKey(), request));
    }

    private PluginCreateRequest createRequest(String key, String manifest) {
        PluginCreateRequest request = new PluginCreateRequest();
        request.setPluginKey(key);
        request.setName("测试插件");
        request.setRuntimeMode("IFRAME");
        request.setManifestJson(manifest);
        return request;
    }

    private PluginCatalog catalog(String key, String mode) {
        PluginCatalog catalog = new PluginCatalog();
        catalog.setId(10L);
        catalog.setPluginKey(key);
        catalog.setName("测试插件");
        catalog.setRuntimeMode(mode);
        catalog.setStatus("ACTIVE");
        catalog.setCurrentStableVersion("1.0.0");
        catalog.setManifestJson("{}");
        return catalog;
    }

    private PluginVersion version(String value, Long pluginId, String manifest) {
        PluginVersion version = new PluginVersion();
        version.setId((long) Math.abs(value.hashCode()));
        version.setPluginId(pluginId);
        version.setVersion(value);
        version.setStatus("READY");
        version.setManifestJson(manifest);
        return version;
    }

    private FactoryPlugin installedBinding(Long pluginId) {
        FactoryPlugin binding = new FactoryPlugin();
        binding.setId(30L);
        binding.setFactoryId(1L);
        binding.setPluginId(pluginId);
        binding.setStatus("DISABLED");
        binding.setHealthStatus("UNKNOWN");
        binding.setRolloutPercent(100);
        return binding;
    }

    private void stubCatalogVersionAndBinding(
            PluginCatalog catalog,
            PluginVersion version,
            AtomicReference<FactoryPlugin> binding
    ) {
        when(catalogRepository.findByPluginKey(catalog.getPluginKey())).thenReturn(Optional.of(catalog));
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(versionRepository.findByPluginIdAndVersion(catalog.getId(), version.getVersion())).thenReturn(Optional.of(version));
        when(versionRepository.findByPluginIdOrderByCreatedAtDesc(catalog.getId()))
                .thenReturn(Collections.singletonList(version));
        when(factoryPluginRepository.findByFactoryIdAndPluginId(1L, catalog.getId()))
                .thenAnswer(invocation -> Optional.ofNullable(binding.get()));
        when(factoryPluginRepository.save(any(FactoryPlugin.class))).thenAnswer(invocation -> {
            FactoryPlugin saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(30L);
            binding.set(saved);
            return saved;
        });
    }
}
