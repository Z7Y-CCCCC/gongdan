package com.heihu.workorder.service;

import com.heihu.workorder.config.SmsSenderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpSmsSenderTest {

    @Test
    void sendVerificationCodePostsExpectedPayload() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SmsSenderProperties.Http http = new SmsSenderProperties.Http();
        http.setUrl("https://sms-gateway.example.com/send-code");
        http.setAuthHeaderName("X-Api-Key");
        http.setAuthHeaderValue("secret");
        http.setTemplate("login_code");

        server.expect(requestTo("https://sms-gateway.example.com/send-code"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "secret"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"phone\":\"13800138000\",\"code\":\"123456\",\"template\":\"login_code\"}"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        new HttpSmsSender(http, restTemplate).sendVerificationCode("13800138000", "123456");

        server.verify();
    }
}
