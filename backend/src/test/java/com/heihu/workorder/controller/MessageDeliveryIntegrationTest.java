package com.heihu.workorder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.service.AuthService;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** No real messaging provider is contacted. All requests terminate at a private loopback receiver. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:message_delivery;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.cache.type=none", "logging.file.name=output/message-delivery-test.log",
        "app.message.allowed-target-hosts=127.0.0.1", "app.message.allow-http=true",
        "app.message.wechat-mp-app-id=qa-app", "app.message.wechat-mp-access-token=local-synthetic-token"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessageDeliveryIntegrationTest {
    static final AtomicReference<String> body = new AtomicReference<>();
    static final AtomicReference<String> query = new AtomicReference<>();
    static final AtomicReference<String> mode = new AtomicReference<>("success");
    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicInteger redirected = new AtomicInteger();
    static volatile CountDownLatch received;
    static volatile CountDownLatch release;
    static final ExecutorService serverThreads = Executors.newCachedThreadPool(task -> { Thread thread = new Thread(task); thread.setDaemon(true); return thread; });
    static final HttpServer receiver = receiver();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired FactoryRepository factories;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    Factory factory;
    String token;

    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry registry) {
        registry.add("app.message.wechat-mp-endpoint", () -> url("/mp"));
    }
    @AfterAll static void stop() { receiver.stop(0); serverThreads.shutdownNow(); }
    @BeforeEach void fixture() {
        mode.set("success"); calls.set(0); redirected.set(0); received = new CountDownLatch(1); release = new CountDownLatch(1);
        factory = new Factory(); factory.setCode(("QA-" + UUID.randomUUID()).toUpperCase(Locale.ROOT)); factory.setName("推送隔离工厂");
        factory.setEnabled(true); factory.setCreatedAt(LocalDateTime.now()); factory = factories.save(factory);
        FactoryUser user = new FactoryUser(); user.setFactory(factory); user.setUsername("admin"); user.setName("admin"); user.setRole(Role.ADMIN);
        user.setEnabled(true); user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        user.setPasswordHash(new BCryptPasswordEncoder().encode("Fixture@123456")); users.save(user);
        LoginRequest request = new LoginRequest(); request.setFactoryCode(factory.getCode()); request.setUsername("admin"); request.setPassword("Fixture@123456");
        token = auth.login(request).getToken();
    }

    @Test void webhookReallyPostsAndPersistsTheAcceptedDelivery() throws Exception {
        long id = config("webhook", url("/hook"), "测试消息", null).path("id").asLong();
        JsonNode result = send(id, 200);
        assertThat(result.path("success").asBoolean()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
        JsonNode posted = mapper.readTree(body.get());
        assertThat(posted.path("title").asText()).isEqualTo("推送验收");
        assertThat(posted.path("content").asText()).isEqualTo("测试消息");
        JsonNode stored = list().get(0);
        assertThat(stored.path("status").asText()).isEqualTo("SENT");
        assertThat(mapper.readTree(stored.path("payloadJson").asText()).path("lastTestResult").asText()).isEqualTo("DELIVERED");
        assertThat(result.path("httpStatus").asInt()).isEqualTo(200);
    }

    @Test void wechatWorkChecksProviderRejectionEvenWhenHttpIsSuccessful() throws Exception {
        long id = config("wechat_work", url("/hook"), "企微消息", null).path("id").asLong();
        mode.set("provider-error");
        JsonNode failed = send(id, 200);
        assertThat(failed.path("success").asBoolean()).isFalse();
        assertThat(failed.path("record").path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("message").asText()).contains("40013");
        assertThat(mapper.readTree(body.get()).path("msgtype").asText()).isEqualTo("text");
        mode.set("success");
        assertThat(send(id, 200).path("success").asBoolean()).isTrue();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void publicAccountUsesServerTokenAndConfiguredTemplateWithoutReturningTheSecret() throws Exception {
        ObjectNode fields = mapper.createObjectNode().put("appId", "qa-app").put("templateId", "qa-template").put("target", "qa-openid");
        long id = config("wechat_mp", "", "{\"thing1\":{\"value\":\"测试\"}}", fields).path("id").asLong();
        JsonNode result = send(id, 200);
        assertThat(result.path("success").asBoolean()).isTrue();
        assertThat(query.get()).isEqualTo("access_token=local-synthetic-token");
        JsonNode posted = mapper.readTree(body.get());
        assertThat(posted.path("touser").asText()).isEqualTo("qa-openid");
        assertThat(posted.path("template_id").asText()).isEqualTo("qa-template");
        assertThat(posted.path("data").path("thing1").path("value").asText()).isEqualTo("测试");
        assertThat(result.toString()).doesNotContain("local-synthetic-token");
        assertThat(list().toString()).doesNotContain("local-synthetic-token");
    }

    @Test void nonSuccessHttpAndRedirectsAreNotReportedAsSentAndAreNotFollowed() throws Exception {
        long id = config("webhook", url("/hook"), "测试", null).path("id").asLong();
        mode.set("error");
        JsonNode failed = send(id, 200);
        assertThat(failed.path("success").asBoolean()).isFalse();
        assertThat(failed.path("httpStatus").asInt()).isEqualTo(503);
        mode.set("redirect");
        assertThat(send(id, 200).path("httpStatus").asInt()).isEqualTo(302);
        assertThat(redirected.get()).isZero();
        assertThat(list().get(0).path("status").asText()).isEqualTo("FAILED");
    }

    @Test void aMissingConfigurationOrNonAllowlistedHostDoesNotPerformARequest() throws Exception {
        long missing = config("wechat_work", "", "消息", null).path("id").asLong();
        send(missing, 400);
        long blocked = config("webhook", "http://not-allowed.invalid/hook", "消息", null).path("id").asLong();
        send(blocked, 400);
        long wrongApp = config("wechat_mp", "", "{}", mapper.createObjectNode().put("appId", "wrong").put("templateId", "template")).path("id").asLong();
        send(wrongApp, 400);
        assertThat(calls.get()).isZero();
    }

    @Test void oversizedOrMalformedProviderResponsesAreFailedRatherThanPseudoSuccessful() throws Exception {
        long id = config("wechat_work", url("/hook"), "消息", null).path("id").asLong();
        mode.set("malformed");
        assertThat(send(id, 200).path("success").asBoolean()).isFalse();
        mode.set("oversized");
        assertThat(send(id, 200).path("success").asBoolean()).isFalse();
        assertThat(list().get(0).path("status").asText()).isEqualTo("FAILED");
    }

    @Test void concurrentSendDoesNotDuplicateTheInFlightRequest() throws Exception {
        long id = config("webhook", url("/hook"), "消息", null).path("id").asLong();
        mode.set("slow");
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> first = caller.submit(() -> send(id, 200));
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            send(id, 409);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).path("success").asBoolean()).isTrue();
            assertThat(calls.get()).isEqualTo(1);
        } finally { release.countDown(); caller.shutdownNow(); }
    }

    JsonNode config(String channel, String webhook, String template, ObjectNode extras) throws Exception {
        ObjectNode payload = mapper.createObjectNode().put("channel", channel).put("webhook", webhook).put("template", template).put("target", "qa-target");
        if (extras != null) payload.setAll(extras);
        String request = mapper.createObjectNode().put("moduleName", "MESSAGE").put("title", "推送验收").put("payloadJson", payload.toString()).toString();
        MvcResult response = mvc.perform(post("/api/advanced-records").header("Authorization", "Bearer " + token).contentType("application/json").content(request)).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        return mapper.readTree(response.getResponse().getContentAsByteArray());
    }
    JsonNode send(long id, int status) throws Exception {
        MvcResult response = mvc.perform(post("/api/advanced-records/" + id + "/send-test").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(response.getResponse().getStatus()).as(response.getResponse().getContentAsString()).isEqualTo(status);
        return mapper.readTree(response.getResponse().getContentAsByteArray());
    }
    JsonNode list() throws Exception {
        MvcResult response = mvc.perform(get("/api/advanced-records?moduleName=MESSAGE").header("Authorization", "Bearer " + token)).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("items");
    }
    static String url(String path) { return "http://127.0.0.1:" + receiver.getAddress().getPort() + path; }
    static HttpServer receiver() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(serverThreads);
            server.createContext("/trap", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
            com.sun.net.httpserver.HttpHandler handle = exchange -> {
                calls.incrementAndGet(); query.set(exchange.getRequestURI().getRawQuery());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = exchange.getRequestBody()) { byte[] buffer = new byte[1024]; int count; while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count); }
                body.set(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                int status = 200; String result = "{\"errcode\":0}";
                if ("provider-error".equals(mode.get())) result = "{\"errcode\":40013}";
                if ("error".equals(mode.get())) status = 503;
                if ("redirect".equals(mode.get())) { status = 302; exchange.getResponseHeaders().add("Location", url("/trap")); }
                if ("malformed".equals(mode.get())) result = "not-json";
                if ("oversized".equals(mode.get())) result = String.join("", java.util.Collections.nCopies(33000, "a"));
                if ("slow".equals(mode.get())) { received.countDown(); try { release.await(8, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } }
                byte[] response = result.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, response.length); exchange.getResponseBody().write(response); exchange.close();
            };
            server.createContext("/hook", handle); server.createContext("/mp", handle); server.start(); return server;
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
