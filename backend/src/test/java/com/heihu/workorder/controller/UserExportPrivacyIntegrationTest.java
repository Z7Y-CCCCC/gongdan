package com.heihu.workorder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:export_privacy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class UserExportPrivacyIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired FactoryRepository factories;
    @Autowired TokenService tokens;

    @Test
    void businessExportDoesNotSendPasswordHashesWhileExplicitBackupRemainsLossless() throws Exception {
        String login = http.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"factoryCode\":\"TEST\",\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<?, ?> session = json.readValue(login, Map.class);
        String token = String.valueOf(session.get("token"));
        Number id = (Number) ((Map<?, ?>) session.get("user")).get("id");
        FactoryUser actor = users.findById(id.longValue()).get();
        String business = http.perform(get("/api/users/export").param("includePasswordHash", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(business.contains("密码哈希")).isFalse();
        assertThat(business.contains(actor.getPasswordHash())).isFalse();
        assertThat(business.contains(actor.getUsername())).isTrue();
        String backup = http.perform(get("/api/users/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(backup.contains("密码哈希")).isTrue();
        assertThat(backup.contains(actor.getPasswordHash())).isTrue();
        String explicit = http.perform(get("/api/users/export").param("includePasswordHash", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(explicit).isEqualTo(backup);
    }

    @Test
    void omittingSensitiveColumnsDoesNotTurnExportIntoAnAnonymousEndpoint() throws Exception {
        http.perform(get("/api/users/export").param("includePasswordHash", "false"))
                .andExpect(status().isUnauthorized());
        http.perform(get("/api/users/export").param("includePasswordHash", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void neitherExportModeAllowsProductionUsersToObtainUserRecords() throws Exception {
        Factory factory = factories.findByCode("TEST").get();
        FactoryUser worker = createUser(factory, "export-worker", "Private worker", Role.PRODUCTION);
        String token = tokens.issue(worker);
        for (String mode : new String[]{"false", "true"}) {
            http.perform(get("/api/users/export").param("includePasswordHash", mode)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void bothModesKeepFactoryBoundariesAndPreserveBusinessTextWithoutFormulaPrefixes() throws Exception {
        Factory factory = factories.findByCode("TEST").get();
        FactoryUser admin = users.findByFactoryIdAndUsername(factory.getId(), "admin").get();
        FactoryUser local = createUser(factory, "export-text", "=1+1,\n\"保存\"", Role.PRODUCTION);
        Factory other = new Factory();
        other.setCode("EXPORT-OTHER"); other.setName("Other private fixture");
        other.setEnabled(true); other.setCreatedAt(LocalDateTime.now());
        other = factories.save(other);
        FactoryUser foreign = createUser(other, "foreign-export-private", "Foreign private text", Role.ADMIN);
        String token = tokens.issue(admin);
        for (String mode : new String[]{"false", "true"}) {
            String csv = http.perform(get("/api/users/export").param("includePasswordHash", mode)
                            .param("factoryId", other.getId().toString()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(csv).startsWith("\uFEFF账号,姓名,角色,手机号,部门,");
            assertThat(csv).contains(local.getUsername(), "\"=1+1,\n\"\"保存\"\"\"");
            assertThat(csv).doesNotContain(foreign.getUsername(), foreign.getName());
            if ("false".equals(mode)) assertThat(csv).doesNotContain(local.getPasswordHash(), admin.getPasswordHash());
            else assertThat(csv).contains(local.getPasswordHash());
        }
    }

    private FactoryUser createUser(Factory factory, String username, String name, Role role) {
        FactoryUser user = new FactoryUser();
        user.setFactory(factory); user.setUsername(username); user.setName(name); user.setRole(role);
        user.setPasswordHash("fixture-hash-" + username); user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        return users.save(user);
    }
}
