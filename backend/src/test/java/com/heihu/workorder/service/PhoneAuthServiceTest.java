package com.heihu.workorder.service;

import com.heihu.workorder.dto.FactoryOption;
import com.heihu.workorder.dto.PhoneLoginResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhoneAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private PhoneAuthService phoneAuthService;

    // ==================== 密码登录测试 ====================

    @Test
    @DisplayName("测试密码登录成功（单用户）")
    void loginByPassword_singleEnabledUser_shouldReturnToken() {
        String phone = "13800138000";
        String password = "password123";
        FactoryUser user = buildUser(1L, phone, "hashedPwd", true, 1L, "F001", "工厂A");

        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches(password, "hashedPwd")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn("test-token-abc");

        PhoneLoginResponse response = phoneAuthService.loginByPassword(phone, password);

        assertFalse(response.isSelectRequired());
        assertEquals("test-token-abc", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(1L, response.getUser().getId());
        verify(tokenService).issue(user);
    }

    @Test
    @DisplayName("测试密码登录（手机号不存在）返回bindingRequired")
    void loginByPassword_phoneNotFound_shouldReturnBindingRequired() {
        String phone = "13800000000";

        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.emptyList());

        PhoneLoginResponse response = phoneAuthService.loginByPassword(phone, "anyPassword");

        assertTrue(response.isBindingRequired());
        assertEquals(phone, response.getPhone());
        assertNull(response.getToken());
        assertNull(response.getUser());
    }

    @Test
    @DisplayName("测试密码登录失败（密码错误）")
    void loginByPassword_wrongPassword_shouldThrowUnauthorized() {
        String phone = "13800138000";
        FactoryUser user = buildUser(1L, phone, "hashedPwd", true, 1L, "F001", "工厂A");

        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches("wrongPassword", "hashedPwd")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () ->
                phoneAuthService.loginByPassword(phone, "wrongPassword"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("手机号或密码不正确", exception.getMessage());
    }

    @Test
    @DisplayName("测试密码登录失败（账号禁用）")
    void loginByPassword_disabledUser_shouldThrowForbidden() {
        String phone = "13800138000";
        FactoryUser user = buildUser(1L, phone, "hashedPwd", false, 1L, "F001", "工厂A");

        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches("password123", "hashedPwd")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () ->
                phoneAuthService.loginByPassword(phone, "password123"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("账号已被禁用", exception.getMessage());
    }

    // ==================== 验证码登录测试 ====================

    @Test
    @DisplayName("测试验证码登录成功（单用户）")
    void loginBySmsCode_singleEnabledUser_shouldReturnToken() {
        String phone = "13800138000";
        String code = "123456";
        FactoryUser user = buildUser(1L, phone, "hashedPwd", true, 1L, "F001", "工厂A");

        when(smsService.verifyCode(phone, code)).thenReturn(true);
        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.singletonList(user));
        when(tokenService.issue(user)).thenReturn("sms-token-xyz");

        PhoneLoginResponse response = phoneAuthService.loginBySmsCode(phone, code);

        assertFalse(response.isSelectRequired());
        assertEquals("sms-token-xyz", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(1L, response.getUser().getId());
        verify(smsService).verifyCode(phone, code);
        verify(tokenService).issue(user);
    }

    @Test
    @DisplayName("测试验证码登录失败（验证码错误）")
    void loginBySmsCode_invalidCode_shouldThrowUnauthorized() {
        String phone = "13800138000";
        String code = "000000";

        when(smsService.verifyCode(phone, code)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () ->
                phoneAuthService.loginBySmsCode(phone, code));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("验证码错误或已过期", exception.getMessage());
    }

    @Test
    @DisplayName("测试验证码登录（手机号未注册但验证码有效）应返回 bindingRequired")
    void loginBySmsCode_phoneNotRegistered_validCode_shouldReturnBindingRequired() {
        String phone = "13800000000";
        String code = "123456";

        when(smsService.verifyCode(phone, code)).thenReturn(true);
        when(userRepository.findAllByPhone(phone)).thenReturn(Collections.emptyList());

        PhoneLoginResponse response = phoneAuthService.loginBySmsCode(phone, code);

        assertTrue(response.isBindingRequired());
        assertEquals(phone, response.getPhone());
        assertNull(response.getToken());
        assertNull(response.getUser());
    }

    // ==================== 多工厂场景测试 ====================

    @Test
    @DisplayName("测试多工厂场景返回工厂列表")
    void loginByPassword_multipleFactories_shouldReturnFactoryList() {
        String phone = "13800138000";
        String password = "password123";
        FactoryUser user1 = buildUser(1L, phone, "hashedPwd", true, 10L, "F001", "工厂A");
        FactoryUser user2 = buildUser(2L, phone, "hashedPwd", true, 20L, "F002", "工厂B");

        when(userRepository.findAllByPhone(phone)).thenReturn(Arrays.asList(user1, user2));
        when(passwordEncoder.matches(password, "hashedPwd")).thenReturn(true);

        PhoneLoginResponse response = phoneAuthService.loginByPassword(phone, password);

        assertTrue(response.isSelectRequired());
        assertNull(response.getToken());
        assertNull(response.getUser());
        List<FactoryOption> factories = response.getFactories();
        assertNotNull(factories);
        assertEquals(2, factories.size());
        assertEquals(10L, factories.get(0).getFactoryId());
        assertEquals("F001", factories.get(0).getFactoryCode());
        assertEquals("工厂A", factories.get(0).getFactoryName());
        assertEquals(20L, factories.get(1).getFactoryId());
        assertEquals("F002", factories.get(1).getFactoryCode());
        assertEquals("工厂B", factories.get(1).getFactoryName());
        verify(tokenService, never()).issue(any());
    }

    // ==================== 工厂选择测试 ====================

    @Test
    @DisplayName("测试工厂选择后完成登录")
    void selectFactory_validFactoryId_shouldReturnToken() {
        String phone = "13800138000";
        Long factoryId = 10L;
        FactoryUser user1 = buildUser(1L, phone, "hashedPwd", true, 10L, "F001", "工厂A");
        FactoryUser user2 = buildUser(2L, phone, "hashedPwd", true, 20L, "F002", "工厂B");

        when(userRepository.findAllByPhone(phone)).thenReturn(Arrays.asList(user1, user2));
        when(passwordEncoder.matches("password123", "hashedPwd")).thenReturn(true);
        when(tokenService.issue(user1)).thenReturn("factory-token-123");

        PhoneLoginResponse response = phoneAuthService.selectFactory(phone, factoryId, "password", "password123");

        assertFalse(response.isSelectRequired());
        assertEquals("factory-token-123", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(1L, response.getUser().getId());
        verify(tokenService).issue(user1);
    }

    // ==================== Helper Methods ====================

    private FactoryUser buildUser(Long userId, String phone, String passwordHash,
                                   boolean enabled, Long factoryId, String factoryCode, String factoryName) {
        Factory factory = new Factory();
        factory.setId(factoryId);
        factory.setCode(factoryCode);
        factory.setName(factoryName);
        factory.setCreatedAt(LocalDateTime.now());

        FactoryUser user = new FactoryUser();
        user.setId(userId);
        user.setFactory(factory);
        user.setPhone(phone);
        user.setPasswordHash(passwordHash);
        user.setEnabled(enabled);
        user.setUsername("user" + userId);
        user.setName("用户" + userId);
        user.setRole(Role.ADMIN);
        user.setVirtualUser(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
