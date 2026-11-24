package com.heihu.workorder.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.unit.DataSize;
import static org.assertj.core.api.Assertions.assertThat;

class MultipartConfigurationTest {
    @ParameterizedTest @ValueSource(strings = {"application.properties", "application-prod.properties"})
    void normalAndProductionDefaultsSupportTwentyMegabyteAttachments(String resource) throws Exception {
        java.util.Properties values = PropertiesLoaderUtils.loadProperties(new ClassPathResource(resource));
        MockEnvironment environment = new MockEnvironment();
        assertThat(DataSize.parse(environment.resolveRequiredPlaceholders(values.getProperty("spring.servlet.multipart.max-file-size"))).toBytes())
                .isEqualTo(20L * 1024 * 1024);
        assertThat(DataSize.parse(environment.resolveRequiredPlaceholders(values.getProperty("spring.servlet.multipart.max-request-size"))).toBytes())
                .isEqualTo(90L * 1024 * 1024);
    }

    @Test void explicitProductionMultipartOverridesRemainSupported() throws Exception {
        java.util.Properties values = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
        MockEnvironment environment = new MockEnvironment().withProperty("SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE", "25MB")
                .withProperty("SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE", "100MB");
        assertThat(environment.resolveRequiredPlaceholders(values.getProperty("spring.servlet.multipart.max-file-size"))).isEqualTo("25MB");
        assertThat(environment.resolveRequiredPlaceholders(values.getProperty("spring.servlet.multipart.max-request-size"))).isEqualTo("100MB");
    }
}
