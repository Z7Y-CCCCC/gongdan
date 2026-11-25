package com.heihu.workorder.controller;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.report.UReportAuthFilter;
import com.heihu.workorder.report.UReportController;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.TokenService;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Cookie and role boundaries only: no vendor servlet, XML, SQL, or template file operations. */
class UReportAccessBoundaryTest {
    private static final String FIXTURE = "00000000000000000000000000000001";

    @ParameterizedTest @ValueSource(strings = {"/ureport", "/ureport/", "/ureport/designer", "/ureport/preview"})
    void reportServletPathsRequireAReportSessionCookie(String path) throws Exception {
        TokenService tokens = mock(TokenService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); verifyNoInteractions(tokens);
    }

    @ParameterizedTest @ValueSource(longs = {11L, 12L})
    void ordinaryAdminsFromEitherPrivateFactoryCannotEnterTheNativeReportServlet(long factoryId) throws Exception {
        TokenService tokens = new TokenService();
        String token = tokens.issue(actor(factoryId, "PRIVATE-" + factoryId, Role.ADMIN));
        MockHttpServletRequest request = requestWithCookie(token); MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test void productionRoleCannotEnterTheReportServletWithACookie() throws Exception {
        TokenService tokens = new TokenService();
        String token = tokens.issue(actor(11L, "PRIVATE-11", Role.PRODUCTION));
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(requestWithCookie(token), response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest @ValueSource(longs = {11L, 12L})
    void privateFactoryAdminsCannotEstablishANativeReportSession(long factoryId) {
        TokenService tokens = new TokenService();
        String token = tokens.issue(actor(factoryId, "PRIVATE-" + factoryId, Role.ADMIN));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new UReportController(tokens).establishSession("Bearer " + token, request, response))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test void sessionEstablishmentDoesNotAllowProductionRole() {
        TokenService tokens = new TokenService();
        String token = tokens.issue(actor(11L, "PRIVATE-11", Role.PRODUCTION));
        assertThatThrownBy(() -> new UReportController(tokens).establishSession("Bearer " + token,
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void theReportFilterProtectsANormalNonRootContextPath() throws Exception {
        TokenService tokens = mock(TokenService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fixture/ureport/designer");
        request.setContextPath("/fixture");
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); verifyNoInteractions(tokens);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/units", "/ureporting", "/ureport-assets/file.js"})
    void ordinaryNonReportPathsAreLeftToTheirOwnController(String path) throws Exception {
        TokenService tokens = mock(TokenService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(request); verifyNoInteractions(tokens);
    }

    @Test void platformSuperadminCanEnterTheNativeReportServlet() throws Exception {
        TokenService tokens = mock(TokenService.class);
        when(tokens.requireSuperAdmin("Bearer " + FIXTURE)).thenReturn(
                new AuthenticatedUser(101L, 1L, "ADMIN", Role.ADMIN, "Platform fixture"));
        MockHttpServletRequest request = requestWithCookie(FIXTURE);
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        verify(tokens).requireSuperAdmin("Bearer " + FIXTURE);
    }

    @Test void platformSuperadminSessionKeepsTheHttpOnlySecureCookieAndNoStoreContract() {
        TokenService tokens = new TokenService();
        String token = tokens.issue(actor(1L, "ADMIN", Role.ADMIN));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.http.ResponseEntity<java.util.Map<String, Object>> session = new UReportController(tokens)
                .establishSession("Bearer " + token, request, response);
        assertThat(session.getBody().get("factoryCode")).isEqualTo("ADMIN");
        assertThat(response.getHeader("Set-Cookie")).contains("Path=/ureport", "HttpOnly", "SameSite=Lax", "Secure");
        assertThat(session.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @ParameterizedTest @ValueSource(strings = {"/fixture/ureport;view=preview", "/fixture/ureport;view=preview/designer"})
    void containerResolvedReportMappingIsProtectedWithNormalPathParameters(String uri) throws Exception {
        TokenService tokens = mock(TokenService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("/fixture"); request.setServletPath("/ureport");
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); verifyNoInteractions(tokens);
    }

    @Test void productionRoleInThePlatformFactoryIsNotASuperadmin() throws Exception {
        TokenService tokens = new TokenService();
        String token = tokens.issue(actor(1L, "ADMIN", Role.PRODUCTION));
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        new UReportAuthFilter(tokens).doFilter(requestWithCookie(token), response, chain);
        assertThat(response.getStatus()).isEqualTo(403); assertThat(chain.getRequest()).isNull();
        assertThatThrownBy(() -> new UReportController(tokens).establishSession("Bearer " + token,
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    private MockHttpServletRequest requestWithCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ureport/designer");
        request.setCookies(new Cookie(UReportAuthFilter.SESSION_COOKIE, token)); return request;
    }

    private FactoryUser actor(long factoryId, String factoryCode, Role role) {
        Factory factory = new Factory(); factory.setId(factoryId); factory.setCode(factoryCode); factory.setEnabled(true);
        FactoryUser user = new FactoryUser(); user.setId(factoryId + 100L); user.setFactory(factory);
        user.setRole(role); user.setName("Fixture actor"); user.setEnabled(true); return user;
    }
}
