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
    void rejectsRootDatabaseUserInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "datasourceUsername", "root");

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::validate);

        assertThat(exception.getMessage()).contains("root account");
    }

    @Test
    void rejectsShortOrSharedAdminPasswordsInProd() {
        ProductionConfigGuard shortPasswordGuard = prodGuard();
        setValidValues(shortPasswordGuard);
        ReflectionTestUtils.setField(shortPasswordGuard, "seedAdminPassword", "short-pass");
        assertThat(assertThrows(IllegalStateException.class, shortPasswordGuard::validate).getMessage())
                .contains("at least 12 characters");

        ProductionConfigGuard sharedPasswordGuard = prodGuard();
        setValidValues(sharedPasswordGuard);
        ReflectionTestUtils.setField(sharedPasswordGuard, "seedAdminPassword", "AdminPw_9x8y7z");
        ReflectionTestUtils.setField(sharedPasswordGuard, "seedSuperAdminPassword", "AdminPw_9x8y7z");
        assertThat(assertThrows(IllegalStateException.class, sharedPasswordGuard::validate).getMessage())
                .contains("must be different");
    }

    @Test
    void rejectsUnsafeMigrationOverridesInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "flywayEnabled", false);

        assertThat(assertThrows(IllegalStateException.class, guard::validate).getMessage())
                .contains("spring.flyway.enabled");
    }

    @Test
    void requiresS3CredentialsWhenS3StorageIsSelected() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "uploadStorageType", "s3");
        ReflectionTestUtils.setField(guard, "uploadS3Bucket", "");

        assertThat(assertThrows(IllegalStateException.class, guard::validate).getMessage())
                .contains("app.upload.s3.bucket");
    }

    @Test
    void allowsRealValuesInProd() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);

        assertDoesNotThrow(guard::validate);
    }

    @Test
    void rejectsMockWechatEvenIfItOverridesTheProductionProfile() {
        ProductionConfigGuard guard = prodGuard();
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "wechatMockEnabled", true);
        assertThat(assertThrows(IllegalStateException.class, guard::validate).getMessage())
                .contains("app.wechat.mock-enabled");
    }

    @Test
    void productionDefaultProfileAlsoEnforcesProductionChecks() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("prod");
        ProductionConfigGuard guard = new ProductionConfigGuard(environment);
        setValidValues(guard);
        ReflectionTestUtils.setField(guard, "seedAdminPassword", "change-admin");
        assertThat(assertThrows(IllegalStateException.class, guard::validate).getMessage())
                .contains("app.seed.admin-password");
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
        ReflectionTestUtils.setField(guard, "datasourceUsername", "heihu_app");
        ReflectionTestUtils.setField(guard, "datasourcePassword", "DbPw_9x8y7z");
        ReflectionTestUtils.setField(guard, "flywayEnabled", true);
        ReflectionTestUtils.setField(guard, "hibernateDdlAuto", "validate");
        ReflectionTestUtils.setField(guard, "seedFactoryName", "Real Factory");
        ReflectionTestUtils.setField(guard, "seedAdminPassword", "AdminPw_9x8y7z");
        ReflectionTestUtils.setField(guard, "seedSuperAdminPassword", "SuperPw_9x8y7z");
        ReflectionTestUtils.setField(guard, "wechatAppId", "wx1234567890abcdef");
        ReflectionTestUtils.setField(guard, "wechatSecret", "WechatSecret_9x8y7z");
        ReflectionTestUtils.setField(guard, "pluginGatewaySecret", "test-plugin-gateway-secret-32-characters-long");
        ReflectionTestUtils.setField(guard, "smsAuthHeaderValue", "Bearer real-token-9x8y7z");
        ReflectionTestUtils.setField(guard, "smsHttpUrl", "https://sms.example.net/send");
        ReflectionTestUtils.setField(guard, "corsAllowedOriginPatterns", "https://workorder.example.com,https://admin.example.com");
        ReflectionTestUtils.setField(guard, "uploadStorageType", "local");
        ReflectionTestUtils.setField(guard, "uploadS3Bucket", "");
        ReflectionTestUtils.setField(guard, "uploadS3AccessKey", "");
        ReflectionTestUtils.setField(guard, "uploadS3SecretKey", "");
    }
}
