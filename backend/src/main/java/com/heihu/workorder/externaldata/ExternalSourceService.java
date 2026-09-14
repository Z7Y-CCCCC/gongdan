package com.heihu.workorder.externaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.service.*;
import com.heihu.workorder.dto.SystemOperationLogRequest;
import com.heihu.workorder.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Scheduled;
import javax.validation.Validator;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Short local transactions only. External I/O always runs after snapshot transaction closes. */
@Service
public class ExternalSourceService {
    private static final List<String> VERIFIED_DATABASE_DRIVERS = Collections.singletonList("SQLITE");
    private static final List<String> PENDING_DATABASE_DRIVERS = Arrays.asList("MYSQL", "POSTGRESQL", "SQLSERVER");
    private final ExternalSourceRepository sources;
    private final SourceCheckRepository checks;
    private final SourceSecrets secrets;
    private final SourcePolicy policy;
    private final SourceProbe probe;
    private final ObjectMapper json;
    private final Validator validator;
    private final TransactionTemplate tx;
    private final SystemLogService logs;
    public ExternalSourceService(ExternalSourceRepository sources, SourceCheckRepository checks, SourceSecrets secrets,
        SourcePolicy policy, SourceProbe probe, ObjectMapper json, Validator validator,
        PlatformTransactionManager manager, SystemLogService logs) {
        this.sources=sources; this.checks=checks; this.secrets=secrets; this.policy=policy; this.probe=probe;
        this.json=json; this.validator=validator; this.tx=new TransactionTemplate(manager); this.logs=logs;
    }
    public Map<String,Object> capabilities(AuthenticatedUser u) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("secretsReady", secrets.ready()); result.put("sqliteFiles", policy.fileIds(u.getFactoryId()));
        result.put("databaseDrivers", VERIFIED_DATABASE_DRIVERS);
        result.put("pendingDatabaseDrivers", PENDING_DATABASE_DRIVERS);
        result.put("automaticChecks", false); result.put("businessDatasets", false); return result;
    }
    public Map<String,Object> list(AuthenticatedUser u, int page, boolean archived) {
        if (page < 0 || page > 1000) throw new IllegalArgumentException("页码无效");
        return tx.execute(status -> {
            Page<ExternalSource> found = sources.findByFactoryIdAndArchivedOrderByIdDesc(u.getFactoryId(), archived, PageRequest.of(page, 50));
            List<Map<String,Object>> items = new ArrayList<>();
            for (ExternalSource source : found) items.add(view(source));
            Map<String,Object> result = new LinkedHashMap<>(); result.put("items", items);
            result.put("total", found.getTotalElements()); result.put("hasMore", found.hasNext()); return result;
        });
    }
    public Map<String,Object> get(AuthenticatedUser u, Long id) { return tx.execute(status -> view(owned(u, id))); }
    private ExternalSource owned(AuthenticatedUser u, Long id) {
        return sources.findByIdAndFactoryId(id, u.getFactoryId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "数据源不存在"));
    }
    private ExternalSource active(AuthenticatedUser u, Long id) {
        ExternalSource s = owned(u,id);
        if (s.archived) throw new ApiException(HttpStatus.CONFLICT, "数据源已归档，请先恢复");
        return s;
    }
    private SourceConfig config(ExternalSource s) {
        try { return json.readValue(s.configJson, SourceConfig.class); }
        catch (Exception e) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "数据源配置无法读取"); }
    }
    private String context(ExternalSource s) { return s.factoryId + ":" + s.id; }
    private Map<String,Object> view(ExternalSource s) {
        Map<String,Object> v = new LinkedHashMap<>();
        v.put("id", s.id); v.put("version", s.version); v.put("enabled", s.enabled); v.put("archived", s.archived);
        v.put("config", config(s)); v.put("secretConfigured", s.secretCipher != null);
        v.put("updatedAt", s.updatedAt); v.put("createdAt", s.createdAt);
        SourceCheck last = checks.findFirstByFactoryIdAndSourceIdAndConfigVersionOrderByIdDesc(s.factoryId, s.id, s.version)
            .orElseGet(() -> checks.findFirstByFactoryIdAndSourceIdOrderByIdDesc(s.factoryId, s.id).orElse(null));
        v.put("lastCheck", last);
        v.put("health", last == null ? "UNKNOWN" :
            (!Objects.equals(last.configVersion, s.version) || last.startedAt.isBefore(Instant.now().minus(15, ChronoUnit.MINUTES))) ? "STALE" :
            ("CHECKING".equals(last.status) && last.startedAt.isBefore(Instant.now().minusSeconds(35))) ? "STALE" : last.status);
        return v;
    }
    private void validate(SourceConfig c, Long factoryId) {
        if (c == null || !validator.validate(c).isEmpty()) throw new IllegalArgumentException("请检查名称、编码、字段长度、超时及必填配置");
        // Compiling a JDBC adapter is not evidence of a verified service/TLS integration.
        if ("DATABASE".equals(c.kind) && !VERIFIED_DATABASE_DRIVERS.contains(c.driver))
            throw new IllegalArgumentException("该数据库适配尚未完成真实环境验收，暂不可创建、修改、启用或检查");
        policy.validate(c, factoryId);
    }
    private String secret(SourceConfig c, ExternalSource old) {
        if (!policy.needsSecret(c)) return "";
        if ("REPLACE".equals(c.secretAction)) {
            if (SourcePolicy.blank(c.secretValue)) throw new IllegalArgumentException("请填写新的凭据");
            return c.secretValue;
        }
        if ("CLEAR".equals(c.secretAction)) throw new IllegalArgumentException("当前认证方式需要凭据，不能清除");
        if (old == null || old.secretCipher == null) throw new IllegalArgumentException("请填写凭据并选择替换");
        SourceConfig before = config(old);
        if (!sameCredentialScope(before,c))
            throw new IllegalArgumentException("连接目标、认证配置或用户名变化后请重新填写凭据");
        return secrets.decrypt(old.secretCipher, context(old));
    }
    private boolean sameCredentialScope(SourceConfig before, SourceConfig after) {
        if (!Objects.equals(before.kind, after.kind)) return false;
        if ("HTTP".equals(after.kind)) {
            URI oldRoot = URI.create(before.baseUrl.trim()), newRoot = URI.create(after.baseUrl.trim());
            return Objects.equals(before.authType, after.authType)
                && Objects.equals(oldRoot.getScheme(), newRoot.getScheme())
                && oldRoot.getHost().equalsIgnoreCase(newRoot.getHost())
                && policy.port(oldRoot) == policy.port(newRoot)
                && rootPath(oldRoot).equals(rootPath(newRoot))
                && (!"API_KEY".equals(after.authType) || before.headerName.equalsIgnoreCase(after.headerName))
                && (!"BASIC".equals(after.authType) || Objects.equals(before.username, after.username));
        }
        return Objects.equals(before.driver, after.driver) && Objects.equals(before.host, after.host)
            && before.port == after.port && Objects.equals(before.database, after.database)
            && Objects.equals(before.schema, after.schema) && Objects.equals(before.username, after.username)
            && before.tls == after.tls;
    }
    private String rootPath(URI uri) { return uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", ""); }
    private String sanitized(SourceConfig c) {
        // Persist an allowlisted copy, never the request itself or ignored type-specific fields.
        SourceConfig clean = new SourceConfig();
        clean.code=c.code; clean.name=c.name; clean.description=c.description; clean.kind=c.kind; clean.timeoutSeconds=c.timeoutSeconds;
        if ("HTTP".equals(c.kind)) {
            clean.baseUrl=c.baseUrl; clean.healthPath=c.healthPath; clean.authType=c.authType;
            clean.headerName="API_KEY".equals(c.authType) ? c.headerName : "";
            clean.username="BASIC".equals(c.authType) ? c.username : "";
        } else {
            clean.driver=c.driver;
            if ("SQLITE".equals(c.driver)) clean.fileId=c.fileId;
            else { clean.host=c.host; clean.port=c.port; clean.database=c.database; clean.schema=c.schema; clean.username=c.username; clean.tls=c.tls; }
        }
        try { return json.writeValueAsString(clean); }
        catch (Exception e) { throw new IllegalArgumentException("配置格式无效"); }
    }
    public Map<String,Object> save(AuthenticatedUser u, Long id, SourceConfig c) {
        validate(c,u.getFactoryId());
        return tx.execute(status -> {
            ExternalSource s = id == null ? new ExternalSource() : active(u,id);
            if (id != null && !Objects.equals(c.version,s.version)) conflict();
            if (id != null && (!s.kind.equals(c.kind) || !s.code.equals(c.code) ||
                ("DATABASE".equals(c.kind) && !config(s).driver.equals(c.driver))))
                throw new IllegalArgumentException("编码和数据源类型不能修改，请新建数据源");
            if (id == null && (sources.countByFactoryIdAndArchivedFalse(u.getFactoryId()) >= 100 ||
                sources.existsByFactoryIdAndCode(u.getFactoryId(), c.code))) throw new ApiException(HttpStatus.CONFLICT, "编码已使用（含归档）或已达到 100 个数据源上限");
            String plain = secret(c, id == null ? null : s);
            s.factoryId=u.getFactoryId(); s.code=c.code; s.name=c.name; s.kind=c.kind;
            s.configJson=sanitized(c); s.updatedAt=Instant.now();
            if (s.createdAt == null) s.createdAt=s.updatedAt;
            // Persist identity first so authenticated encryption binds to an immutable source id.
            sources.saveAndFlush(s);
            s.secretCipher=policy.needsSecret(c) ? secrets.encrypt(plain,context(s)) : null;
            sources.saveAndFlush(s);
            audit(u,s,id == null ? "CREATE" : "UPDATE","配置已保存；凭据不写入日志");
            return view(s);
        });
    }
    public Map<String,Object> lifecycle(AuthenticatedUser u, Long id, Long version, String action) {
        return tx.execute(status -> {
            ExternalSource s=owned(u,id);
            if (!Objects.equals(s.version,version)) conflict();
            if (!Arrays.asList("enable","disable","archive","restore").contains(action)) throw new IllegalArgumentException("操作无效");
            if (s.archived && !"restore".equals(action)) throw new ApiException(HttpStatus.CONFLICT,"数据源已归档");
            if (!s.archived && "restore".equals(action)) throw new ApiException(HttpStatus.CONFLICT,"数据源尚未归档，无需恢复");
            if ("enable".equals(action)) {
                SourceConfig c=config(s); validate(c,u.getFactoryId()); secret(c,s); s.enabled=true;
            }
            if ("disable".equals(action) || "archive".equals(action) || "restore".equals(action)) s.enabled=false;
            if ("archive".equals(action)) s.archived=true;
            if ("restore".equals(action)) {
                if (sources.countByFactoryIdAndArchivedFalse(u.getFactoryId()) >= 100) throw new ApiException(HttpStatus.CONFLICT,"数据源数量已达上限");
                s.archived=false;
            }
            s.updatedAt=Instant.now(); sources.saveAndFlush(s); audit(u,s,action.toUpperCase(Locale.ROOT),"生命周期变更");
            return view(s);
        });
    }
    public SourceCheck testDraft(AuthenticatedUser u, SourceConfig c) {
        validate(c,u.getFactoryId());
        String plain=tx.execute(status -> {
            ExternalSource old=c.sourceId==null?null:active(u,c.sourceId);
            if (old!=null && !Objects.equals(old.version,c.version)) conflict();
            String result=secret(c,old);
            audit(u,old,"TEST_DRAFT","测试未保存配置，不更新已保存健康状态");
            return result;
        });
        return probe.test(c,plain,u.getFactoryId());
    }
    public SourceCheck testSaved(AuthenticatedUser u, Long id) {
        ExternalSource snapshot=tx.execute(status -> {
            ExternalSource s=active(u,id); validate(config(s),u.getFactoryId()); return s;
        });
        SourceConfig c=config(snapshot); String plain=secret(c,snapshot);
        SourceCheck check=tx.execute(status -> {
            SourceCheck r=new SourceCheck(); r.factoryId=u.getFactoryId(); r.sourceId=id; r.configVersion=snapshot.version;
            audit(u,snapshot,"TEST","手动检查已保存配置"); return checks.saveAndFlush(r);
        });
        SourceCheck result;
        try { result=probe.test(c,plain,u.getFactoryId()); }
        catch (ApiException e) {
            final SourceCheck denied=SourceProbe.outcome("FAILED","LOCAL_LIMIT","本系统检查资源繁忙或请求受限",null);
            complete(check,denied);
            throw e;
        }
        return complete(check,result);
    }
    private SourceCheck complete(SourceCheck check, SourceCheck result) {
        return tx.execute(status -> {
            check.status=result.status; check.errorCode=result.errorCode; check.message=result.message;
            check.httpStatus=result.httpStatus; check.durationMs=result.durationMs; check.finishedAt=Instant.now();
            return checks.saveAndFlush(check);
        });
    }
    public Page<SourceCheck> history(AuthenticatedUser u, Long id, int page) {
        if (page < 0 || page > 1000) throw new IllegalArgumentException("页码无效");
        return tx.execute(status -> { owned(u,id); return checks.findByFactoryIdAndSourceIdOrderByIdDesc(u.getFactoryId(),id,PageRequest.of(page,50)); });
    }
    public Map<String,Object> schema(AuthenticatedUser u, Long id, String table, int page) {
        ExternalSource s=tx.execute(status -> { ExternalSource r=active(u,id); audit(u,r,"SCHEMA","查看只读表字段元数据"); return r; });
        SourceConfig c=config(s); validate(c,u.getFactoryId());
        return probe.schema(c,secret(c,s),u.getFactoryId(),table,page);
    }
    private void audit(AuthenticatedUser u, ExternalSource s, String action, String detail) {
        SystemOperationLogRequest r=new SystemOperationLogRequest();
        r.setModule("DATA_SOURCE"); r.setActionType(action); r.setTargetCode(s==null?"DRAFT":s.code);
        r.setTargetName(s==null?"未保存数据源":s.name); r.setDetail(detail);
        logs.recordOperation(u.getFactoryId(),u.getName(),r);
    }
    private void conflict() { throw new ApiException(HttpStatus.CONFLICT,"配置已被修改，请刷新后重试"); }
    @Scheduled(cron = "0 15 4 * * *")
    public void cleanup() { tx.execute(status -> checks.deleteByStartedAtBefore(Instant.now().minus(30,ChronoUnit.DAYS))); }
}
