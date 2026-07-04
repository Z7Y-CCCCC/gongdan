package com.heihu.workorder.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigGuardTest {

    @Test
    void rejectsDatasourcePlaceholderInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "datasourcePassword", "change-me");

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);

        assertThat(exception.getMessage()).contains("spring.datasource.password");
    }

    @Test
    void rejectsSeedPasswordPlaceholderInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "seedAdminPassword", "change-admin");

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);

        assertThat(exception.getMessage()).contains("app.seed.admin-password");
    }

    @Test
    void rejectsWechatPlaceholderInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "wechatSecret", "change-wechat-secret");

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);

        assertThat(exception.getMessage()).contains("app.wechat.secret");
    }

    @Test
    void rejectsOptionalSmsAuthPlaceholderWhenPresentInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "smsAuthHeaderValue", "Bearer change-sms-token");

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);

        assertThat(exception.getMessage()).contains("app.sms.http.auth-header-value");
    }

    @Test
    void rejectsWildcardCorsOriginsInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "corsAllowedOriginPatterns", "https://workorder.example.com,*");

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);

        assertThat(exception.getMessage()).contains("app.cors.allowed-origin-patterns");
    }

    @Test
    void allowsRealValuesInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void skipsValidationOutsideProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        ProductionConfigGuard guard = new ProductionConfigGuard(environment);
        ReflectionTestUtils.setField(guard, "datasourcePassword", "change-me");
        ReflectionTestUtils.setField(guard, "seedAdminPassword", "change-admin");

        assertDoesNotThrow(guard::validate);
    }

    private ProductionConfigGuard prodGuard() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return new ProductionConfigGuard(environment);
    }

    private void setValidValues(ProductionConfigGuard guard) {
        ReflectionTestUtils.setField(guard, "datasourcePassword", "DbPw_9x8y7z");
        ReflectionTestUtils.setField(guard, "seedFactoryName", "Real Factory");
        ReflectionTestUtils.setField(guard, "seedAdminPassword", "AdminPw_9x8y7z");
        ReflectionTestUtils.setField(guard, "seedSuperAdminPassword", "SuperPw_9x8y7z");
        ReflectionTestUtils.setField(guard, "wechatAppId", "wx1234567890abcdef");
        ReflectionTestUtils.setField(guard, "wechatSecret", "WechatSecret_9x8y7z");
        ReflectionTestUtils.setField(guard, "smsAuthHeaderValue", "Bearer real-token-9x8y7z");
        ReflectionTestUtils.setField(guard, "corsAllowedOriginPatterns", "https://workorder.example.com,https://admin.example.com");
    }
}
