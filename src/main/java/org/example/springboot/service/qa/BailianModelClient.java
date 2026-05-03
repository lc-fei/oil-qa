package org.example.springboot.service.qa;

import lombok.RequiredArgsConstructor;
import org.example.springboot.config.BailianProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里百炼 OpenAI 兼容接口客户端，集中处理 JSON Mode 与普通对话调用。
 */
@Component
@RequiredArgsConstructor
public class BailianModelClient {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final BailianProperties bailianProperties;

    public String chat(String systemPrompt, String userPrompt, boolean jsonMode) throws Exception {
        if (!StringUtils.hasText(bailianProperties.getApiKey())) {
            throw new IllegalStateException("未配置阿里百炼API Key");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", bailianProperties.getModel());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (jsonMode) {
            // 百炼 JSON Mode 要求 prompt 中出现 JSON/json 关键词，否则模型可能拒绝结构化输出。
            payload.put("response_format", Map.of("type", "json_object"));
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(bailianProperties.getConnectTimeoutMs()))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(bailianProperties.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofMillis(bailianProperties.getReadTimeoutMs()))
                .header("Authorization", "Bearer " + bailianProperties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON_MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("阿里百炼调用失败，HTTP状态码：" + response.statusCode());
        }
        JsonNode root = JSON_MAPPER.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("阿里百炼返回内容为空");
        }
        return choices.get(0).path("message").path("content").asText("");
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
