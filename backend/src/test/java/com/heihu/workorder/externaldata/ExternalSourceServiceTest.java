package com.heihu.workorder.externaldata;

import com.heihu.workorder.service.*;
import com.heihu.workorder.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "app.external-data.keys=test:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.external-data.active-key=test",
    "app.external-data.allowed-targets=1@example.com:443,1@other.example.com:443,1@example.com:8443,2@example.com:443"
})
@ActiveProfiles("test")
@Import({ExternalSourceService.class,SourcePolicy.class,SourceSecrets.class,ObjectMapper.class})
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ExternalSourceServiceTest {
    @Autowired ExternalSourceService service;
    @Autowired ExternalSourceRepository sources;
    @Autowired SourceCheckRepository checks;
    @MockBean SourceProbe probe;
    @MockBean SystemLogService logs;
    private final AuthenticatedUser a=new AuthenticatedUser(1L,1L,"A",Role.ADMIN,"Admin A");
    private final AuthenticatedUser b=new AuthenticatedUser(2L,2L,"B",Role.ADMIN,"Admin B");
    @BeforeEach void clear() { checks.deleteAll(); sources.deleteAll(); reset(probe,logs); }
    SourceConfig config() {
        SourceConfig c=new SourceConfig(); c.code="mes"; c.name="MES"; c.baseUrl="https://example.com"; c.healthPath="/health";
        c.authType="BEARER"; c.secretAction="REPLACE"; c.secretValue="secret-token"; return c;
    }
    @Test void savesEncryptedSecretAndNeverReturnsPlaintext() {
        Map<String,Object> v=service.save(a,null,config());
        ExternalSource stored=sources.findById((Long)v.get("id")).get();
        assertThat(stored.configJson).doesNotContain("secret-token");
        assertThat(stored.secretCipher).isNotBlank().doesNotContain("secret-token");
        SourceConfig returned=(SourceConfig)v.get("config");
        assertThat(returned.secretValue).isEmpty(); assertThat(v.get("secretConfigured")).isEqualTo(true);
        assertThat(v.get("enabled")).isEqualTo(false); verifyNoInteractions(probe);
    }
    @Test void tenantIsolationAppliesToEveryIdOperationAndDraftCredentialReuse() {
        Map<String,Object> v=service.save(a,null,config()); Long id=(Long)v.get("id");
        assertThat(service.list(b,0,false).get("total")).isEqualTo(0L);
        assertThatThrownBy(() -> service.get(b,id)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.testSaved(b,id)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.history(b,id,0)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.schema(b,id,"",0)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.lifecycle(b,id,(Long)v.get("version"),"archive")).hasMessageContaining("不存在");
        SourceConfig c=config(); c.sourceId=id; c.version=(Long)v.get("version"); c.secretAction="KEEP"; c.secretValue="";
        assertThatThrownBy(() -> service.testDraft(b,c)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.save(b,id,c)).hasMessageContaining("不存在");
        verifyNoInteractions(probe);
    }
    @Test void failedCheckDoesNotUndoSavedConfiguration() {
        Map<String,Object> v=service.save(a,null,config()); Long id=(Long)v.get("id");
        when(probe.test(any(),anyString(),eq(1L))).thenReturn(SourceProbe.outcome("FAILED","AUTH","认证失败",401));
        assertThat(service.testSaved(a,id).httpStatus).isEqualTo(401);
        assertThat(service.get(a,id).get("health")).isEqualTo("FAILED"); assertThat(sources.count()).isEqualTo(1);
    }
    @Test void editInvalidatesOldChecksAndRejectsStaleVersion() {
        Map<String,Object> v=service.save(a,null,config()); Long id=(Long)v.get("id");
        when(probe.test(any(),anyString(),eq(1L))).thenReturn(SourceProbe.outcome("UP",null,"可达",200));
        service.testSaved(a,id);
        SourceConfig changed=config(); changed.name="Updated"; changed.version=(Long)v.get("version"); changed.secretAction="KEEP"; changed.secretValue="";
        Map<String,Object> updated=service.save(a,id,changed);
        assertThat(updated.get("health")).isEqualTo("STALE");
        assertThatThrownBy(() -> service.save(a,id,changed)).hasMessageContaining("已被修改");
    }
    @Test void draftTestDoesNotSaveOrOverwriteHealth() {
        Map<String,Object> v=service.save(a,null,config()); Long id=(Long)v.get("id");
        SourceConfig c=config(); c.sourceId=id; c.version=(Long)v.get("version");
        when(probe.test(any(),anyString(),eq(1L))).thenReturn(SourceProbe.outcome("UP",null,"可达",200));
        service.testDraft(a,c);
        assertThat(checks.count()).isZero(); assertThat(service.get(a,id).get("health")).isEqualTo("UNKNOWN"); assertThat(sources.count()).isEqualTo(1);
    }
    @Test void switchingToAnonymousClearsSecretAndUnrelatedConfiguration() {
        Map<String,Object> v=service.save(a,null,config()); Long id=(Long)v.get("id");
        SourceConfig c=config(); c.version=(Long)v.get("version"); c.authType="NONE"; c.secretValue="must-not-persist";
        c.host="unrelated"; c.username="old-user";
        Map<String,Object> updated=service.save(a,id,c);
        ExternalSource s=sources.findById(id).get();
        assertThat(s.secretCipher).isNull(); assertThat(s.configJson).doesNotContain("must-not-persist","old-user","unrelated");
        assertThat(updated.get("secretConfigured")).isEqualTo(false);
    }
    @Test void archiveIsRecoverableAndCannotBeTested() {
        Map<String,Object> v=service.save(a,null,config()); Long id=(Long)v.get("id");
        Map<String,Object> archived=service.lifecycle(a,id,(Long)v.get("version"),"archive");
        assertThat(service.list(a,0,false).get("total")).isEqualTo(0L);
        assertThat(service.list(a,0,true).get("total")).isEqualTo(1L);
        assertThatThrownBy(() -> service.testSaved(a,id)).hasMessageContaining("已归档");
        Map<String,Object> restored=service.lifecycle(a,id,(Long)archived.get("version"),"restore");
        assertThat(restored.get("archived")).isEqualTo(false); assertThat(restored.get("enabled")).isEqualTo(false);
    }
    @Test void unknownBodyFieldsRejectedWithoutEchoingValues() {
        assertThatThrownBy(() -> new ObjectMapper().readValue("{\"unexpected\":\"secret-token\"}",SourceConfig.class))
            .hasMessageContaining("不支持的配置字段");
    }
    @Test void lateOlderVersionCannotHideCurrentHealth() {
        Map<String,Object> v=service.save(a,null,config()); Long id=(Long)v.get("id");
        when(probe.test(any(),anyString(),eq(1L))).thenReturn(SourceProbe.outcome("UP",null,"可达",200));
        service.testSaved(a,id);
        SourceCheck old=new SourceCheck(); old.factoryId=1L; old.sourceId=id; old.configVersion=-1L; old.status="FAILED";
        checks.saveAndFlush(old);
        assertThat(service.get(a,id).get("health")).isEqualTo("UP");
    }
    @Test void capabilitiesOnlyEnableDriversWithRealIntegrationCoverage() {
        assertThat(service.capabilities(a).get("databaseDrivers")).isEqualTo(Collections.singletonList("SQLITE"));
        assertThat(service.capabilities(a).get("pendingDatabaseDrivers")).isEqualTo(Arrays.asList("MYSQL","POSTGRESQL","SQLSERVER"));
        for (String driver : Arrays.asList("MYSQL","POSTGRESQL","SQLSERVER")) {
            SourceConfig c=config(); c.kind="DATABASE"; c.driver=driver;
            assertThatThrownBy(() -> service.save(a,null,c)).hasMessageContaining("尚未完成真实环境验收");
            assertThatThrownBy(() -> service.testDraft(a,c)).hasMessageContaining("尚未完成真实环境验收");
        }
        assertThat(sources.count()).isZero(); verifyNoInteractions(probe);
    }
    @Test void legacyUnverifiedDatabaseCanBeViewedAndDisabledButCannotDispatch() throws Exception {
        Map<String,Object> saved=service.save(a,null,config()); Long id=(Long)saved.get("id");
        ExternalSource legacy=sources.findById(id).get();
        SourceConfig c=config(); c.kind="DATABASE"; c.driver="MYSQL"; c.host="10.1.2.3"; c.database="plant";
        c.secretAction="KEEP"; c.secretValue="";
        legacy.kind="DATABASE"; legacy.configJson=new ObjectMapper().writeValueAsString(c); legacy.enabled=true;
        legacy=sources.saveAndFlush(legacy); c.version=legacy.version; c.sourceId=id;
        assertThat(service.get(a,id).get("enabled")).isEqualTo(true);
        assertThatThrownBy(() -> service.save(a,id,c)).hasMessageContaining("尚未完成真实环境验收");
        assertThatThrownBy(() -> service.testDraft(a,c)).hasMessageContaining("尚未完成真实环境验收");
        assertThatThrownBy(() -> service.testSaved(a,id)).hasMessageContaining("尚未完成真实环境验收");
        assertThatThrownBy(() -> service.schema(a,id,"",0)).hasMessageContaining("尚未完成真实环境验收");
        assertThatThrownBy(() -> service.lifecycle(a,id,c.version,"enable")).hasMessageContaining("尚未完成真实环境验收");
        Map<String,Object> disabled=service.lifecycle(a,id,c.version,"disable");
        assertThat(disabled.get("enabled")).isEqualTo(false);
        assertThat(service.lifecycle(a,id,(Long)disabled.get("version"),"archive").get("archived")).isEqualTo(true);
        assertThat(checks.count()).isZero(); verifyNoInteractions(probe);
    }
    @Test void keptCredentialsCannotBeRedirectedToAnotherApprovedTargetOrRoot() {
        Map<String,Object> saved=service.save(a,null,config()); Long id=(Long)saved.get("id");
        for (String target : Arrays.asList("https://other.example.com","https://example.com:8443","https://example.com/another-root")) {
            SourceConfig c=config(); c.sourceId=id; c.version=(Long)saved.get("version");
            c.secretAction="KEEP"; c.secretValue=""; c.baseUrl=target;
            assertThatThrownBy(() -> service.testDraft(a,c)).hasMessageContaining("请重新填写凭据");
            assertThatThrownBy(() -> service.save(a,id,c)).hasMessageContaining("请重新填写凭据");
        }
        assertThat(sources.findById(id).get().version).isEqualTo(saved.get("version"));
        verifyNoInteractions(probe);
    }
    @Test void keptCredentialsAllowEquivalentRootAndHealthPathChanges() {
        SourceConfig initial=config(); initial.authType="API_KEY"; initial.headerName="X-Api-Key";
        initial.baseUrl="https://example.com/api/";
        Map<String,Object> saved=service.save(a,null,initial); Long id=(Long)saved.get("id");
        SourceConfig c=config(); c.sourceId=id; c.version=(Long)saved.get("version"); c.authType="API_KEY";
        c.headerName="x-api-key"; c.baseUrl="https://EXAMPLE.com:443/api"; c.healthPath="/ready";
        c.secretAction="KEEP"; c.secretValue="";
        when(probe.test(any(),anyString(),eq(1L))).thenReturn(SourceProbe.outcome("UP",null,"可达",200));
        assertThat(service.testDraft(a,c).status).isEqualTo("UP");
        verify(probe).test(c,"secret-token",1L);
        assertThat(service.save(a,id,c).get("secretConfigured")).isEqualTo(true);
    }
    @Test void changingAuthenticationHeaderRequiresNewCredential() {
        SourceConfig initial=config(); initial.authType="API_KEY"; initial.headerName="X-Api-Key";
        Map<String,Object> saved=service.save(a,null,initial);
        SourceConfig c=config(); c.sourceId=(Long)saved.get("id"); c.version=(Long)saved.get("version");
        c.authType="API_KEY"; c.headerName="X-Different-Key"; c.secretAction="KEEP"; c.secretValue="";
        assertThatThrownBy(() -> service.testDraft(a,c)).hasMessageContaining("请重新填写凭据");
        verifyNoInteractions(probe);
    }
    @Test void replacementCredentialAllowsIntentionalTargetChange() {
        Map<String,Object> saved=service.save(a,null,config()); Long id=(Long)saved.get("id");
        SourceConfig changed=config(); changed.version=(Long)saved.get("version");
        changed.baseUrl="https://other.example.com"; changed.secretValue="other-target-token";
        assertThat(service.save(a,id,changed).get("secretConfigured")).isEqualTo(true);
        when(probe.test(any(),anyString(),eq(1L))).thenReturn(SourceProbe.outcome("UP",null,"可达",200));
        service.testSaved(a,id);
        verify(probe).test(any(),eq("other-target-token"),eq(1L));
    }
    @Test void restoringAnUnarchivedSourceDoesNotSilentlyDisableIt() {
        Map<String,Object> saved=service.save(a,null,config()); Long id=(Long)saved.get("id");
        Map<String,Object> enabled=service.lifecycle(a,id,(Long)saved.get("version"),"enable");
        assertThatThrownBy(() -> service.lifecycle(a,id,(Long)enabled.get("version"),"restore")).hasMessageContaining("尚未归档");
        assertThat(service.get(a,id).get("enabled")).isEqualTo(true);
    }
    @Test void olderConcurrentCheckCompletingLastCannotReplaceNewerSameVersionResult() throws Exception {
        Map<String,Object> saved=service.save(a,null,config()); Long id=(Long)saved.get("id");
        CountDownLatch firstStarted=new CountDownLatch(1), releaseFirst=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        when(probe.test(any(),anyString(),eq(1L))).thenAnswer(invocation -> {
            if (calls.incrementAndGet()==1) {
                firstStarted.countDown();
                if (!releaseFirst.await(5,TimeUnit.SECONDS)) throw new AssertionError("first check was not released");
                return SourceProbe.outcome("FAILED","NETWORK","旧检查失败",null);
            }
            return SourceProbe.outcome("UP",null,"新检查可达",200);
        });
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<SourceCheck> first=executor.submit(() -> service.testSaved(a,id));
            assertThat(firstStarted.await(5,TimeUnit.SECONDS)).isTrue();
            SourceCheck latest=service.testSaved(a,id);
            releaseFirst.countDown();
            assertThat(first.get(5,TimeUnit.SECONDS).status).isEqualTo("FAILED");
            Map<String,Object> current=service.get(a,id);
            assertThat(current.get("health")).isEqualTo("UP");
            assertThat(((SourceCheck)current.get("lastCheck")).id).isEqualTo(latest.id);
        } finally { releaseFirst.countDown(); executor.shutdownNow(); }
    }
    @Test void lifecycleVersionChangeInvalidatesAnInFlightCheck() throws Exception {
        Map<String,Object> saved=service.save(a,null,config()); Long id=(Long)saved.get("id");
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        when(probe.test(any(),anyString(),eq(1L))).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("check was not released");
            return SourceProbe.outcome("UP",null,"旧版本可达",200);
        });
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<SourceCheck> check=executor.submit(() -> service.testSaved(a,id));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            service.lifecycle(a,id,(Long)saved.get("version"),"archive");
            release.countDown(); check.get(5,TimeUnit.SECONDS);
            Map<String,Object> archived=service.get(a,id);
            assertThat(archived.get("archived")).isEqualTo(true);
            assertThat(archived.get("health")).isEqualTo("STALE");
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
