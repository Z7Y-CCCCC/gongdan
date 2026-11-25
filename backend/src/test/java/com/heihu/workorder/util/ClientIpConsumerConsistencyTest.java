package com.heihu.workorder.util;

import com.heihu.workorder.config.ApiRequestLoggingFilter;
import com.heihu.workorder.controller.PhoneAuthController;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.dto.SmsSendRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import com.heihu.workorder.service.AuthAttemptLimiter;
import com.heihu.workorder.service.AuthService;
import com.heihu.workorder.service.IpLocationService;
import com.heihu.workorder.service.PhoneAuthService;
import com.heihu.workorder.service.SmsService;
import com.heihu.workorder.service.TokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ordinary mocked consumers: no provider send, external connection, or real credentials. */
class ClientIpConsumerConsistencyTest {
    @Test void smsLoginMetadataRequestLogsAndSessionRefreshUseTheSameConfiguredResolver() {
        ClientIpResolver resolver = new ClientIpResolver("192.0.2.10,192.0.2.11");
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "203.0.113.20, 198.51.100.20, 192.0.2.11");
        String expected = "198.51.100.20";

        SmsService sms = mock(SmsService.class);
        PhoneAuthController phone = new PhoneAuthController(mock(PhoneAuthService.class), sms);
        ReflectionTestUtils.setField(phone, "clientIpResolver", resolver);
        SmsSendRequest send = new SmsSendRequest(); send.setPhone("13900000001"); phone.sendSmsCode(send, request);
        verify(sms).sendCode("13900000001", expected);

        ApiRequestLoggingFilter logger = new ApiRequestLoggingFilter(1000L);
        ReflectionTestUtils.setField(logger, "clientIpResolver", resolver);
        assertThat(ReflectionTestUtils.<String>invokeMethod(logger, "clientAddress", request)).isEqualTo(expected);

        FactoryRepository factories = mock(FactoryRepository.class); UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class); TokenService tokens = mock(TokenService.class);
        IpLocationService locations = mock(IpLocationService.class);
        Factory factory = new Factory(); factory.setId(11L); factory.setCode("FIXTURE"); factory.setEnabled(true);
        FactoryUser user = new FactoryUser(); user.setId(1L); user.setFactory(factory); user.setEnabled(true);
        user.setUsername("fixture-user"); user.setName("Fixture user"); user.setRole(Role.ADMIN); user.setPasswordHash("fixture-hash");
        when(factories.findByCode("FIXTURE")).thenReturn(Optional.of(factory));
        when(users.findByFactoryIdAndUsernameForUpdate(11L, "fixture-user")).thenReturn(Optional.of(user));
        when(passwords.matches("FixtureOnlyPassword", "fixture-hash")).thenReturn(true);
        when(locations.searchLocation(expected)).thenReturn("Fixture location");
        AuthService auth = new AuthService(factories, users, passwords, tokens, mock(AuthAttemptLimiter.class), locations);
        ReflectionTestUtils.setField(auth, "clientIpResolver", resolver);
        LoginRequest login = new LoginRequest(); login.setFactoryCode("FIXTURE"); login.setUsername("fixture-user");
        login.setPassword("FixtureOnlyPassword"); auth.login(login, request);
        verify(tokens).issue(user, expected, "Fixture location", null, null);

        UserSessionRepository sessions = mock(UserSessionRepository.class);
        TokenService sessionTokens = new TokenService(720L, sessions, users, locations);
        ReflectionTestUtils.setField(sessionTokens, "clientIpResolver", resolver);
        sessionTokens.refreshSessionMetadata("Bearer FIXTURE_TOKEN", request);
        verify(sessions).updateMetadata(anyString(), eq(expected), eq("Fixture location"), anyString(), anyString(), any());
    }
}
