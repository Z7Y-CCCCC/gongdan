package com.heihu.workorder.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestLoggingFilterTest {

    @Test
    void apiRequestReceivesCorrelationIdWithoutEchoingAuthorization() throws Exception {
        ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(1000L);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/work-orders");
        request.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
        assertThat(response.getHeader("Authorization")).isNull();
    }

    @Test
    void validIncomingCorrelationIdIsPreserved() throws Exception {
        ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(1000L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/work-orders");
        request.addHeader("X-Request-ID", "request-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-ID")).isEqualTo("request-12345678");
    }
}
