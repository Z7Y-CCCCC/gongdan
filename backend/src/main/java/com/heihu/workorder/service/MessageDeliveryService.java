package com.heihu.workorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heihu.workorder.dto.MessageDeliveryTestResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.ExtensionRecord;
import com.heihu.workorder.repository.ExtensionRecordRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded, explicit test delivery; no implicit retries or off-host redirects. */
@Service
public class MessageDeliveryService {
    private final ExtensionRecordRepository records;
    private final ExtensionRecordService recordService;
    private final ObjectMapper mapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final ConcurrentHashMap<Long, Boolean> running = new ConcurrentHashMap<>();
    private final Set<String> allowedHosts;
    private final boolean allowHttp;
    private final String mpAppId;
    private final String mpToken;
    private final String mpEndpoint;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "message-delivery-deadline"); thread.setDaemon(true); return thread;
    });

    public MessageDeliveryService(ExtensionRecordRepository records, ExtensionRecordService recordService,
            @Value("${app.message.allowed-target-hosts:qyapi.weixin.qq.com,api.weixin.qq.com}") String allowedHosts,
            @Value("${app.message.allow-http:false}") boolean allowHttp,
            @Value("${app.message.wechat-mp-app-id:}") String mpAppId,
            @Value("${app.message.wechat-mp-access-token:}") String mpToken,
            @Value("${app.message.wechat-mp-endpoint:https://api.weixin.qq.com/cgi-bin/message/template/send}") String mpEndpoint) {
        this.records = records; this.recordService = recordService;
        this.allowedHosts = new HashSet<>();
        Arrays.stream(allowedHosts.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT)).forEach(this.allowedHosts::add);
        this.allowHttp = allowHttp; this.mpAppId = mpAppId; this.mpToken = mpToken; this.mpEndpoint = mpEndpoint;
    }

    @Transactional
    public MessageDeliveryTestResponse sendTest(Long factoryId, Long id) {
        if (running.putIfAbsent(id, Boolean.TRUE) != null) throw new ApiException(HttpStatus.CONFLICT, "该推送正在发送，请等待结果");
        try {
            ExtensionRecord record = records.findByIdAndFactoryId(id, factoryId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "推送配置不存在"));
            if (!ExtensionRecordService.MODULE_MESSAGE.equals(record.getModuleName())) throw bad("该记录不是消息推送配置");
            ObjectNode payload = parsePayload(record.getPayloadJson());
            if (record.getPayloadJson().length() > 18000) throw bad("推送配置过大，请精简后重试");
            String channel = text(payload, "channel");
            String content = text(payload, "template");
            if (content.isEmpty()) throw bad("请填写消息模板");
            URI endpoint;
            ObjectNode body = mapper.createObjectNode();
            boolean providerResponse = false;
            if ("webhook".equals(channel)) {
                endpoint = target(text(payload, "webhook"));
                body.put("title", record.getTitle()); body.put("target", text(payload, "target")); body.put("content", content);
            } else if ("wechat_work".equals(channel)) {
                if (content.getBytes(StandardCharsets.UTF_8).length > 2048) throw bad("企业微信文本不能超过2048字节");
                endpoint = target(text(payload, "webhook"));
                body.put("msgtype", "text"); body.putObject("text").put("content", content);
                providerResponse = true;
            } else if ("wechat_mp".equals(channel)) {
                if (mpAppId.trim().isEmpty() || mpToken.trim().isEmpty()) throw bad("请先在服务器配置公众号凭据");
                if (!mpAppId.equals(text(payload, "appId"))) throw bad("公众号 AppId 与服务器配置不一致");
                String templateId = text(payload, "templateId");
                String receiver = text(payload, "target");
                if (templateId.isEmpty() || receiver.isEmpty()) throw bad("请填写公众号模板ID和接收者openid");
                JsonNode data;
                try { data = mapper.readTree(content); } catch (IOException exception) { throw bad("公众号消息模板必须是 JSON data 对象"); }
                if (data == null || !data.isObject()) throw bad("公众号消息模板必须是 JSON data 对象");
                URI base = target(mpEndpoint);
                if (base.getRawQuery() != null) throw bad("公众号服务地址不应包含查询参数");
                try { endpoint = URI.create(base.toString() + "?access_token=" + URLEncoder.encode(mpToken, "UTF-8")); }
                catch (Exception exception) { throw bad("公众号凭据配置不正确"); }
                body.put("touser", receiver); body.put("template_id", templateId); body.set("data", data);
                providerResponse = true;
            } else { throw bad("不支持的消息渠道"); }

            Delivery result = deliver(endpoint, body, providerResponse);
            payload.put("lastTestResult", result.success ? "DELIVERED" : "FAILED");
            payload.put("lastTestAt", LocalDateTime.now().toString());
            payload.put("lastTestMessage", result.message);
            if (result.httpStatus == null) payload.putNull("lastTestHttpStatus"); else payload.put("lastTestHttpStatus", result.httpStatus);
            record.setStatus(result.success ? "SENT" : "FAILED");
            record.setPayloadJson(payload.toString()); record.setUpdatedAt(LocalDateTime.now());
            records.save(record);
            return new MessageDeliveryTestResponse(result.success, result.message, result.httpStatus, recordService.toResponse(record));
        } finally { running.remove(id); }
    }

    private ObjectNode parsePayload(String payload) {
        try { JsonNode node = mapper.readTree(payload == null ? "" : payload); if (node != null && node.isObject()) return (ObjectNode) node; }
        catch (IOException ignored) { }
        throw bad("推送配置格式不正确");
    }

    private URI target(String address) {
        URI uri;
        try { uri = URI.create(address); } catch (Exception exception) { throw bad("Webhook 地址格式不正确"); }
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                || !("https".equalsIgnoreCase(scheme) || allowHttp && "http".equalsIgnoreCase(scheme))) {
            throw bad("推送地址必须是无用户信息的 HTTPS 地址");
        }
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) throw bad("推送目标未在服务器主机白名单中");
        return uri;
    }

    private Delivery deliver(URI target, ObjectNode body, boolean providerResponse) {
        RequestConfig config = RequestConfig.custom().setConnectTimeout(3000).setConnectionRequestTimeout(3000).setSocketTimeout(5000).build();
        HttpPost request = new HttpPost(target);
        request.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
        ScheduledFuture<?> deadline = deadlines.schedule(request::abort, 8, TimeUnit.SECONDS);
        try (CloseableHttpClient client = HttpClients.custom().disableRedirectHandling().disableAutomaticRetries().setDefaultRequestConfig(config).build();
                CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 300) return new Delivery(false, "目标服务返回 HTTP " + status, status);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (response.getEntity() != null) {
                try (InputStream stream = response.getEntity().getContent()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = stream.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        if (output.size() + count > 32768) return new Delivery(false, "目标服务响应过大", status);
                        output.write(buffer, 0, count);
                    }
                }
            }
            if (providerResponse) {
                JsonNode result;
                try { result = mapper.readTree(output.toByteArray()); } catch (IOException exception) { return new Delivery(false, "微信服务返回了无效响应", status); }
                if (result == null || !result.has("errcode") || !result.path("errcode").isIntegralNumber()) return new Delivery(false, "微信服务返回了无效响应", status);
                if (result.path("errcode").asLong() != 0) return new Delivery(false, "微信服务拒绝消息，错误码 " + result.path("errcode").asLong(), status);
            }
            return new Delivery(true, "目标服务已接受测试消息", status);
        } catch (IOException exception) { return new Delivery(false, "推送连接失败，请检查地址和服务状态", null); }
        finally { deadline.cancel(false); }
    }
    @PreDestroy void close() { deadlines.shutdownNow(); }
    private String text(ObjectNode payload, String key) { return payload.path(key).asText("").trim(); }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private static final class Delivery {
        final boolean success; final String message; final Integer httpStatus;
        Delivery(boolean success, String message, Integer httpStatus) { this.success = success; this.message = message; this.httpStatus = httpStatus; }
    }
}
