package com.heihu.workorder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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

    @Value("${app.sms.http.auth-header-value:}")
    private String smsAuthHeaderValue;

    public ProductionConfigGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!isProd()) {
            return;
        }
        requireRealValue(datasourcePassword, "spring.datasource.password");
        requireRealValue(seedFactoryName, "app.seed.factory-name");
        requireRealValue(seedAdminPassword, "app.seed.admin-password");
        requireRealValue(seedSuperAdminPassword, "app.seed.super-admin-password");
        requireRealValue(wechatAppId, "app.wechat.app-id");
        requireRealValue(wechatSecret, "app.wechat.secret");
        rejectPlaceholderIfPresent(smsAuthHeaderValue, "app.sms.http.auth-header-value");
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private void requireRealValue(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured for prod");
        }
        rejectPlaceholderIfPresent(value, propertyName);
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
}
