package com.heihu.workorder.config;

import com.heihu.workorder.service.HttpSmsSender;
import com.heihu.workorder.service.LoggingSmsSender;
import com.heihu.workorder.service.SmsSender;
import java.util.Arrays;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(SmsSenderProperties.class)
public class SmsSenderConfig {

    @Bean
    public SmsSender smsSender(SmsSenderProperties properties, Environment environment) {
        String sender = normalize(properties.getSender());
        if ("http".equals(sender)) {
            requireText(properties.getHttp().getUrl(), "app.sms.http.url must be configured when app.sms.sender=http");
            return new HttpSmsSender(properties.getHttp());
        }
        if ("log".equals(sender)) {
            if (isProd(environment)) {
                throw new IllegalStateException("app.sms.sender=log is not allowed in prod. Configure APP_SMS_SENDER=http and APP_SMS_HTTP_URL.");
            }
            return new LoggingSmsSender(properties.isLogCode());
        }
        throw new IllegalStateException("Unsupported app.sms.sender: " + properties.getSender());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean isProd(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
