package com.heihu.workorder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class ProductionConfigGuard {

    private static final Set<String> PLACEHOLDER_VALUES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "change-me",
            "change-me-too",
            "change-root",
            "change-admin",
            "change-super-admin",
            "change-wechat-secret",
            "bearer change-sms-token",
            "local-admin-pass",
            "local-super-admin-pass",
            "password",
            "admin123",
            "123456",
            "wx...",
            "your factory"
    )));

    private final Environment environment;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.flyway.enabled:false}")
    private boolean flywayEnabled;

    @Value("${spring.jpa.hibernate.ddl-auto:}")
    private String hibernateDdlAuto;

    @Value("${app.seed.factory-name:}")
    private String seedFactoryName;

    @Value("${app.seed.admin-password:}")
    private String seedAdminPassword;

    @Value("${app.seed.super-admin-password:}")
    private String seedSuperAdminPassword;

    @Value("${app.wechat.app-id:}")
    private String wechatAppId;

    @Value("${app.wechat.secret:}")
    private String wechatSecret;

    @Value("${app.wechat.mock-enabled:false}")
    private boolean wechatMockEnabled;

    @Value("${app.sms.http.auth-header-value:}")
    private String smsAuthHeaderValue;

    @Value("${app.sms.http.url:}")
    private String smsHttpUrl;

    @Value("${app.cors.allowed-origin-patterns:}")
    private String corsAllowedOriginPatterns;

    @Value("${app.plugin.gateway-secret:}")
    private String pluginGatewaySecret;

    @Value("${app.upload.storage-type:local}")
    private String uploadStorageType;

    @Value("${app.upload.s3.bucket:}")
    private String uploadS3Bucket;

    @Value("${app.upload.s3.access-key:}")
    private String uploadS3AccessKey;

    @Value("${app.upload.s3.secret-key:}")
    private String uploadS3SecretKey;

    public ProductionConfigGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!isProd()) {
            return;
        }
        if (wechatMockEnabled) {
            throw new IllegalStateException("app.wechat.mock-enabled must be false in prod");
        }
        requireNonRootDatabaseUser();
        requireRealValue(datasourcePassword, "spring.datasource.password");
        requireRealValue(seedFactoryName, "app.seed.factory-name");
        requireStrongSecret(seedAdminPassword, "app.seed.admin-password");
        requireStrongSecret(seedSuperAdminPassword, "app.seed.super-admin-password");
        if (seedAdminPassword.trim().equals(seedSuperAdminPassword.trim())) {
            throw new IllegalStateException("factory admin and super admin passwords must be different in prod");
        }
        requireRealValue(wechatAppId, "app.wechat.app-id");
        if (!wechatAppId.trim().matches("wx[0-9A-Za-z]{16}")) {
            throw new IllegalStateException("app.wechat.app-id must be a valid WeChat AppID in prod");
        }
        requireRealValue(wechatSecret, "app.wechat.secret");
        rejectPlaceholderIfPresent(smsAuthHeaderValue, "app.sms.http.auth-header-value");
        requireHttpsUrl(smsHttpUrl, "app.sms.http.url");
        requireRestrictedCorsOrigins();
        requireStrongSecret(pluginGatewaySecret, "app.plugin.gateway-secret");
        requireMigrationSafety();
        requireSupportedUploadStorage();
    }

    private boolean isProd() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    private void requireRealValue(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured for prod");
        }
        rejectPlaceholderIfPresent(value, propertyName);
    }

    private void requireStrongSecret(String value, String propertyName) {
        requireRealValue(value, propertyName);
        if (value.trim().length() < 12) {
            throw new IllegalStateException(propertyName + " must be at least 12 characters in prod");
        }
    }

    private void rejectPlaceholderIfPresent(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (isPlaceholder(value)) {
            throw new IllegalStateException(propertyName + " uses a placeholder value and must be changed before prod startup");
        }
    }

    private boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return PLACEHOLDER_VALUES.contains(normalized)
                || normalized.contains("change-")
                || normalized.contains("change_me")
                || normalized.contains("example-token");
    }

    private void requireRestrictedCorsOrigins() {
        requireRealValue(corsAllowedOriginPatterns, "app.cors.allowed-origin-patterns");
        Arrays.stream(corsAllowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(origin -> {
                    String normalized = origin.toLowerCase(Locale.ROOT);
                    if (normalized.contains("*")) {
                        throw new IllegalStateException("app.cors.allowed-origin-patterns must not allow wildcard origins in prod");
                    }
                    if (!normalized.startsWith("https://")) {
                        throw new IllegalStateException("app.cors.allowed-origin-patterns must use HTTPS origins in prod");
                    }
                });
    }

    private void requireNonRootDatabaseUser() {
        requireRealValue(datasourceUsername, "spring.datasource.username");
        if ("root".equalsIgnoreCase(datasourceUsername.trim())) {
            throw new IllegalStateException("spring.datasource.username must not use the root account in prod");
        }
    }

    private void requireHttpsUrl(String value, String propertyName) {
        requireRealValue(value, propertyName);
        if (!value.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalStateException(propertyName + " must use HTTPS in prod");
        }
    }

    private void requireMigrationSafety() {
        if (!flywayEnabled) {
            throw new IllegalStateException("spring.flyway.enabled must be true in prod");
        }
        if (!"validate".equalsIgnoreCase(hibernateDdlAuto == null ? "" : hibernateDdlAuto.trim())) {
            throw new IllegalStateException("spring.jpa.hibernate.ddl-auto must be validate in prod");
        }
    }

    private void requireSupportedUploadStorage() {
        String storageType = uploadStorageType == null ? "" : uploadStorageType.trim().toLowerCase(Locale.ROOT);
        if (!"local".equals(storageType) && !"s3".equals(storageType)) {
            throw new IllegalStateException("app.upload.storage-type must be local or s3 in prod");
        }
        if ("s3".equals(storageType)) {
            requireRealValue(uploadS3Bucket, "app.upload.s3.bucket");
            requireRealValue(uploadS3AccessKey, "app.upload.s3.access-key");
            requireStrongSecret(uploadS3SecretKey, "app.upload.s3.secret-key");
        }
    }
}
