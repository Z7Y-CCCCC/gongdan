package com.heihu.workorder.service;

import com.heihu.workorder.dto.MobileAuthResponse;
import com.heihu.workorder.dto.MobileBindRequest;
import com.heihu.workorder.dto.MobileWechatLoginRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileAuthServiceTest {

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private MobileAuthService service;

    @BeforeEach
    void setUp() {
        service = new MobileAuthService(factoryRepository, userRepository, passwordEncoder, tokenService);
        ReflectionTestUtils.setField(service, "mockEnabled", true);
        ReflectionTestUtils.setField(service, "openidTicketTtlMinutes", 10L);
    }

    @Test
    void bindRejectsExpiredOpenidTicket() {
        ReflectionTestUtils.setField(service, "openidTicketTtlMinutes", 0L);
        MobileWechatLoginRequest loginRequest = new MobileWechatLoginRequest();
        loginRequest.setMockOpenid("MOCK_OPENID_TEST");
        when(userRepository.findByWechatOpenid("MOCK_OPENID_TEST")).thenReturn(Optional.empty());

        MobileAuthResponse login = service.wechatLogin(loginRequest);

        MobileBindRequest bindRequest = new MobileBindRequest();
        bindRequest.setOpenidTicket(login.getOpenidTicket());
        bindRequest.setFactoryCode("HHGD");
        bindRequest.setUsername("admin");
        bindRequest.setPassword("secret");

        assertThatThrownBy(() -> service.bind(bindRequest))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(factoryRepository, passwordEncoder, tokenService);
    }

    @Test
    void wechatLoginIgnoresMockOpenidWhenMockDisabled() {
        ReflectionTestUtils.setField(service, "mockEnabled", false);
        MobileWechatLoginRequest loginRequest = new MobileWechatLoginRequest();
        loginRequest.setMockOpenid("MOCK_OPENID_TEST");

        assertThatThrownBy(() -> service.wechatLogin(loginRequest))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(userRepository);
    }

    @Test
    void bindBlocksAfterRepeatedWrongPasswords() {
        MobileAuthResponse login = createMockLogin("MOCK_OPENID_LIMIT");
        Factory factory = factory(1L, "HHGD");
        FactoryUser user = user(10L, factory);
        when(factoryRepository.findByCode("HHGD")).thenReturn(Optional.of(factory));
        when(userRepository.findByFactoryIdAndUsername(1L, "worker")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

        MobileBindRequest bindRequest = bindRequest(login.getOpenidTicket(), "bad");
        for (int i = 0; i < AuthAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.bind(bindRequest))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        assertThatThrownBy(() -> service.bind(bindRequest))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        verify(passwordEncoder, times(AuthAttemptLimiter.MAX_FAILED_ATTEMPTS)).matches("bad", "hashed");
    }

    @Test
    void bindDoesNotConsumeTicketAfterWrongPassword() {
        MobileAuthResponse login = createMockLogin("MOCK_OPENID_RETRY");
        Factory factory = factory(1L, "HHGD");
        FactoryUser user = user(10L, factory);
        when(factoryRepository.findByCode("HHGD")).thenReturn(Optional.of(factory));
        when(userRepository.findByFactoryIdAndUsername(1L, "worker")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(userRepository.findByWechatOpenid("MOCK_OPENID_RETRY")).thenReturn(Optional.empty());
        when(tokenService.issue(user)).thenReturn("mobile-token");

        assertThatThrownBy(() -> service.bind(bindRequest(login.getOpenidTicket(), "bad")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        MobileAuthResponse response = service.bind(bindRequest(login.getOpenidTicket(), "secret"));

        assertThat(response.isBindRequired()).isFalse();
        assertThat(response.getToken()).isEqualTo("mobile-token");
        assertThat(user.getWechatOpenid()).isEqualTo("MOCK_OPENID_RETRY");
    }

    private MobileAuthResponse createMockLogin(String openid) {
        MobileWechatLoginRequest loginRequest = new MobileWechatLoginRequest();
        loginRequest.setMockOpenid(openid);
        when(userRepository.findByWechatOpenid(openid)).thenReturn(Optional.empty());
        return service.wechatLogin(loginRequest);
    }

    private MobileBindRequest bindRequest(String ticket, String password) {
        MobileBindRequest request = new MobileBindRequest();
        request.setOpenidTicket(ticket);
        request.setFactoryCode("HHGD");
        request.setUsername("worker");
        request.setPassword(password);
        return request;
    }

    private Factory factory(Long id, String code) {
        Factory factory = new Factory();
        factory.setId(id);
        factory.setCode(code);
        factory.setName("Test Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        return factory;
    }

    private FactoryUser user(Long id, Factory factory) {
        FactoryUser user = new FactoryUser();
        user.setId(id);
        user.setFactory(factory);
        user.setUsername("worker");
        user.setName("Worker");
        user.setPasswordHash("hashed");
        user.setRole(Role.PRODUCTION);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
