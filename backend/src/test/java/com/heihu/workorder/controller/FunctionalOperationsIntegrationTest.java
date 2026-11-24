package com.heihu.workorder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.AuthResponse;
import com.heihu.workorder.dto.LoginRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.InternalMessage;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.InternalMessageRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.service.AuthService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Real controllers, transactions and persistence in an isolated memory database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:functional_operations;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.cache.type=none", "logging.file.name=output/functional-operations-test.log"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FunctionalOperationsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired FactoryRepository factories;
    @Autowired UserRepository users;
    @Autowired InternalMessageRepository messages;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    Factory factory;
    FactoryUser actor;
    String token;

    @BeforeEach void fixture() {
        factory = factory();
        actor = user(factory, "actor", Role.ADMIN);
        token = login(actor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"units", "defect-items", "departments", "processes", "customers", "suppliers", "warehouses"})
    void primaryMastersCreateEditReadDeleteThroughActualApi(String resource) throws Exception {
        String body = "{\"name\":\"功能验收\",\"code\":\"QA-001\",\"enabled\":true}";
        JsonNode created = json(call("POST", "/api/" + resource, body, token, 200));
        long id = created.path("id").asLong();
        assertThat(id).isPositive();
        JsonNode list = json(call("GET", "/api/" + resource, null, token, 200));
        assertThat(list.path("items").toString()).contains("功能验收");
        call("PUT", "/api/" + resource + "/" + id,
                "{\"name\":\"已修改\",\"code\":\"QA-001\",\"enabled\":true}", token, 200);
        assertThat(json(call("GET", "/api/" + resource, null, token, 200)).path("items").toString()).contains("已修改");
        FactoryUser foreign = user(factory(), "foreign", Role.ADMIN);
        MvcResult forbidden = raw("PUT", "/api/" + resource + "/" + id, body, login(foreign));
        assertThat(forbidden.getResponse().getStatus()).isIn(400, 404);
        assertThat(json(call("GET", "/api/" + resource, null, token, 200)).path("items").toString()).contains("已修改");
        MvcResult deleted = raw("DELETE", "/api/" + resource + "/" + id, null, token);
        assertThat(deleted.getResponse().getStatus()).isIn(200, 204);
        assertThat(json(call("GET", "/api/" + resource, null, token, 200)).path("items")).isEmpty();
    }

    @Test void personalPreferencesPersistAndStayUserScoped() throws Exception {
        String key = "ui.functional-check";
        call("PUT", "/api/user/preferences/" + key, "{\"value\":\"{\\\"columns\\\":[1,2]}\"}", token, 200);
        assertThat(json(call("GET", "/api/user/preferences/" + key, null, token, 200)).path("value").asText()).contains("columns");
        String other = login(user(factory, "colleague", Role.PRODUCTION));
        assertThat(json(call("GET", "/api/user/preferences/" + key, null, other, 200)).path("value").isNull()).isTrue();
        call("PUT", "/api/user/preferences/" + key, "{\"value\":\"updated\"}", token, 200);
        assertThat(json(call("GET", "/api/user/preferences/" + key, null, token, 200)).path("value").asText()).isEqualTo("updated");
    }

    @Test void advancedModulesPersistTheirActualPayloadAndDelete() throws Exception {
        for (String module : Arrays.asList("SOP", "EQUIPMENT", "QUALITY", "MESSAGE", "PAYROLL_RULE", "FIELD_EVENT")) {
            JsonNode created = json(call("POST", "/api/advanced-records",
                    "{\"moduleName\":\"" + module + "\",\"title\":\"功能验收\",\"payloadJson\":\"{\\\"content\\\":\\\"原文\\\"}\"}", token, 200));
            long id = created.path("id").asLong();
            call("PUT", "/api/advanced-records/" + id,
                    "{\"moduleName\":\"" + module + "\",\"title\":\"更新\",\"status\":\"DONE\",\"payloadJson\":\"{\\\"content\\\":\\\"更新内容\\\"}\"}", token, 200);
            JsonNode stored = json(call("GET", "/api/advanced-records?moduleName=" + module, null, token, 200)).path("items").get(0);
            assertThat(stored.path("status").asText()).isEqualTo("DONE");
            assertThat(stored.path("payloadJson").asText()).contains("更新内容");
            call("DELETE", "/api/advanced-records/" + id, null, token, 200);
            assertThat(json(call("GET", "/api/advanced-records?moduleName=" + module, null, token, 200)).path("total").asLong()).isZero();
        }
    }

    @Test void advancedRecordsRejectInvalidOrOversizedDataWithoutTruncatingExistingContent() throws Exception {
        String exact = "{\"x\":\"" + String.join("", Collections.nCopies(19992, "x")) + "\"}";
        assertThat(exact.length()).isEqualTo(20000);
        com.fasterxml.jackson.databind.node.ObjectNode valid = mapper.createObjectNode().put("moduleName", "SOP").put("title", "原记录").put("payloadJson", exact);
        JsonNode created = json(call("POST", "/api/advanced-records", valid.toString(), token, 200));
        assertThat(created.path("payloadJson").asText()).isEqualTo(exact);
        long id = created.path("id").asLong();
        for (String invalid : Arrays.asList("{\"x\":", exact + " ", "[1,2]", "{} {}")) {
            // Oversized non-whitespace content remains over the character limit.
            String payload = invalid.equals(exact + " ") ? exact.replace("\"}", "x\"}") : invalid;
            call("PUT", "/api/advanced-records/" + id, valid.deepCopy().put("title", "不应写入").put("payloadJson", payload).toString(), token, 400);
        }
        call("POST", "/api/advanced-records", valid.deepCopy().put("title", String.join("", Collections.nCopies(129, "x"))).toString(), token, 400);
        JsonNode stored = json(call("GET", "/api/advanced-records?moduleName=SOP", null, token, 200)).path("items").get(0);
        assertThat(stored.path("title").asText()).isEqualTo("原记录");
        assertThat(stored.path("payloadJson").asText()).isEqualTo(exact);
    }

    @Test void unrelatedPrivateConversationsNeverAppearAsMyLatestMessageOrUnread() throws Exception {
        FactoryUser b = user(factory, "b", Role.PRODUCTION);
        FactoryUser c = user(factory, "c", Role.PRODUCTION);
        message(b, c, "B-C PRIVATE", LocalDateTime.now());
        JsonNode contact = contact(call("GET", "/api/internal-chat/contacts", null, token, 200), b.getId());
        assertThat(contact.path("lastMessage").isNull()).isTrue();
        assertThat(contact.path("unreadCount").asInt()).isZero();
        message(b, actor, "FOR A", LocalDateTime.now().minusMinutes(1));
        contact = contact(call("GET", "/api/internal-chat/contacts", null, token, 200), b.getId());
        assertThat(contact.path("lastMessage").asText()).isEqualTo("FOR A");
        assertThat(contact.path("unreadCount").asInt()).isEqualTo(1);
        call("PUT", "/api/internal-chat/conversations/" + b.getId() + "/read", null, token, 204);
        assertThat(contact(call("GET", "/api/internal-chat/contacts", null, token, 200), b.getId()).path("unreadCount").asInt()).isZero();
    }

    @Test void contactsRemainCorrectAfterMoreThanFiveThousandUnrelatedMessages() throws Exception {
        FactoryUser b = user(factory, "b", Role.PRODUCTION);
        FactoryUser c = user(factory, "c", Role.PRODUCTION);
        message(b, actor, "OLDER FOR A", LocalDateTime.now().minusDays(1));
        jdbc.batchUpdate("insert into internal_messages(factory_id,sender_id,receiver_id,content,created_at) values(?,?,?,?,?)",
                Collections.nCopies(5001, new Object[] {factory.getId(), b.getId(), c.getId(), "unrelated", LocalDateTime.now()}));
        JsonNode contact = contact(call("GET", "/api/internal-chat/contacts", null, token, 200), b.getId());
        assertThat(contact.path("lastMessage").asText()).isEqualTo("OLDER FOR A");
        assertThat(contact.path("unreadCount").asInt()).isEqualTo(1);
    }

    @Test void crossFactoryChatSendAndReadAreRejected() throws Exception {
        FactoryUser foreign = user(factory(), "foreign", Role.PRODUCTION);
        call("POST", "/api/internal-chat/messages", "{\"receiverId\":" + foreign.getId() + ",\"content\":\"test\"}", token, 404);
        call("GET", "/api/internal-chat/conversations/" + foreign.getId() + "/messages", null, token, 404);
    }

    @Test void workOrderRequiredFieldsCannotBeBypassedOrClearedButHistoricalMissingValuesRemainEditable() throws Exception {
        long product = product();
        com.fasterxml.jackson.databind.node.ObjectNode order = mapper.createObjectNode().put("code", "LEGACY").put("productId", product).put("plannedQuantity", 2);
        JsonNode legacy = json(call("POST", "/api/work-orders", order.toString(), token, 200));
        long field = field("workorder", "Batch", "text");
        order.put("code", "NEW");
        call("POST", "/api/work-orders", order.toString(), token, 400);
        order.put("code", "LEGACY").put("plannedQuantity", 3).put("version", legacy.path("version").asLong());
        call("PUT", "/api/work-orders/" + legacy.path("id").asLong(), order.toString(), token, 200);
        order.put("code", "FILLED"); order.remove("version");
        order.set("customValues", mapper.createArrayNode().add(value(field, "text", mapper.getNodeFactory().textNode("batch-1"))));
        JsonNode filled = json(call("POST", "/api/work-orders", order.toString(), token, 200));
        order.put("version", filled.path("version").asLong());
        order.set("customValues", mapper.createArrayNode().add(value(field, "text", mapper.getNodeFactory().textNode(""))));
        call("PUT", "/api/work-orders/" + filled.path("id").asLong(), order.toString(), token, 400);
        order.put("code", "WRONG-TYPE"); order.remove("version");
        order.set("customValues", mapper.createArrayNode().add(value(field, "number", mapper.getNodeFactory().numberNode(1))));
        call("POST", "/api/work-orders", order.toString(), token, 400);
    }

    @Test void workOrderUnitsComeFromTheActualProductAcrossAdminAndMobileApis() throws Exception {
        long unit = json(call("POST", "/api/units", "{\"name\":\"kg\"}", token, 200)).path("id").asLong();
        String productBody = mapper.createObjectNode().put("code", "MASS-PRODUCT").put("name", "Mass product")
                .put("unitId", unit).put("attribute", "自制").toString();
        long product = json(call("POST", "/api/products", productBody, token, 200)).path("id").asLong();
        String orderBody = mapper.createObjectNode().put("code", "MASS-ORDER").put("productId", product)
                .put("plannedQuantity", 12).toString();
        JsonNode created = json(call("POST", "/api/work-orders", orderBody, token, 200));
        assertThat(created.path("unitName").asText()).isEqualTo("kg");
        long id = created.path("id").asLong();
        String mobileToken = login(user(factory, "unit-worker", Role.PRODUCTION));
        assertThat(json(call("GET", "/api/work-orders", null, token, 200)).path("items").get(0).path("unitName").asText()).isEqualTo("kg");
        for (String route : Arrays.asList("/api/mobile/work-orders", "/api/mobile/work-orders?keyword=MASS",
                "/api/mobile/work-orders?reportableOnly=true")) {
            assertThat(json(call("GET", route, null, mobileToken, 200)).path("items").get(0).path("unitName").asText()).isEqualTo("kg");
        }
        assertThat(json(call("GET", "/api/mobile/work-orders/" + id, null, mobileToken, 200)).path("unitName").asText()).isEqualTo("kg");
        assertThat(json(call("GET", "/api/mobile/work-orders/by-code/MASS-ORDER", null, mobileToken, 200)).path("unitName").asText()).isEqualTo("kg");
        assertThat(json(call("GET", "/api/mobile/dashboard", null, mobileToken, 200)).path("recentOrders").get(0).path("unitName").asText()).isEqualTo("kg");
        call("PUT", "/api/units/" + unit, "{\"name\":\"千克\"}", token, 200);
        assertThat(json(call("GET", "/api/mobile/work-orders/" + id, null, mobileToken, 200)).path("unitName").asText()).isEqualTo("千克");
    }

    @ParameterizedTest @ValueSource(strings = {"inbound", "outbound"})
    void receiptHeaderAndLineFieldRulesAreEnforcedBeforeStockChanges(String direction) throws Exception {
        long product = product();
        long header = field("receipt", "Batch", "text");
        long line = field("receipt_detail", "Count", "number");
        com.fasterxml.jackson.databind.node.ObjectNode receipt = mapper.createObjectNode()
                .put(direction + "Type", "NORMAL").put(direction + "Time", "2026-11-24 12:00:00");
        com.fasterxml.jackson.databind.node.ObjectNode item = mapper.createObjectNode().put("productId", product).put("quantity", 2);
        receipt.set("items", mapper.createArrayNode().add(item));
        String route = "/api/" + direction + "-receipts";
        call("POST", route, receipt.toString(), token, 400);
        receipt.set("customValues", mapper.createArrayNode().add(value(header, "text", mapper.getNodeFactory().textNode("batch-1"))));
        call("POST", route, receipt.toString(), token, 400);
        item.set("customValues", mapper.createArrayNode().add(value(line, "number", mapper.getNodeFactory().textNode("not-a-number"))));
        call("POST", route, receipt.toString(), token, 400);
        assertThat(json(call("GET", "/api/products", null, token, 200)).path("items").get(0).path("inventoryQty").asInt()).isEqualTo(20);
        item.set("customValues", mapper.createArrayNode().add(value(line, "number", mapper.getNodeFactory().numberNode(0))));
        call("POST", route, receipt.toString(), token, 200);
        assertThat(json(call("GET", "/api/products", null, token, 200)).path("items").get(0).path("inventoryQty").asInt()).isEqualTo("inbound".equals(direction) ? 22 : 18);
    }

    long product() throws Exception {
        long unit = json(call("POST", "/api/units", "{\"name\":\"piece\"}", token, 200)).path("id").asLong();
        String body = mapper.createObjectNode().put("code", "QA-PRODUCT").put("name", "QA product").put("unitId", unit).put("attribute", "自制").put("inventoryQty", 20).toString();
        return json(call("POST", "/api/products", body, token, 200)).path("id").asLong();
    }
    long field(String scope, String name, String type) throws Exception {
        String body = mapper.createObjectNode().put("scope", scope).put("name", name).put("fieldType", type).put("required", true).toString();
        return json(call("POST", "/api/custom-fields", body, token, 200)).path("id").asLong();
    }
    com.fasterxml.jackson.databind.node.ObjectNode value(long id, String type, JsonNode value) {
        com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode().put("fieldId", id).put("fieldType", type);
        node.set("value", value); return node;
    }

    MvcResult call(String method, String path, String body, String token, int status) throws Exception {
        MvcResult result = raw(method, path, body, token);
        assertThat(result.getResponse().getStatus()).as(method + " " + path + ": " + result.getResponse().getContentAsString()).isEqualTo(status);
        return result;
    }
    MvcResult raw(String method, String path, String body, String token) throws Exception {
        return mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body == null ? "" : body)).andReturn();
    }
    JsonNode json(MvcResult response) throws Exception { return mapper.readTree(response.getResponse().getContentAsByteArray()); }
    JsonNode contact(MvcResult response, Long id) throws Exception {
        for (JsonNode item : json(response)) if (item.path("id").asLong() == id) return item;
        throw new AssertionError("Contact missing: " + id);
    }
    Factory factory() {
        Factory value = new Factory(); value.setCode(("QA-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT)); value.setName("功能验收隔离工厂");
        value.setEnabled(true); value.setCreatedAt(LocalDateTime.now()); return factories.save(value);
    }
    FactoryUser user(Factory f, String name, Role role) {
        FactoryUser value = new FactoryUser(); value.setFactory(f); value.setUsername(name); value.setName(name);
        value.setRole(role); value.setEnabled(true); value.setCreatedAt(LocalDateTime.now()); value.setUpdatedAt(LocalDateTime.now());
        value.setPasswordHash(new BCryptPasswordEncoder().encode("Fixture@123456")); return users.save(value);
    }
    String login(FactoryUser user) {
        LoginRequest request = new LoginRequest(); request.setFactoryCode(user.getFactory().getCode());
        request.setUsername(user.getUsername()); request.setPassword("Fixture@123456");
        AuthResponse response = user.getRole() == Role.PRODUCTION ? auth.loginMobile(request, null) : auth.login(request); return response.getToken();
    }
    void message(FactoryUser sender, FactoryUser receiver, String content, LocalDateTime time) {
        InternalMessage value = new InternalMessage(); value.setFactory(factory); value.setSender(sender); value.setReceiver(receiver);
        value.setContent(content); value.setCreatedAt(time); messages.saveAndFlush(value);
    }
}
