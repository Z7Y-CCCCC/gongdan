package com.heihu.workorder.service;

import com.heihu.workorder.dto.MonitorCacheEntryResponse;
import com.heihu.workorder.dto.MonitorCacheMetricsResponse;
import com.heihu.workorder.dto.MonitorCacheNameResponse;
import com.heihu.workorder.dto.MonitorCacheRecoveryResponse;
import com.heihu.workorder.dto.MonitorCommandStatResponse;
import com.heihu.workorder.dto.MonitorOnlineUserResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.UserSession;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端系统监控服务。业务缓存由 Spring Cache 写入 Redis，本服务维护一个按工厂隔离的
 * 可查看缓存目录，便于管理端查看和软删除恢复，不直接暴露其他租户的数据。
 */
@Service
public class SystemMonitorService {

    private static final String REDIS_PREFIX = "gongdan:monitor:";
    private static final LinkedHashMap<String, String> CACHE_REMARKS = new LinkedHashMap<>();

    static {
        CACHE_REMARKS.put("online_tokens", "在线用户");
        CACHE_REMARKS.put("sys_config", "配置信息");
        CACHE_REMARKS.put("sys_dict", "数据字典");
        CACHE_REMARKS.put("work_order_summary", "工单摘要");
        CACHE_REMARKS.put("report_summary", "报工摘要");
    }

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final FactoryRepository factoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final String redisPort;
    private final Duration recoveryTtl;
    private final Map<Long, Map<String, Map<String, String>>> fallbackCaches = new ConcurrentHashMap<>();
    private final Map<Long, List<RecoverySnapshot>> recoveries = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> commandStats = new ConcurrentHashMap<>();
    private final AtomicLong snapshotSequence = new AtomicLong(0);
    private volatile boolean redisUnavailable;

    public SystemMonitorService(
            UserSessionRepository sessionRepository,
            UserRepository userRepository,
            FactoryRepository factoryRepository,
            StringRedisTemplate redisTemplate,
            @Value("${spring.redis.port:6379}") String redisPort,
            @Value("${app.cache.recovery-ttl-minutes:30}") long recoveryTtlMinutes
    ) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.factoryRepository = factoryRepository;
        this.redisTemplate = redisTemplate;
        this.redisPort = redisPort;
        this.recoveryTtl = Duration.ofMinutes(Math.max(1, recoveryTtlMinutes));
    }

    @Transactional
    public List<MonitorOnlineUserResponse> listOnlineUsers(Long factoryId, String ipAddress, String loginName) {
        count("online.list");
        Instant now = Instant.now();
        sessionRepository.deleteByExpiresAtBefore(now);
        List<UserSession> sessions = sessionRepository.findByFactoryIdAndKickedFalseAndExpiresAtAfterOrderByCreatedAtDesc(factoryId, now);
        Map<Long, String> departments = userRepository.findByIdIn(sessions.stream()
                        .map(UserSession::getUserId)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(
                        user -> user.getId(),
                        user -> user.getDepartment() == null ? "未分配部门" : user.getDepartment().getName()
                ));
        return sessions
                .stream()
                .map(session -> toOnlineResponse(session, departments.getOrDefault(session.getUserId(), "未分配部门")))
                .filter(item -> !hasText(ipAddress) || contains(item.getIpAddress(), ipAddress))
                .filter(item -> !hasText(loginName) || contains(item.getLoginName(), loginName))
                .collect(Collectors.toList());
    }

    public void forceLogout(Long factoryId, Long sessionId) {
        count("online.forceLogout");
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "在线会话不存在"));
        if (!factoryId.equals(session.getFactoryId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "在线会话不存在");
        }
        session.setKicked(true);
        sessionRepository.save(session);
        removeCacheEntry(factoryId, "online_tokens", String.valueOf(sessionId));
    }

    public MonitorCacheMetricsResponse getMetrics(Long factoryId) {
        count("cache.metrics");
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        Properties info = redisInfo();
        boolean redisReady = info != null;
        Runtime runtime = Runtime.getRuntime();
        long jvmUsed = runtime.totalMemory() - runtime.freeMemory();
        long jvmMax = runtime.maxMemory();
        long used = redisReady ? longInfo(info, "used_memory", jvmUsed) : jvmUsed;
        long configuredMax = redisReady ? longInfo(info, "maxmemory", 0) : 0;
        long max = configuredMax > 0 ? configuredMax : jvmMax;
        long uptimeSeconds = redisReady ? longInfo(info, "uptime_in_seconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000) : ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        Map<String, String> basic = new LinkedHashMap<>();
        basic.put("redisVersion", redisReady ? value(info, "redis_version", "-") : "-");
        basic.put("cacheType", redisReady ? "Redis缓存" : "本地回退缓存");
        basic.put("mode", redisReady ? value(info, "redis_mode", "standalone") : "单机回退");
        basic.put("port", redisReady ? value(info, "tcp_port", redisPort) : "—");
        basic.put("clients", redisReady ? value(info, "connected_clients", "0") : String.valueOf(activeSessionCount(factoryId)));
        basic.put("uptimeDays", String.valueOf(uptimeSeconds / (24L * 60L * 60L)));
        basic.put("usedMemory", redisReady ? value(info, "used_memory_human", humanBytes(used)) : humanBytes(used));
        basic.put("memoryConfig", redisReady ? humanBytes(configuredMax) : humanBytes(jvmMax));
        basic.put("aofEnabled", redisReady ? ("1".equals(value(info, "aof_enabled", "0")) ? "是" : "否") : "否");
        basic.put("rdbStatus", redisReady ? value(info, "rdb_last_bgsave_status", "unknown") : "unknown");
        basic.put("keyCount", redisReady ? String.valueOf(redisKeyCount()) : String.valueOf(cache.values().stream().mapToInt(Map::size).sum()));
        basic.put("networkInput", redisReady ? humanBytes(longInfo(info, "total_net_input_bytes", 0)) : "—");
        basic.put("networkOutput", redisReady ? humanBytes(longInfo(info, "total_net_output_bytes", 0)) : "—");
        basic.put("status", redisReady ? "连接正常" : "Redis不可用，已启用本地回退");
        basic.put("recoveryWindow", recoveryTtl.toMinutes() + "分钟");

        List<MonitorCommandStatResponse> commands = redisCommandStats(info);
        if (commands.isEmpty()) {
            commands = commandStats.entrySet().stream()
                    .map(entry -> new MonitorCommandStatResponse(entry.getKey(), entry.getValue().get()))
                    .sorted(Comparator.comparingLong(MonitorCommandStatResponse::getCount).reversed())
                    .limit(12)
                    .collect(Collectors.toList());
        }

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("usedBytes", used);
        memory.put("maxBytes", max);
        memory.put("usedPercent", max > 0 ? Math.round(used * 10000.0 / max) / 100.0 : 0);
        memory.put("usedHuman", humanBytes(used));
        memory.put("maxHuman", humanBytes(max));
        memory.put("heap", heapUsage());

        MonitorCacheMetricsResponse response = new MonitorCacheMetricsResponse();
        response.setBasicInfo(basic);
        response.setCommandStats(commands);
        response.setMemoryInfo(memory);
        return response;
    }

    public List<MonitorCacheNameResponse> listCacheNames(Long factoryId) {
        count("cache.names");
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        List<MonitorCacheNameResponse> result = new ArrayList<>();
        CACHE_REMARKS.forEach((name, remark) -> result.add(new MonitorCacheNameResponse(name, remark, cache.get(name).size())));
        return result;
    }

    public List<String> listCacheKeys(Long factoryId, String cacheName) {
        count("cache.keys");
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        Map<String, String> entries = cache.get(cacheName);
        if (entries == null) throw cacheNotFound(cacheName);
        List<String> keys = new ArrayList<>(entries.keySet());
        Collections.sort(keys);
        return keys;
    }

    public MonitorCacheEntryResponse getCacheValue(Long factoryId, String cacheName, String cacheKey) {
        count("cache.value");
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        Map<String, String> entries = cache.get(cacheName);
        if (entries == null || !entries.containsKey(cacheKey)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "缓存键不存在");
        }
        return new MonitorCacheEntryResponse(cacheName, cacheKey, entries.get(cacheKey));
    }

    public int clearCacheName(Long factoryId, String cacheName) {
        count("cache.clearName");
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        Map<String, String> entries = cache.get(cacheName);
        if (entries == null) throw cacheNotFound(cacheName);
        Map<String, String> deleted = new LinkedHashMap<>(entries);
        entries.clear();
        writeCache(factoryId, cache);
        saveRecovery(factoryId, "NAME", cacheName, null, deleted);
        return deleted.size();
    }

    public int clearCacheKey(Long factoryId, String cacheName, String cacheKey) {
        count("cache.clearKey");
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        Map<String, String> entries = cache.get(cacheName);
        if (entries == null) throw cacheNotFound(cacheName);
        String value = entries.remove(cacheKey);
        if (value == null) throw new ApiException(HttpStatus.NOT_FOUND, "缓存键不存在");
        writeCache(factoryId, cache);
        Map<String, String> deleted = new LinkedHashMap<>();
        deleted.put(cacheKey, value);
        saveRecovery(factoryId, "KEY", cacheName, cacheKey, deleted);
        return 1;
    }

    public int clearAll(Long factoryId) {
        count("cache.clearAll");
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        Map<String, String> deleted = new LinkedHashMap<>();
        cache.forEach((name, entries) -> entries.forEach((key, value) -> deleted.put(name + "::" + key, value)));
        cache.values().forEach(Map::clear);
        writeCache(factoryId, cache);
        saveRecovery(factoryId, "ALL", "全部缓存", null, deleted);
        return deleted.size();
    }

    public List<MonitorCacheRecoveryResponse> listRecoveries(Long factoryId) {
        count("cache.recoveries");
        cleanupExpiredRecoveries();
        List<RecoverySnapshot> snapshots = recoveries.getOrDefault(factoryId, Collections.emptyList());
        synchronized (snapshots) {
            return snapshots.stream().map(this::toRecoveryResponse).collect(Collectors.toList());
        }
    }

    public int restore(Long factoryId, Long recoveryId) {
        count("cache.restore");
        cleanupExpiredRecoveries();
        List<RecoverySnapshot> snapshots = recoveries.getOrDefault(factoryId, Collections.emptyList());
        RecoverySnapshot snapshot;
        synchronized (snapshots) {
            snapshot = snapshots.stream().filter(item -> item.id.equals(recoveryId)).findFirst().orElse(null);
        }
        if (snapshot == null) throw new ApiException(HttpStatus.GONE, "缓存恢复窗口已过期或记录不存在");

        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        for (Map.Entry<String, String> item : snapshot.entries.entrySet()) {
            String cacheName = snapshot.scope.equals("ALL") ? splitName(item.getKey()) : snapshot.cacheName;
            String cacheKey = snapshot.scope.equals("ALL") ? splitKey(item.getKey()) : item.getKey();
            Map<String, String> entries = cache.get(cacheName);
            String current = entries == null ? null : entries.get(cacheKey);
            if (current != null && !current.equals(item.getValue())) {
                throw new ApiException(HttpStatus.CONFLICT, "缓存键已重新生成，恢复会覆盖新数据，请先处理冲突后再恢复");
            }
        }
        for (Map.Entry<String, String> item : snapshot.entries.entrySet()) {
            String cacheName = snapshot.scope.equals("ALL") ? splitName(item.getKey()) : snapshot.cacheName;
            String cacheKey = snapshot.scope.equals("ALL") ? splitKey(item.getKey()) : item.getKey();
            cache.computeIfAbsent(cacheName, ignored -> new ConcurrentHashMap<>()).put(cacheKey, item.getValue());
        }
        writeCache(factoryId, cache);
        synchronized (snapshots) {
            snapshots.remove(snapshot);
        }
        return snapshot.entries.size();
    }

    @Scheduled(fixedDelayString = "${app.cache.recovery-cleanup-ms:300000}")
    public void cleanupExpiredRecoveries() {
        Instant now = Instant.now();
        recoveries.values().forEach(snapshots -> {
            synchronized (snapshots) {
                snapshots.removeIf(snapshot -> !now.isBefore(snapshot.expiresAt));
            }
        });
    }

    private Map<String, Map<String, String>> ensureCache(Long factoryId) {
        if (!isRedisAvailable()) {
            return fallbackCaches.computeIfAbsent(factoryId, this::createInitialCache);
        }
        String marker = markerKey(factoryId);
        try {
            if (redisTemplate.opsForValue().get(marker) == null) {
                writeCacheToRedis(factoryId, createInitialCache(factoryId));
                redisTemplate.opsForValue().set(marker, "1");
            }
            return readCacheFromRedis(factoryId);
        } catch (RuntimeException ex) {
            redisUnavailable = true;
            return fallbackCaches.computeIfAbsent(factoryId, this::createInitialCache);
        }
    }

    private Map<String, Map<String, String>> createInitialCache(Long factoryId) {
        Map<String, Map<String, String>> cache = new LinkedHashMap<>();
        CACHE_REMARKS.keySet().forEach(name -> cache.put(name, new ConcurrentHashMap<>()));
        refreshOnlineCache(factoryId, cache.get("online_tokens"));
        Factory factory = factoryRepository.findById(factoryId).orElse(null);
        Map<String, String> config = cache.get("sys_config");
        config.put("factory.code", factory == null ? "-" : factory.getCode());
        config.put("factory.name", factory == null ? "-" : factory.getName());
        config.put("session.ttl.minutes", "720");
        config.put("cache.recovery.ttl.minutes", String.valueOf(recoveryTtl.toMinutes()));
        Map<String, String> dict = cache.get("sys_dict");
        dict.put("work_order.status", "未开始,执行中,已结束,已取消");
        dict.put("report.status", "PENDING,APPROVED,REJECTED");
        dict.put("user.role", "ADMIN,PRODUCTION");
        cache.get("work_order_summary").put("source", "数据库业务接口");
        cache.get("work_order_summary").put("lastRefresh", LocalDateTime.now().toString());
        cache.get("report_summary").put("source", "数据库业务接口");
        cache.get("report_summary").put("lastRefresh", LocalDateTime.now().toString());
        return cache;
    }

    private void refreshOnlineCache(Long factoryId, Map<String, String> target) {
        if (target == null) return;
        sessionRepository.findByFactoryIdAndKickedFalseAndExpiresAtAfterOrderByCreatedAtDesc(factoryId, Instant.now())
                .forEach(session -> target.put(String.valueOf(session.getId()), session.getDisplayName() + " / " + session.getFactoryCode()));
    }

    private Map<String, Map<String, String>> readCacheFromRedis(Long factoryId) {
        Map<String, Map<String, String>> cache = new LinkedHashMap<>();
        CACHE_REMARKS.keySet().forEach(name -> {
            Map<String, String> entries = new LinkedHashMap<>();
            Set<String> keys = redisTemplate.opsForSet().members(indexKey(factoryId, name));
            if (keys != null) {
                for (String key : keys) {
                    String value = redisTemplate.opsForValue().get(valueKey(factoryId, name, key));
                    if (value != null) entries.put(key, value);
                }
            }
            cache.put(name, entries);
        });
        return cache;
    }

    private void writeCache(Long factoryId, Map<String, Map<String, String>> cache) {
        if (!isRedisAvailable()) {
            fallbackCaches.put(factoryId, copyCache(cache));
            return;
        }
        try {
            writeCacheToRedis(factoryId, cache);
        } catch (RuntimeException ex) {
            redisUnavailable = true;
            fallbackCaches.put(factoryId, copyCache(cache));
        }
    }

    private void writeCacheToRedis(Long factoryId, Map<String, Map<String, String>> cache) {
        CACHE_REMARKS.keySet().forEach(name -> {
            String index = indexKey(factoryId, name);
            Set<String> existing = redisTemplate.opsForSet().members(index);
            if (existing != null) {
                for (String key : existing) redisTemplate.delete(valueKey(factoryId, name, key));
            }
            redisTemplate.delete(index);
            Map<String, String> entries = cache.get(name);
            if (entries != null) {
                entries.forEach((key, value) -> {
                    redisTemplate.opsForValue().set(valueKey(factoryId, name, key), value);
                    redisTemplate.opsForSet().add(index, key);
                });
            }
        });
    }

    private void removeCacheEntry(Long factoryId, String cacheName, String cacheKey) {
        Map<String, Map<String, String>> cache = ensureCache(factoryId);
        Map<String, String> entries = cache.get(cacheName);
        if (entries != null && entries.remove(cacheKey) != null) writeCache(factoryId, cache);
    }

    private Map<String, Map<String, String>> copyCache(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        source.forEach((name, entries) -> copy.put(name, new ConcurrentHashMap<>(entries)));
        return copy;
    }

    private boolean isRedisAvailable() {
        if (redisUnavailable) return false;
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return pong != null;
        } catch (RuntimeException ex) {
            redisUnavailable = true;
            return false;
        }
    }

    private Properties redisInfo() {
        if (!isRedisAvailable()) return null;
        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) connection -> connection.info());
            return info == null ? new Properties() : info;
        } catch (RuntimeException ex) {
            redisUnavailable = true;
            return null;
        }
    }

    private long redisKeyCount() {
        try {
            Long count = redisTemplate.execute((RedisCallback<Long>) connection -> connection.dbSize());
            return count == null ? 0 : count;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private List<MonitorCommandStatResponse> redisCommandStats(Properties info) {
        if (info == null) return Collections.emptyList();
        List<MonitorCommandStatResponse> result = new ArrayList<>();
        for (String key : info.stringPropertyNames()) {
            if (!key.startsWith("cmdstat_")) continue;
            String raw = info.getProperty(key, "");
            long calls = parseMetric(raw, "calls");
            result.add(new MonitorCommandStatResponse(key.substring("cmdstat_".length()), calls));
        }
        result.sort(Comparator.comparingLong(MonitorCommandStatResponse::getCount).reversed());
        return result.stream().limit(12).collect(Collectors.toList());
    }

    private long parseMetric(String raw, String metric) {
        String prefix = metric + "=";
        for (String part : raw.split(",")) {
            if (part.startsWith(prefix)) {
                try { return Long.parseLong(part.substring(prefix.length())); } catch (NumberFormatException ignored) { return 0; }
            }
        }
        return 0;
    }

    private long activeSessionCount(Long factoryId) {
        return sessionRepository.findByFactoryIdAndKickedFalseAndExpiresAtAfterOrderByCreatedAtDesc(factoryId, Instant.now()).size();
    }

    private String value(Properties info, String key, String fallback) {
        String value = info == null ? null : info.getProperty(key);
        return hasText(value) ? value : fallback;
    }

    private long longInfo(Properties info, String key, long fallback) {
        try { return Long.parseLong(value(info, key, String.valueOf(fallback))); } catch (NumberFormatException ignored) { return fallback; }
    }

    private String markerKey(Long factoryId) { return REDIS_PREFIX + factoryId + ":initialized"; }
    private String indexKey(Long factoryId, String cacheName) { return REDIS_PREFIX + factoryId + ":index:" + cacheName; }
    private String valueKey(Long factoryId, String cacheName, String cacheKey) { return REDIS_PREFIX + factoryId + ":value:" + cacheName + ":" + cacheKey; }

    private void saveRecovery(Long factoryId, String scope, String cacheName, String cacheKey, Map<String, String> entries) {
        if (entries.isEmpty()) return;
        RecoverySnapshot snapshot = new RecoverySnapshot(snapshotSequence.incrementAndGet(), scope, cacheName, cacheKey, entries, Instant.now(), Instant.now().plus(recoveryTtl));
        List<RecoverySnapshot> snapshots = recoveries.computeIfAbsent(factoryId, ignored -> Collections.synchronizedList(new ArrayList<>()));
        snapshots.add(0, snapshot);
    }

    private MonitorOnlineUserResponse toOnlineResponse(UserSession session, String departmentName) {
        MonitorOnlineUserResponse response = new MonitorOnlineUserResponse();
        response.setId(session.getId());
        response.setSessionId("session-" + session.getId());
        response.setLoginName(session.getDisplayName());
        response.setDepartmentName(departmentName);
        response.setIpAddress(defaultValue(session.getIpAddress(), "-"));
        response.setLoginLocation(defaultValue(session.getLoginLocation(), "内网IP"));
        response.setOperatingSystem(defaultValue(session.getOperatingSystem(), "未知系统"));
        response.setBrowser(defaultValue(session.getBrowser(), "未知浏览器"));
        response.setLoginTime(session.getCreatedAt());
        return response;
    }

    private MonitorCacheRecoveryResponse toRecoveryResponse(RecoverySnapshot snapshot) {
        MonitorCacheRecoveryResponse response = new MonitorCacheRecoveryResponse();
        response.setId(snapshot.id);
        response.setScope(snapshot.scope);
        response.setCacheName(snapshot.cacheName);
        response.setCacheKey(snapshot.cacheKey);
        response.setEntryCount(snapshot.entries.size());
        response.setDeletedAt(toLocalDateTime(snapshot.deletedAt));
        response.setExpiresAt(toLocalDateTime(snapshot.expiresAt));
        return response;
    }

    private Map<String, Object> heapUsage() {
        MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("used", usage.getUsed());
        result.put("committed", usage.getCommitted());
        result.put("max", usage.getMax());
        return result;
    }

    private String humanBytes(long bytes) {
        if (bytes <= 0) return "0B";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0 * 100) / 100.0 + "KB";
        if (bytes < 1024L * 1024L * 1024L) return Math.round(bytes / 1024.0 / 1024.0 * 100) / 100.0 + "MB";
        return Math.round(bytes / 1024.0 / 1024.0 / 1024.0 * 100) / 100.0 + "GB";
    }

    private void count(String command) { commandStats.computeIfAbsent(command, ignored -> new AtomicLong()).incrementAndGet(); }
    private ApiException cacheNotFound(String cacheName) { return new ApiException(HttpStatus.NOT_FOUND, "缓存名称不存在：" + cacheName); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private boolean contains(String value, String query) { return value != null && value.toLowerCase().contains(query.trim().toLowerCase()); }
    private String defaultValue(String value, String fallback) { return hasText(value) ? value : fallback; }
    private LocalDateTime toLocalDateTime(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()); }
    private String splitName(String key) { return key.substring(0, key.indexOf("::")); }
    private String splitKey(String key) { return key.substring(key.indexOf("::") + 2); }

    private static final class RecoverySnapshot {
        private final Long id;
        private final String scope;
        private final String cacheName;
        private final String cacheKey;
        private final Map<String, String> entries;
        private final Instant deletedAt;
        private final Instant expiresAt;

        private RecoverySnapshot(Long id, String scope, String cacheName, String cacheKey, Map<String, String> entries, Instant deletedAt, Instant expiresAt) {
            this.id = id;
            this.scope = scope;
            this.cacheName = cacheName;
            this.cacheKey = cacheKey;
            this.entries = new LinkedHashMap<>(entries);
            this.deletedAt = deletedAt;
            this.expiresAt = expiresAt;
        }
    }
}
