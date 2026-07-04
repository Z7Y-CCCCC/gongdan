package com.heihu.workorder.service;

import com.heihu.workorder.dto.MobileAuthResponse;
import com.heihu.workorder.dto.MobileBindRequest;
import com.heihu.workorder.dto.MobileWechatLoginRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
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
}
