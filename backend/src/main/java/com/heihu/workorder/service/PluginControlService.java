package com.heihu.workorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.PluginActivationRequest;
import com.heihu.workorder.dto.PluginCatalogResponse;
import com.heihu.workorder.dto.PluginConfigRequest;
import com.heihu.workorder.dto.PluginCreateRequest;
import com.heihu.workorder.dto.PluginRuntimeManifestResponse;
import com.heihu.workorder.dto.PluginVersionRequest;
import com.heihu.workorder.dto.PluginVersionResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryPlugin;
import com.heihu.workorder.model.PluginCatalog;
import com.heihu.workorder.model.PluginVersion;
import com.heihu.workorder.repository.FactoryPluginRepository;
import com.heihu.workorder.repository.PluginCatalogRepository;
import com.heihu.workorder.repository.PluginVersionRepository;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
@Transactional
public class PluginControlService {

    private static final Logger log = LoggerFactory.getLogger(PluginControlService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");
    private static final Set<String> RUNTIME_MODES = new HashSet<>();

    static {
        RUNTIME_MODES.add("BUILT_IN");
        RUNTIME_MODES.add("REMOTE_SERVICE");
        RUNTIME_MODES.add("IFRAME");
    }

    private final PluginCatalogRepository catalogRepository;
    private final PluginVersionRepository versionRepository;
    private final FactoryPluginRepository factoryPluginRepository;
    private final ObjectMapper objectMapper;
    private final SystemLogService systemLogService;

    public PluginControlService(
            PluginCatalogRepository catalogRepository,
            PluginVersionRepository versionRepository,
            FactoryPluginRepository factoryPluginRepository,
            ObjectMapper objectMapper,
            SystemLogService systemLogService
    ) {
        this.catalogRepository = catalogRepository;
        this.versionRepository = versionRepository;
        this.factoryPluginRepository = factoryPluginRepository;
        this.objectMapper = objectMapper;
        this.systemLogService = systemLogService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "platform.plugins.list", key = "#factoryId")
    public List<PluginCatalogResponse> list(Long factoryId) {
        Map<Long, FactoryPlugin> enabledByPlugin = new LinkedHashMap<>();
        for (FactoryPlugin item : factoryPluginRepository.findByFactoryId(factoryId)) {
            enabledByPlugin.put(item.getPluginId(), item);
        }
        List<PluginCatalogResponse> result = new ArrayList<>();
        for (PluginCatalog catalog : catalogRepository.findAllByOrderByNameAsc()) {
            result.add(toResponse(catalog, enabledByPlugin.get(catalog.getId())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "platform.plugins.runtime", key = "#factoryId + ':' + #role")
    public List<PluginRuntimeManifestResponse> runtimeManifest(Long factoryId, String role) {
        List<PluginRuntimeManifestResponse> result = new ArrayList<>();
        for (FactoryPlugin binding : factoryPluginRepository.findByFactoryId(factoryId)) {
            if (!"ENABLED".equals(binding.getStatus()) || !StringUtils.hasText(binding.getEnabledVersion())) {
                continue;
            }
            PluginCatalog catalog = catalogRepository.findById(binding.getPluginId()).orElse(null);
            if (catalog == null || !"ACTIVE".equals(catalog.getStatus())) {
                continue;
            }
            PluginVersion version = versionRepository.findByPluginIdAndVersion(catalog.getId(), binding.getEnabledVersion()).orElse(null);
            if (version == null || !"READY".equals(version.getStatus())) {
                continue;
            }
            JsonNode manifest = parseManifest(StringUtils.hasText(version.getManifestJson())
                    ? version.getManifestJson() : catalog.getManifestJson());
            if (!roleAllowed(manifest, role)) {
                continue;
            }
            PluginRuntimeManifestResponse item = new PluginRuntimeManifestResponse();
            item.setPluginKey(catalog.getPluginKey());
            item.setName(catalog.getName());
            item.setVersion(version.getVersion());
            item.setRuntimeMode(catalog.getRuntimeMode());
            item.setManifest(manifest);
            result.add(item);
        }
        return result;
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse create(Long factoryId, String operator, PluginCreateRequest request) {
        String key = normalize(request.getPluginKey());
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "插件标识必须是小写字母开头的 2-64 位字母、数字或短横线");
        }
        if (catalogRepository.findByPluginKey(key).isPresent()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "插件标识已存在");
        }
        String name = required(request.getName(), "插件名称不能为空");
        String runtimeMode = normalizeUpper(request.getRuntimeMode(), "IFRAME");
        if (!RUNTIME_MODES.contains(runtimeMode)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "不支持的插件运行模式");
        }
        PluginCatalog catalog = new PluginCatalog();
        catalog.setPluginKey(key);
        catalog.setName(limit(name, 128));
        catalog.setDescription(limit(normalize(request.getDescription()), 500));
        catalog.setPluginType(limit(normalizeUpper(request.getPluginType(), "CUSTOM"), 32));
        catalog.setRuntimeMode(runtimeMode);
        catalog.setStatus("ACTIVE");
        catalog.setMinCoreVersion(limit(normalize(request.getMinCoreVersion()), 64));
        catalog.setManifestJson(validateManifest(request.getManifestJson()));
        catalog.setCreatedAt(LocalDateTime.now());
        catalog.setUpdatedAt(LocalDateTime.now());
        PluginCatalog saved = catalogRepository.save(catalog);
        audit(factoryId, operator, "PLUGIN_CREATE", key, name, "创建插件目录");
        return toResponse(saved, null);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse registerVersion(Long factoryId, String operator, String pluginKey, PluginVersionRequest request) {
        PluginCatalog catalog = findCatalog(pluginKey);
        String versionText = required(request.getVersion(), "插件版本不能为空");
        if (versionText.length() > 64) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件版本不能超过 64 个字符");
        }
        if (versionRepository.findByPluginIdAndVersion(catalog.getId(), versionText).isPresent()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "插件版本已存在");
        }
        String manifest = StringUtils.hasText(request.getManifestJson())
                ? validateManifest(request.getManifestJson()) : catalog.getManifestJson();
        PluginVersion item = new PluginVersion();
        item.setPluginId(catalog.getId());
        item.setVersion(versionText);
        item.setArtifactUrl(limit(normalize(request.getArtifactUrl()), 512));
        item.setImageName(limit(normalize(request.getImageName()), 255));
        item.setChecksum(limit(normalize(request.getChecksum()), 128));
        item.setStatus("READY");
        item.setReleaseNotes(limit(request.getReleaseNotes(), 20000));
        item.setManifestJson(manifest);
        item.setCreatedAt(LocalDateTime.now());
        versionRepository.save(item);
        if (!StringUtils.hasText(catalog.getCurrentStableVersion())) {
            catalog.setCurrentStableVersion(versionText);
            catalog.setUpdatedAt(LocalDateTime.now());
            catalogRepository.save(catalog);
        }
        audit(factoryId, operator, "PLUGIN_VERSION_REGISTER", catalog.getPluginKey(), versionText, "登记插件版本");
        return toResponse(catalog, factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId()).orElse(null));
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse enable(Long factoryId, String operator, String pluginKey, PluginActivationRequest request) {
        PluginCatalog catalog = findCatalog(pluginKey);
        String version = resolveVersion(catalog, request == null ? null : request.getVersion());
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId()).orElseGet(() -> {
            FactoryPlugin item = new FactoryPlugin();
            item.setFactoryId(factoryId);
            item.setPluginId(catalog.getId());
            item.setHealthStatus("UNKNOWN");
            return item;
        });
        binding.setEnabledVersion(version);
        binding.setStatus("ENABLED");
        binding.setRolloutPercent(normalizeRollout(request == null ? null : request.getRolloutPercent()));
        binding.setEnabledAt(binding.getEnabledAt() == null ? LocalDateTime.now() : binding.getEnabledAt());
        binding.setDisabledAt(null);
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        audit(factoryId, operator, "PLUGIN_ENABLE", catalog.getPluginKey(), version, "启用插件");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse disable(Long factoryId, String operator, String pluginKey) {
        PluginCatalog catalog = findCatalog(pluginKey);
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "当前工厂尚未安装该插件"));
        binding.setStatus("DISABLED");
        binding.setDisabledAt(LocalDateTime.now());
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        audit(factoryId, operator, "PLUGIN_DISABLE", catalog.getPluginKey(), binding.getEnabledVersion(), "下线插件");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse activateVersion(Long factoryId, String operator, String pluginKey, String version) {
        PluginCatalog catalog = findCatalog(pluginKey);
        PluginVersion selected = versionRepository.findByPluginIdAndVersion(catalog.getId(), version)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "插件版本不存在"));
        if (!"READY".equals(selected.getStatus())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "只有 READY 状态的版本可以上线");
        }
        catalog.setCurrentStableVersion(selected.getVersion());
        catalog.setUpdatedAt(LocalDateTime.now());
        catalogRepository.save(catalog);
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId()).orElse(null);
        if (binding != null && "ENABLED".equals(binding.getStatus())) {
            binding.setEnabledVersion(selected.getVersion());
            binding.setHealthStatus("UNKNOWN");
            binding.setHealthMessage("版本已切换，等待健康检查");
            binding.setUpdatedAt(LocalDateTime.now());
            factoryPluginRepository.save(binding);
        }
        audit(factoryId, operator, "PLUGIN_VERSION_ACTIVATE", catalog.getPluginKey(), version, "切换插件版本");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse rollback(Long factoryId, String operator, String pluginKey) {
        PluginCatalog catalog = findCatalog(pluginKey);
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "当前工厂尚未安装该插件"));
        List<PluginVersion> versions = versionRepository.findByPluginIdOrderByCreatedAtDesc(catalog.getId());
        PluginVersion previous = null;
        for (PluginVersion version : versions) {
            if ("READY".equals(version.getStatus()) && !version.getVersion().equals(binding.getEnabledVersion())) {
                previous = version;
                break;
            }
        }
        if (previous == null) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "没有可回滚的旧版本");
        }
        binding.setEnabledVersion(previous.getVersion());
        binding.setStatus("ENABLED");
        binding.setHealthStatus("UNKNOWN");
        binding.setHealthMessage("已回滚，等待健康检查");
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        catalog.setCurrentStableVersion(previous.getVersion());
        catalog.setUpdatedAt(LocalDateTime.now());
        catalogRepository.save(catalog);
        audit(factoryId, operator, "PLUGIN_ROLLBACK", catalog.getPluginKey(), previous.getVersion(), "回滚插件版本");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse updateConfig(Long factoryId, String operator, String pluginKey, PluginConfigRequest request) {
        PluginCatalog catalog = findCatalog(pluginKey);
        String config = request == null ? null : request.getConfigJson();
        if (StringUtils.hasText(config)) {
            validateManifest(config);
        }
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId()).orElseGet(() -> {
            FactoryPlugin item = new FactoryPlugin();
            item.setFactoryId(factoryId);
            item.setPluginId(catalog.getId());
            item.setStatus("DISABLED");
            item.setHealthStatus("UNKNOWN");
            return item;
        });
        binding.setConfigJson(StringUtils.hasText(config) ? config : null);
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        audit(factoryId, operator, "PLUGIN_CONFIG_UPDATE", catalog.getPluginKey(), null, "更新插件配置");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse refreshHealth(Long factoryId, String operator, String pluginKey) {
        PluginCatalog catalog = findCatalog(pluginKey);
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "当前工厂尚未安装该插件"));
        PluginVersion currentVersion = StringUtils.hasText(binding.getEnabledVersion())
                ? versionRepository.findByPluginIdAndVersion(catalog.getId(), binding.getEnabledVersion()).orElse(null)
                : null;
        JsonNode manifest = parseManifest(currentVersion != null && StringUtils.hasText(currentVersion.getManifestJson())
                ? currentVersion.getManifestJson() : catalog.getManifestJson());
        String mode = catalog.getRuntimeMode();
        if ("BUILT_IN".equals(mode)) {
            binding.setHealthStatus("UP");
            binding.setHealthMessage("内置插件由核心进程托管");
        } else {
            String backendUrl = text(manifest, "backendUrl");
            String healthUrl = text(manifest, "healthUrl");
            if (!StringUtils.hasText(backendUrl)) {
                binding.setHealthStatus("UNKNOWN");
                binding.setHealthMessage("未配置 backendUrl，尚未接入独立插件服务");
            } else {
                try {
                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    requestFactory.setConnectTimeout(2000);
                    requestFactory.setReadTimeout(3000);
                    RestTemplate restTemplate = new RestTemplate(requestFactory);
                    String path = StringUtils.hasText(healthUrl) ? healthUrl : "/actuator/health";
                    ResponseEntity<String> response = restTemplate.exchange(
                            URI.create(trimTrailingSlash(backendUrl) + (path.startsWith("/") ? path : "/" + path)),
                            HttpMethod.GET,
                            HttpEntity.EMPTY,
                            String.class
                    );
                    binding.setHealthStatus(response.getStatusCode().is2xxSuccessful() ? "UP" : "DOWN");
                    binding.setHealthMessage("健康检查返回 HTTP " + response.getStatusCodeValue());
                } catch (Exception ex) {
                    binding.setHealthStatus("DOWN");
                    binding.setHealthMessage(limit("健康检查失败：" + ex.getMessage(), 500));
                }
            }
        }
        binding.setLastHealthAt(LocalDateTime.now());
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        audit(factoryId, operator, "PLUGIN_HEALTH_REFRESH", catalog.getPluginKey(), binding.getHealthStatus(), "刷新插件健康状态");
        return toResponse(catalog, binding);
    }

    private PluginCatalog findCatalog(String pluginKey) {
        String key = normalize(pluginKey);
        return catalogRepository.findByPluginKey(key)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "插件不存在"));
    }

    private String resolveVersion(PluginCatalog catalog, String requested) {
        String version = StringUtils.hasText(requested) ? requested.trim() : catalog.getCurrentStableVersion();
        if (!StringUtils.hasText(version)) {
            List<PluginVersion> versions = versionRepository.findByPluginIdOrderByCreatedAtDesc(catalog.getId());
            for (PluginVersion item : versions) {
                if ("READY".equals(item.getStatus())) {
                    version = item.getVersion();
                    break;
                }
            }
        }
        if (!StringUtils.hasText(version)) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "请先登记一个 READY 状态的插件版本");
        }
        PluginVersion item = versionRepository.findByPluginIdAndVersion(catalog.getId(), version)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "插件版本不存在"));
        if (!"READY".equals(item.getStatus())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "只有 READY 状态的版本可以启用");
        }
        return version;
    }

    private PluginCatalogResponse toResponse(PluginCatalog catalog, FactoryPlugin binding) {
        PluginCatalogResponse response = new PluginCatalogResponse();
        response.setId(catalog.getId());
        response.setPluginKey(catalog.getPluginKey());
        response.setName(catalog.getName());
        response.setDescription(catalog.getDescription());
        response.setPluginType(catalog.getPluginType());
        response.setRuntimeMode(catalog.getRuntimeMode());
        response.setStatus(catalog.getStatus());
        response.setCurrentStableVersion(catalog.getCurrentStableVersion());
        response.setMinCoreVersion(catalog.getMinCoreVersion());
        response.setManifestJson(catalog.getManifestJson());
        if (binding != null) {
            response.setFactoryStatus(binding.getStatus());
            response.setEnabledVersion(binding.getEnabledVersion());
            response.setRolloutPercent(binding.getRolloutPercent());
            response.setHealthStatus(binding.getHealthStatus());
            response.setHealthMessage(binding.getHealthMessage());
            response.setLastHealthAt(format(binding.getLastHealthAt()));
            response.setConfigJson(binding.getConfigJson());
        } else {
            response.setFactoryStatus("NOT_INSTALLED");
            response.setRolloutPercent(100);
            response.setHealthStatus("UNKNOWN");
            response.setConfigJson(null);
        }
        List<PluginVersionResponse> versions = new ArrayList<>();
        for (PluginVersion version : versionRepository.findByPluginIdOrderByCreatedAtDesc(catalog.getId())) {
            PluginVersionResponse item = new PluginVersionResponse();
            item.setId(version.getId());
            item.setVersion(version.getVersion());
            item.setArtifactUrl(version.getArtifactUrl());
            item.setImageName(version.getImageName());
            item.setChecksum(version.getChecksum());
            item.setStatus(version.getStatus());
            item.setReleaseNotes(version.getReleaseNotes());
            item.setManifestJson(version.getManifestJson());
            item.setCreatedAt(format(version.getCreatedAt()));
            versions.add(item);
        }
        response.setVersions(versions);
        return response;
    }

    private boolean roleAllowed(JsonNode manifest, String role) {
        JsonNode roles = manifest == null ? null : manifest.get("requiredRoles");
        if (roles == null || !roles.isArray() || !StringUtils.hasText(role)) return true;
        for (JsonNode item : roles) {
            if (role.equalsIgnoreCase(item.asText())) return true;
        }
        return false;
    }

    private JsonNode parseManifest(String raw) {
        if (!StringUtils.hasText(raw)) return objectMapper.createObjectNode();
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ex) {
            log.warn("插件 manifest 解析失败，将使用空清单：{}", ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String validateManifest(String raw) {
        if (!StringUtils.hasText(raw)) return "{}";
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件清单必须是 JSON 对象");
            }
            return node.toString();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件清单 JSON 格式不正确");
        }
    }

    private void audit(Long factoryId, String operator, String action, String targetCode, String targetName, String detail) {
        try {
            com.heihu.workorder.dto.SystemOperationLogRequest request = new com.heihu.workorder.dto.SystemOperationLogRequest();
            request.setModule("PLUGIN");
            request.setActionType(action);
            request.setTargetCode(targetCode);
            request.setTargetName(targetName);
            request.setDetail(detail);
            systemLogService.recordOperation(factoryId, operator, request);
        } catch (RuntimeException ex) {
            log.warn("插件操作审计写入失败，不阻断插件操作：{}", ex.getMessage());
        }
    }

    private Integer normalizeRollout(Integer value) {
        if (value == null) return 100;
        if (value < 0 || value > 100) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "灰度比例必须在 0 到 100 之间");
        }
        return value;
    }

    private String required(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
        return normalized;
    }

    private String normalizeUpper(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized.toUpperCase();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(FMT);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null ? null : normalize(value.asText());
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
