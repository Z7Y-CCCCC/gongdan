package com.heihu.workorder.controller;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.UserSession;
import com.heihu.workorder.report.UReportAuthFilter;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import com.heihu.workorder.service.TokenService;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ordinary private H2 sessions, MVC requests, and a mock filter chain; no vendor servlet actions. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth_logout;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthLogoutIntegrationTest {
    @Autowired MockMvc http;
    @Autowired FactoryRepository factories;
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired TokenService tokens;

    @Test
    void anonymousLogoutIsIdempotentAndDoesNotOverwriteAnyBrowserCookie() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            http.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    @Test
    void anAmbientReportCookieIsNotUsedAsLogoutAuthorization() throws Exception {
        FactoryUser actor = user(factories.findByCode("ADMIN").get(), "cookie-only", Role.ADMIN);
        String token = tokens.issue(actor);

        http.perform(post("/api/auth/logout").cookie(new Cookie(UReportAuthFilter.SESSION_COOKIE, token)))
                .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(tokens.requireUser("Bearer " + token).getUserId()).isEqualTo(actor.getId());
        assertReportCookie(token, 200);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "PRODUCTION"})
    void eitherAuthenticatedRoleCanRevokeItsOwnExactSession(Role role) throws Exception {
        FactoryUser actor = user(factory("LOGOUT-ROLE"), "actor", role);
        String token = tokens.issue(actor);
        assertApiSession(token, 200);

        logout(token);

        assertApiSession(token, 401);
        assertThat(sessions.findByUserIdOrderByCreatedAtDesc(actor.getId())).isEmpty();
    }

    @Test
    void logoutLeavesAnotherPrivateFactoryUsersSessionUnaffected() throws Exception {
        FactoryUser actor = user(factory("LOGOUT-A"), "actor", Role.ADMIN);
        FactoryUser foreign = user(factory("LOGOUT-B"), "foreign", Role.PRODUCTION);
        String token = tokens.issue(actor);
        String other = tokens.issue(foreign);

        logout(token);

        assertApiSession(token, 401);
        assertApiSession(other, 200);
        assertThat(tokens.requireUser("Bearer " + other).getUserId()).isEqualTo(foreign.getId());
    }

    @Test
    void logoutOfAnOlderKickedTokenCannotKickANewerLogin() throws Exception {
        FactoryUser actor = user(factory("LOGOUT-ROTATE"), "actor", Role.ADMIN);
        String older = tokens.issue(actor);
        String newer = tokens.issue(actor);

        logout(older);
        logout(older);

        assertApiSession(older, 401);
        assertApiSession(newer, 200);
        assertThat(sessions.findByUserIdAndKickedFalse(actor.getId())).hasSize(1);
    }

    @Test
    void expiredRepeatedAndUnknownTokensCanBeLoggedOutWithoutAValidUserSession() throws Exception {
        Factory fixture = factory("LOGOUT-EXPIRED");
        String survivor = tokens.issue(user(fixture, "survivor", Role.PRODUCTION));
        FactoryUser actor = user(fixture, "expired", Role.ADMIN);
        String token = tokens.issue(actor);
        UserSession expired = sessions.findByUserIdOrderByCreatedAtDesc(actor.getId()).get(0);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        sessions.saveAndFlush(expired);

        logout(token);
        logout(token);
        logout("fixture-unknown");

        assertThat(sessions.findByUserIdOrderByCreatedAtDesc(actor.getId())).isEmpty();
        assertApiSession(token, 401);
        assertApiSession(survivor, 200);
    }

    @Test
    void theOldReportCookieIsRejectedByTheFilterAfterItsExactTokenIsLoggedOut() throws Exception {
        FactoryUser actor = user(factories.findByCode("ADMIN").get(), "report-old", Role.ADMIN);
        String token = tokens.issue(actor);
        assertReportCookie(token, 200);

        logout(token);

        assertReportCookie(token, 401);
        http.perform(get("/api/ureport/session").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLateOldLogoutResponseCannotOverwriteANewerEstablishedReportCookie() throws Exception {
        FactoryUser actor = user(factories.findByCode("ADMIN").get(), "report-new", Role.ADMIN);
        String older = tokens.issue(actor);
        String newer = tokens.issue(actor);
        String newCookie = http.perform(get("/api/ureport/session")
                        .header("Authorization", "Bearer " + newer).secure(true))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(newCookie).contains(UReportAuthFilter.SESSION_COOKIE + "=" + newer,
                "Path=/ureport", "HttpOnly", "SameSite=Lax", "Secure");

        logout(older);

        assertApiSession(newer, 200);
        assertReportCookie(newer, 200);
        assertReportCookie(older, 401);
    }

    private void logout(String token) throws Exception {
        http.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    private void assertApiSession(String token, int expectedStatus) throws Exception {
        http.perform(get("/api/mobile/work-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private void assertReportCookie(String token, int expectedStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ureport/designer");
        request.setCookies(new Cookie(UReportAuthFilter.SESSION_COOKIE, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        if (expectedStatus == 200) assertThat(chain.getRequest()).isSameAs(request);
        else assertThat(chain.getRequest()).isNull();
    }

    private Factory factory(String code) {
        Factory factory = new Factory();
        factory.setCode(code); factory.setName("Private logout fixture");
        factory.setEnabled(true); factory.setCreatedAt(LocalDateTime.now());
        return factories.save(factory);
    }

    private FactoryUser user(Factory factory, String username, Role role) {
        FactoryUser user = new FactoryUser();
        user.setFactory(factory); user.setUsername(username); user.setName("Fixture " + username);
        user.setRole(role); user.setPasswordHash("fixture-hash-" + username); user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        return users.save(user);
    }
}
