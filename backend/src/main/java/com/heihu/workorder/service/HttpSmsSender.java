package com.heihu.workorder.service;

import com.heihu.workorder.config.SmsSenderProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class HttpSmsSender implements SmsSender {

    private final SmsSenderProperties.Http http;
    private final RestTemplate restTemplate;

    public HttpSmsSender(SmsSenderProperties.Http http) {
        this(http, createRestTemplate(http));
    }

    HttpSmsSender(SmsSenderProperties.Http http, RestTemplate restTemplate) {
        this.http = http;
        this.restTemplate = restTemplate;
    }

    @Override
    public void sendVerificationCode(String phone, String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasText(http.getAuthHeaderName()) && hasText(http.getAuthHeaderValue())) {
            headers.set(http.getAuthHeaderName(), http.getAuthHeaderValue());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phone", phone);
        payload.put("code", code);
        payload.put("template", http.getTemplate());

        restTemplate.postForEntity(http.getUrl(), new HttpEntity<>(payload, headers), String.class);
    }

    private static RestTemplate createRestTemplate(SmsSenderProperties.Http http) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(http.getConnectTimeoutMs());
        requestFactory.setReadTimeout(http.getReadTimeoutMs());
        return new RestTemplate(requestFactory);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
