package com.heihu.workorder.controller;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.report.UReportAuthFilter;
import com.heihu.workorder.report.UReportController;
import com.heihu.workorder.service.AuthService;
import com.heihu.workorder.service.TokenService;
import com.heihu.workorder.util.ClientIpResolver;
import java.util.Collections;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Local controller/filter fixtures only; no vendor template or external proxy operations. */
class UReportCookieSecurityTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theDefaultAllowsLocalHttpButSecuresDirectHttps(boolean directTls) {
        TokenService tokens = new TokenService();
        String token = tokens.issue(platformUser());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(directTls);

        String cookie = establish(controller(tokens, null), token, request);

        assertCookie(cookie, directTls);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anExplicitDeploymentSettingSecuresBothTerminatedAndDirectTls(boolean directTls) {
        TokenService tokens = new TokenService();
        String token = tokens.issue(platformUser());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(directTls);

        String cookie = establish(controller(tokens, true), token, request);

        assertCookie(cookie, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https", "http", "HTTPS"})
    void untrustedForwardedHeadersDoNotEnableSecureCookies(String forwardedProto) {
        TokenService tokens = new TokenService();
        String token = tokens.issue(platformUser());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", forwardedProto);
        request.addHeader("Forwarded", "for=192.0.2.17;proto=https");

        String cookie = establish(controller(tokens, false), token, request);

        assertCookie(cookie, false);
    }

    @Test
    void untrustedHttpHeadersCannotDisableTheExplicitDeploymentSetting() {
        TokenService tokens = new TokenService();
        String token = tokens.issue(platformUser());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "http");
        request.addHeader("Forwarded", "for=192.0.2.17;proto=http");

        assertCookie(establish(controller(tokens, true), token, request), true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anExpiredOrRevokedReportSessionNeverWritesOrClearsACookie(boolean forcedSecure) throws Exception {
        TokenService tokens = mock(TokenService.class);
        when(tokens.requireSuperAdmin("Bearer expired-fixture"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期"));
        MockHttpServletResponse bridgeResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller(tokens, forcedSecure).establishSession(
                "Bearer expired-fixture", new MockHttpServletRequest(), bridgeResponse))
                .isInstanceOf(ApiException.class);
        assertThat(bridgeResponse.getHeader("Set-Cookie")).isNull();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ureport/designer");
        request.setCookies(new Cookie(UReportAuthFilter.SESSION_COOKIE, "expired-fixture"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aLateLogoutDoesNotClearTheNewCookieUnderEitherDeploymentSetting(boolean forcedSecure) throws Exception {
        TokenService tokens = new TokenService();
        FactoryUser actor = platformUser();
        String oldToken = tokens.issue(actor);
        String newToken = tokens.issue(actor);
        String newCookie = establish(controller(tokens, forcedSecure), newToken, new MockHttpServletRequest());
        assertCookie(newCookie, forcedSecure);

        AuthService auth = mock(AuthService.class);
        doAnswer(call -> { tokens.revoke(call.getArgument(0)); return null; }).when(auth).logout("Bearer " + oldToken);
        MockMvcBuilders.standaloneSetup(new AuthController(auth)).build()
                .perform(post("/api/auth/logout").header("Authorization", "Bearer " + oldToken)
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(tokens.requireSuperAdmin("Bearer " + newToken).getUserId()).isEqualTo(actor.getId());
        assertThatThrownBy(() -> tokens.requireUser("Bearer " + oldToken)).isInstanceOf(ApiException.class);
    }

    private UReportController controller(TokenService tokens, Boolean forcedSecure) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            if (forcedSecure != null) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("cookie-fixture",
                        Collections.<String, Object>singletonMap("app.ureport.cookie-secure", forcedSecure)));
            }
            context.registerBean(ClientIpResolver.class, () -> new ClientIpResolver(""));
            context.registerBean(TokenService.class, () -> tokens);
            context.register(UReportController.class);
            context.refresh();
            return context.getBean(UReportController.class);
        }
    }

    private String establish(UReportController controller, String token, MockHttpServletRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(controller.establishSession("Bearer " + token, request, response).getHeaders().getCacheControl())
                .isEqualTo("no-store");
        return response.getHeader("Set-Cookie");
    }

    private void assertCookie(String cookie, boolean secure) {
        assertThat(cookie).contains("Max-Age=3600", "Path=/ureport", "HttpOnly", "SameSite=Lax");
        if (secure) assertThat(cookie).contains("; Secure");
        else assertThat(cookie).doesNotContain("; Secure");
    }

    private FactoryUser platformUser() {
        Factory factory = new Factory();
        factory.setId(1L); factory.setCode("ADMIN"); factory.setEnabled(true);
        FactoryUser actor = new FactoryUser();
        actor.setId(101L); actor.setFactory(factory); actor.setRole(Role.ADMIN);
        actor.setName("Platform cookie fixture"); actor.setEnabled(true);
        return actor;
    }
}
