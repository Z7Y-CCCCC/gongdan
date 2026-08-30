package com.heihu.workorder.config;

import com.heihu.workorder.service.LoggingSmsSender;
import com.heihu.workorder.service.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsSenderConfigTest {

    private final SmsSenderConfig config = new SmsSenderConfig();

    @Test
    void logSenderIsAllowedOutsideProd() {
        SmsSenderProperties properties = new SmsSenderProperties();
        properties.setSender("log");

        SmsSender sender = config.smsSender(properties, new MockEnvironment().withProperty("spring.profiles.active", "test"));

        assertThat(sender).isInstanceOf(LoggingSmsSender.class);
    }

    @Test
    void logSenderIsRejectedInProd() {
        SmsSenderProperties properties = new SmsSenderProperties();
        properties.setSender("log");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                config.smsSender(properties, environment));

        assertThat(exception.getMessage()).contains("not allowed in prod");
    }

    @Test
    void httpSenderRequiresUrl() {
        SmsSenderProperties properties = new SmsSenderProperties();
        properties.setSender("http");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                config.smsSender(properties, new MockEnvironment()));

        assertThat(exception.getMessage()).contains("app.sms.http.url");
    }
}
