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
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
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
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9A-Za-z][0-9A-Za-z._+\\-]{0,63}$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
    private static final int MANIFEST_MAX_LENGTH = 20000;
    private static final int CONFIG_MAX_LENGTH = 4000;
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

    @Value("${app.plugin.allowed-backend-hosts:localhost,127.0.0.1,quality-plugin,inspection-plugin}")
    private String allowedBackendHosts = "localhost,127.0.0.1,quality-plugin,inspection-plugin";

    @Value("${app.plugin.allowed-frontend-origins:http://localhost:9101,http://127.0.0.1:9101,http://localhost:9102,http://127.0.0.1:9102}")
    private String allowedFrontendOrigins = "http://localhost:9101,http://127.0.0.1:9101,http://localhost:9102,http://127.0.0.1:9102";

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
    @Cacheable(cacheNames = "platform.plugins.runtime", key = "#factoryId + ':' + #role + ':' + #userId")
    public List<PluginRuntimeManifestResponse> runtimeManifest(Long factoryId, String role, Long userId) {
        List<PluginRuntimeManifestResponse> result = new ArrayList<>();
        for (FactoryPlugin binding : factoryPluginRepository.findByFactoryId(factoryId)) {
            if (!"ENABLED".equals(binding.getStatus())
                    || !StringUtils.hasText(binding.getEnabledVersion())) {
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
            if (!"BUILT_IN".equals(catalog.getRuntimeMode()) && !"UP".equals(binding.getHealthStatus())) {
                continue;
            }
            JsonNode manifest = parseManifest(StringUtils.hasText(version.getManifestJson())
                    ? version.getManifestJson() : catalog.getManifestJson());
            if (!roleAllowed(manifest, role) || !inRollout(binding, factoryId, catalog.getPluginKey(), userId)) {
                continue;
            }
            PluginRuntimeManifestResponse item = new PluginRuntimeManifestResponse();
            item.setPluginKey(catalog.getPluginKey());
            item.setName(catalog.getName());
            item.setVersion(version.getVersion());
            item.setRuntimeMode(catalog.getRuntimeMode());
            item.setManifest(publicManifest(manifest));
            // Runtime configuration is delivered only through the gateway request.
            // It must never be exposed to the browser or an iframe context.
            item.setConfig(objectMapper.createObjectNode());
            result.add(item);
        }
        return result;
    }

    /**
     * Keeps the two-argument service call source-compatible for internal tools that do
     * not have a user id. User-facing controllers should pass the authenticated id so
     * rollout assignment remains stable per user.
     */
    @Transactional(readOnly = true)
    public List<PluginRuntimeManifestResponse> runtimeManifest(Long factoryId, String role) {
        return runtimeManifest(factoryId, role, null);
    }

    @Transactional(readOnly = true)
    public PluginTarget resolveBackendTarget(Long factoryId, Long userId, String role, String pluginKey) {
        PluginCatalog catalog = findCatalog(pluginKey);
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "插件未在当前工厂启用"));
        if (!"ENABLED".equals(binding.getStatus()) || !StringUtils.hasText(binding.getEnabledVersion())) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "插件未在当前工厂启用");
        }
        if (!"ACTIVE".equals(catalog.getStatus())) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "插件已下线");
        }
        PluginVersion version = versionRepository.findByPluginIdAndVersion(catalog.getId(), binding.getEnabledVersion())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "插件版本不存在"));
        if (!"READY".equals(version.getStatus())) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "插件版本尚未就绪");
        }
        JsonNode manifest = parseManifest(StringUtils.hasText(version.getManifestJson())
                ? version.getManifestJson() : catalog.getManifestJson());
        if (!roleAllowed(manifest, role) || !inRollout(binding, factoryId, catalog.getPluginKey(), userId)) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "当前账号未命中插件灰度范围");
        }
        if ("BUILT_IN".equals(catalog.getRuntimeMode())) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "内置插件不需要远程代理");
        }
        String backendUrl = requireAllowedBackendUrl(manifest);
        String configJson = StringUtils.hasText(binding.getConfigJson()) ? binding.getConfigJson() : "{}";
        return new PluginTarget(catalog.getPluginKey(), version.getVersion(), backendUrl, configJson);
    }

    public static final class PluginTarget {
        private final String pluginKey;
        private final String version;
        private final String backendUrl;
        private final String configJson;

        public PluginTarget(String pluginKey, String version, String backendUrl, String configJson) {
            this.pluginKey = pluginKey;
            this.version = version;
            this.backendUrl = backendUrl;
            this.configJson = configJson;
        }

        public String getPluginKey() { return pluginKey; }
        public String getVersion() { return version; }
        public String getBackendUrl() { return backendUrl; }
        public String getConfigJson() { return configJson; }
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
        if (!VERSION_PATTERN.matcher(versionText).matches()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件版本格式不正确");
        }
        if (versionRepository.findByPluginIdAndVersion(catalog.getId(), versionText).isPresent()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "插件版本已存在");
        }
        String manifest = StringUtils.hasText(request.getManifestJson())
                ? validateManifest(request.getManifestJson()) : catalog.getManifestJson();
        String checksum = normalize(request.getChecksum());
        if (checksum != null && !SHA256_PATTERN.matcher(checksum).matches()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "SHA-256 校验值必须是 64 位十六进制字符");
        }
        PluginVersion item = new PluginVersion();
        item.setPluginId(catalog.getId());
        item.setVersion(versionText);
        item.setArtifactUrl(limit(normalize(request.getArtifactUrl()), 512));
        item.setImageName(limit(normalize(request.getImageName()), 255));
        item.setChecksum(checksum == null ? null : checksum.toLowerCase());
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
    public PluginCatalogResponse install(Long factoryId, String operator, String pluginKey) {
        PluginCatalog catalog = findCatalog(pluginKey);
        if (factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId()).isPresent()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "当前工厂已经安装该插件");
        }
        FactoryPlugin binding = new FactoryPlugin();
        binding.setFactoryId(factoryId);
        binding.setPluginId(catalog.getId());
        binding.setStatus("DISABLED");
        binding.setRolloutPercent(100);
        binding.setHealthStatus("UNKNOWN");
        binding.setHealthMessage("插件已安装，等待启用");
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        audit(factoryId, operator, "PLUGIN_INSTALL", catalog.getPluginKey(), null, "安装插件");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse uninstall(Long factoryId, String operator, String pluginKey) {
        PluginCatalog catalog = findCatalog(pluginKey);
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "当前工厂尚未安装该插件"));
        String previousVersion = binding.getEnabledVersion();
        factoryPluginRepository.delete(binding);
        audit(factoryId, operator, "PLUGIN_UNINSTALL", catalog.getPluginKey(), previousVersion, "卸载插件并清理工厂配置");
        return toResponse(catalog, null);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse enable(Long factoryId, String operator, String pluginKey, PluginActivationRequest request) {
        PluginCatalog catalog = findCatalog(pluginKey);
        String version = resolveVersion(catalog, request == null ? null : request.getVersion());
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.CONFLICT, "请先在当前工厂安装插件"));
        binding.setEnabledVersion(version);
        binding.setStatus("ENABLED");
        binding.setRolloutPercent(normalizeRollout(request == null ? null : request.getRolloutPercent()));
        binding.setEnabledAt(binding.getEnabledAt() == null ? LocalDateTime.now() : binding.getEnabledAt());
        binding.setDisabledAt(null);
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        audit(factoryId, operator, "PLUGIN_ENABLE", catalog.getPluginKey(), version, "启用插件");
        if (!"BUILT_IN".equals(catalog.getRuntimeMode())) {
            return refreshHealthInternal(factoryId, operator, pluginKey, false);
        }
        binding.setHealthStatus("UP");
        binding.setHealthMessage("内置插件由核心进程托管");
        binding.setLastHealthAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
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
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId()).orElse(null);
        if (binding != null && "ENABLED".equals(binding.getStatus())) {
            binding.setEnabledVersion(selected.getVersion());
            binding.setHealthStatus("UNKNOWN");
            binding.setHealthMessage("版本已切换，等待健康检查");
            binding.setUpdatedAt(LocalDateTime.now());
            factoryPluginRepository.save(binding);
        }
        audit(factoryId, operator, "PLUGIN_VERSION_ACTIVATE", catalog.getPluginKey(), version, "当前工厂切换插件版本");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse rollback(Long factoryId, String operator, String pluginKey) {
        PluginCatalog catalog = findCatalog(pluginKey);
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "当前工厂尚未安装该插件"));
        List<PluginVersion> versions = versionRepository.findByPluginIdOrderByCreatedAtDesc(catalog.getId());
        PluginVersion previous = null;
        boolean currentFound = false;
        for (PluginVersion version : versions) {
            if (version.getVersion().equals(binding.getEnabledVersion())) {
                currentFound = true;
                continue;
            }
            if (currentFound && "READY".equals(version.getStatus())) {
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
        audit(factoryId, operator, "PLUGIN_ROLLBACK", catalog.getPluginKey(), previous.getVersion(), "回滚插件版本");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse updateConfig(Long factoryId, String operator, String pluginKey, PluginConfigRequest request) {
        PluginCatalog catalog = findCatalog(pluginKey);
        String config = request == null ? null : request.getConfigJson();
        FactoryPlugin binding = factoryPluginRepository.findByFactoryIdAndPluginId(factoryId, catalog.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.CONFLICT, "请先在当前工厂安装插件"));
        if (StringUtils.hasText(config)) {
            config = validateConfig(config, configManifest(catalog, binding));
        }
        binding.setConfigJson(StringUtils.hasText(config) ? config : null);
        binding.setUpdatedAt(LocalDateTime.now());
        factoryPluginRepository.save(binding);
        audit(factoryId, operator, "PLUGIN_CONFIG_UPDATE", catalog.getPluginKey(), null, "更新插件配置");
        return toResponse(catalog, binding);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse refreshHealth(Long factoryId, String operator, String pluginKey) {
        return refreshHealthInternal(factoryId, operator, pluginKey, true);
    }

    @CacheEvict(cacheNames = {"platform.plugins.list", "platform.plugins.runtime"}, allEntries = true)
    public PluginCatalogResponse refreshHealthSystem(Long factoryId, String pluginKey) {
        return refreshHealthInternal(factoryId, "SYSTEM", pluginKey, false);
    }

    private PluginCatalogResponse refreshHealthInternal(Long factoryId, String operator, String pluginKey, boolean writeAudit) {
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
                    backendUrl = requireAllowedBackendUrl(manifest);
                    String path = normalizeHealthPath(healthUrl);
                    SimpleClientHttpRequestFactory requestFactory = noRedirectRequestFactory();
                    requestFactory.setConnectTimeout(2000);
                    requestFactory.setReadTimeout(3000);
                    RestTemplate restTemplate = new RestTemplate(requestFactory);
                    ResponseEntity<String> response = restTemplate.exchange(
                            URI.create(backendUrl + path),
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
        if (writeAudit) {
            audit(factoryId, operator, "PLUGIN_HEALTH_REFRESH", catalog.getPluginKey(), binding.getHealthStatus(), "刷新插件健康状态");
        }
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

    private boolean inRollout(FactoryPlugin binding, Long factoryId, String pluginKey, Long userId) {
        Integer rollout = binding.getRolloutPercent() == null ? 100 : binding.getRolloutPercent();
        if (rollout >= 100) return true;
        if (rollout <= 0) return false;
        String subject = factoryId + ":" + (userId == null ? "anonymous" : userId) + ":" + pluginKey;
        int bucket = Math.floorMod(subject.hashCode(), 100);
        return bucket < rollout;
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

    private JsonNode publicManifest(JsonNode manifest) {
        if (manifest == null || !manifest.isObject()) return objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode publicNode = manifest.deepCopy();
        publicNode.remove("backendUrl");
        publicNode.remove("healthUrl");
        return publicNode;
    }

    private String validateManifest(String raw) {
        if (!StringUtils.hasText(raw)) return "{}";
        if (raw.length() > MANIFEST_MAX_LENGTH) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件清单不能超过 20000 个字符");
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件清单必须是 JSON 对象");
            }
            validateManifestNode(node);
            return node.toString();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件清单 JSON 格式不正确");
        }
    }

    private String validateConfig(String raw, JsonNode manifest) {
        if (raw.length() > CONFIG_MAX_LENGTH) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置不能超过 4000 个字符");
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置必须是 JSON 对象");
            }
            validateConfigValues(node, manifest == null ? null : manifest.get("configSchema"));
            return node.toString();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置 JSON 格式不正确");
        }
    }

    private void validateManifestNode(JsonNode node) {
        String backendUrl = text(node, "backendUrl");
        if (StringUtils.hasText(backendUrl)) requireAllowedBackendUrl(node);

        String frontendEntry = text(node, "frontendEntry");
        if (StringUtils.hasText(frontendEntry)) requireAllowedFrontendEntry(frontendEntry);

        String healthUrl = text(node, "healthUrl");
        if (StringUtils.hasText(healthUrl)) normalizeHealthPath(healthUrl);

        JsonNode roles = node.get("requiredRoles");
        if (roles != null) {
            if (!roles.isArray()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "requiredRoles 必须是数组");
            }
            for (JsonNode role : roles) {
                String value = role.asText("").toUpperCase();
                if (!("ADMIN".equals(value) || "PRODUCTION".equals(value))) {
                    throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "requiredRoles 包含不支持的角色");
                }
            }
        }

        JsonNode permissions = node.get("permissions");
        if (permissions != null) {
            if (!permissions.isArray() || permissions.size() > 100) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "permissions 必须是最多 100 项的数组");
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode permission : permissions) {
                String value = permission.asText("").trim();
                if (!Pattern.matches("^[a-z][a-z0-9:_-]{1,63}$", value) || !seen.add(value)) {
                    throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "permissions 包含不合法或重复的权限标识");
                }
            }
        }

        validateConfigSchema(node.get("configSchema"));

        JsonNode menus = node.get("menus");
        if (menus != null) {
            if (!menus.isArray() || menus.size() > 20) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "menus 必须是最多 20 项的数组");
            }
            for (JsonNode menu : menus) {
                if (!menu.isObject() || !StringUtils.hasText(text(menu, "label"))) {
                    throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件菜单必须包含 label");
                }
                String path = text(menu, "path");
                if (StringUtils.hasText(path) && !isSafeRelativePath(path)) {
                    throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件菜单 path 不安全");
                }
            }
        }
    }

    private void validateConfigSchema(JsonNode schema) {
        if (schema == null || schema.isNull()) return;
        JsonNode properties = schema.has("properties") ? schema.get("properties") : schema;
        if (!properties.isObject() || properties.size() > 100) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "configSchema 必须是最多 100 项的对象");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!CONFIG_KEY_PATTERN.matcher(field.getKey()).matches() || !field.getValue().isObject()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "configSchema 字段定义不合法");
            }
            JsonNode definition = field.getValue();
            String type = text(definition, "type");
            if (StringUtils.hasText(type) && !isConfigType(type)) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "configSchema 包含不支持的字段类型");
            }
            validateNumber(definition, "minimum");
            validateNumber(definition, "maximum");
            validateNonNegativeInteger(definition, "maxLength");
            validateNonNegativeInteger(definition, "maxItems");
            JsonNode enumValues = definition.get("enum");
            if (enumValues != null && (!enumValues.isArray() || enumValues.size() > 100)) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "configSchema enum 定义不合法");
            }
        }
        JsonNode required = schema.get("required");
        if (required != null && (!required.isArray() || required.size() > 100)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "configSchema required 定义不合法");
        }
    }

    private JsonNode configManifest(PluginCatalog catalog, FactoryPlugin binding) {
        if (binding != null && StringUtils.hasText(binding.getEnabledVersion())) {
            PluginVersion version = versionRepository.findByPluginIdAndVersion(catalog.getId(), binding.getEnabledVersion()).orElse(null);
            if (version != null && StringUtils.hasText(version.getManifestJson())) {
                return parseManifest(version.getManifestJson());
            }
        }
        return parseManifest(catalog.getManifestJson());
    }

    private void validateConfigValues(JsonNode config, JsonNode schema) {
        if (schema == null || schema.isNull()) return;
        JsonNode properties = schema.has("properties") ? schema.get("properties") : schema;
        if (!properties.isObject()) return;
        Iterator<String> names = config.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode definition = properties.get(name);
            if (definition == null || !definition.isObject()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置包含未声明的字段：" + name);
            }
            validateConfigValue(name, config.get(name), definition);
        }
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode item : required) {
                if (StringUtils.hasText(item.asText()) && !config.has(item.asText())) {
                    throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置缺少必填字段：" + item.asText());
                }
            }
        }
        Iterator<Map.Entry<String, JsonNode>> definitions = properties.fields();
        while (definitions.hasNext()) {
            Map.Entry<String, JsonNode> definition = definitions.next();
            if (definition.getValue().path("required").asBoolean(false) && !config.has(definition.getKey())) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置缺少必填字段：" + definition.getKey());
            }
        }
    }

    private void validateConfigValue(String name, JsonNode value, JsonNode definition) {
        String type = text(definition, "type");
        if (StringUtils.hasText(type) && !matchesConfigType(value, type)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置字段类型不正确：" + name);
        }
        if (value != null && value.isNumber()) {
            if (definition.has("minimum") && value.asDouble() < definition.get("minimum").asDouble()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置字段小于最小值：" + name);
            }
            if (definition.has("maximum") && value.asDouble() > definition.get("maximum").asDouble()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置字段超过最大值：" + name);
            }
        }
        if (value != null && value.isTextual() && definition.has("maxLength")
                && value.textValue().length() > definition.get("maxLength").asInt()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置字段过长：" + name);
        }
        if (value != null && value.isArray() && definition.has("maxItems")
                && value.size() > definition.get("maxItems").asInt()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置数组过长：" + name);
        }
        JsonNode enumValues = definition.get("enum");
        if (enumValues != null && enumValues.isArray()) {
            boolean matched = false;
            for (JsonNode enumValue : enumValues) {
                if (enumValue.equals(value)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件配置字段取值不允许：" + name);
            }
        }
    }

    private boolean isConfigType(String type) {
        return "string".equals(type) || "integer".equals(type) || "number".equals(type)
                || "boolean".equals(type) || "object".equals(type) || "array".equals(type);
    }

    private boolean matchesConfigType(JsonNode value, String type) {
        if (value == null) return false;
        if ("string".equals(type)) return value.isTextual();
        if ("integer".equals(type)) return value.isIntegralNumber();
        if ("number".equals(type)) return value.isNumber();
        if ("boolean".equals(type)) return value.isBoolean();
        if ("object".equals(type)) return value.isObject();
        if ("array".equals(type)) return value.isArray();
        return false;
    }

    private void validateNumber(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNumber()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "configSchema " + field + " 定义不合法");
        }
    }

    private void validateNonNegativeInteger(JsonNode node, String field) {
        if (node.has(field) && (!node.get(field).isIntegralNumber() || node.get(field).asInt() < 0)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "configSchema " + field + " 定义不合法");
        }
    }

    private String requireAllowedBackendUrl(JsonNode manifest) {
        String value = text(manifest, "backendUrl");
        if (!StringUtils.hasText(value)) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "插件尚未配置 backendUrl");
        }
        URI uri = parseHttpUri(value, "backendUrl");
        if (!hostAllowed(uri.getHost(), allowedBackendHosts)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件 backendUrl 主机不在允许列表");
        }
        return trimTrailingSlash(uri.toString());
    }

    private void requireAllowedFrontendEntry(String value) {
        URI uri = parseHttpUri(value, "frontendEntry");
        String origin = uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase()
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        boolean allowed = false;
        for (String candidate : allowedFrontendOrigins.split(",")) {
            if (origin.equalsIgnoreCase(candidate.trim())) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件 frontendEntry 来源不在允许列表");
        }
    }

    private URI parseHttpUri(String value, String field) {
        try {
            URI uri = URI.create(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost()) || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || uri.getPort() > 65535) {
                throw new IllegalArgumentException("invalid URI");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件 " + field + " 配置不正确");
        }
    }

    private boolean hostAllowed(String host, String allowList) {
        if (!StringUtils.hasText(host)) return false;
        String normalizedHost = host.toLowerCase();
        for (String raw : allowList.split(",")) {
            String candidate = raw.trim().toLowerCase();
            if (candidate.equals(normalizedHost)) return true;
            if (candidate.startsWith("*.") && normalizedHost.endsWith(candidate.substring(1))
                    && normalizedHost.length() > candidate.length() - 1) return true;
        }
        return false;
    }

    private String normalizeHealthPath(String value) {
        String path = StringUtils.hasText(value) ? value.trim() : "/actuator/health";
        if (!isSafeRelativePath(path)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "插件 healthUrl 必须是安全的相对路径");
        }
        return path;
    }

    private boolean isSafeRelativePath(String path) {
        String lower = path.toLowerCase();
        return path.startsWith("/") && !path.startsWith("//") && !path.contains("\\")
                && !path.contains("..") && !lower.contains("%2e") && !lower.contains("%2f")
                && !lower.contains("%5c") && !lower.contains("://");
    }

    private SimpleClientHttpRequestFactory noRedirectRequestFactory() {
        return new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
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
