package com.kaoyan408.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Command Code Provider API 客户端（OpenAI 兼容格式）。
 *
 * <p>为什么用 JDK 自带的 HttpClient 而不是 Spring 的 WebClient：
 * 只需要一个 POST + 读 SSE 流，引入 WebFlux 会带进整套响应式栈，
 * 与本项目的 MVC + MyBatis 不搭。HttpClient 的 BodyHandlers.ofInputStream()
 * 已经能满足逐行读取。
 *
 * <p>关于推理内容：deepseek-v4.1-flash 这类模型在正文之前会先输出
 * reasoning 分片（thinking:disabled 对其无效，实测反而触发更多推理）。
 * 这里把推理分片吞掉，只把正文交给调用方；同时通过 onThinking 回调
 * 通知「模型正在思考」，让前端能显示进度而不是空等。
 */
@Component
public class CommandCodeClient {

    private static final Logger log = LoggerFactory.getLogger(CommandCodeClient.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${kaoyan.ai.base-url:https://api.commandcode.ai/provider/v1}")
    private String baseUrl;

    @Value("${kaoyan.ai.api-key:}")
    private String apiKey;

    @Value("${kaoyan.ai.model:deepseek/deepseek-v4.1-flash}")
    private String model;

    @Value("${kaoyan.ai.max-tokens:2000}")
    private int maxTokens;

    @Value("${kaoyan.ai.temperature:0.3}")
    private double temperature;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String modelName() {
        return model;
    }

    /** 一次对话的输入：系统提示 + 历史消息 + 本次提问。 */
    public record ChatInput(String systemPrompt,
                            List<Map<String, String>> history,
                            String question) {
    }

    /** 流式结果的收尾统计。 */
    public record Usage(int promptTokens, int completionTokens) {
    }

    /**
     * 发起一次流式对话。
     *
     * @param onThinking 收到推理分片时回调（用于前端显示「思考中」），可为 null
     * @param onDelta    收到正文分片时回调
     * @return 本次用量；上游没返回 usage 时返回 0
     */
    public Usage stream(ChatInput input,
                        Runnable onThinking,
                        Consumer<String> onDelta) {
        if (!isConfigured()) {
            throw new IllegalStateException("AI 服务未配置：缺少 kaoyan.ai.api-key");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (input.systemPrompt() != null && !input.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", input.systemPrompt()));
        }
        messages.addAll(input.history());
        messages.add(Map.of("role", "user", "content", input.question()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("stream", true);

        String payload;
        try {
            payload = JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("构造请求体失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<java.io.InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("AI 上游返回 {}: {}", response.statusCode(), truncate(err, 500));
                throw new IllegalStateException("AI 服务返回错误（" + response.statusCode() + "）");
            }

            return readSse(response.body(), onThinking, onDelta);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 请求失败: {}", e.getMessage());
            throw new IllegalStateException("AI 服务连接失败：" + e.getMessage(), e);
        }
    }


    /**
     * 一次性返回完整回答（非流式）。
     *
     * <p>用于生成结构化内容（图解 DSL）：那种场景要的是完整 JSON，
     * 流式反而得先拼起来再解析，多一层不必要的复杂度。
     *
     * <p>注意本模型的推理 token 与正文共用 max_tokens，
     * 生成 JSON 时容易先被推理吃掉大半导致输出截断，所以这里单独放宽。
     */
    public String complete(String systemPrompt, String question, int maxTokensOverride) {
        if (!isConfigured()) {
            throw new IllegalStateException("AI 服务未配置：缺少 kaoyan.ai.api-key");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", question));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokensOverride > 0 ? maxTokensOverride : maxTokens);
        body.put("temperature", 0.2);

        String payload;
        try {
            payload = JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("构造请求体失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("AI 上游返回 {}: {}", response.statusCode(),
                        truncate(response.body(), 500));
                throw new IllegalStateException("AI 服务返回错误（" + response.statusCode() + "）");
            }
            JsonNode root = JSON.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 请求失败: {}", e.getMessage());
            throw new IllegalStateException("AI 服务连接失败：" + e.getMessage(), e);
        }
    }

    /** 逐行解析 SSE，把 reasoning 与 content 分流。 */
    private Usage readSse(java.io.InputStream stream, Runnable onThinking, Consumer<String> onDelta)
            throws Exception {
        int promptTokens = 0;
        int completionTokens = 0;
        boolean thinkingNotified = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }

                JsonNode node;
                try {
                    node = JSON.readTree(data);
                } catch (Exception ignore) {
                    // 上游偶尔会夹带非 JSON 的心跳行，跳过即可
                    continue;
                }

                // 用量一般出现在最后一个分片
                JsonNode usage = node.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                }

                JsonNode delta = node.path("choices").path(0).path("delta");
                if (delta.isMissingNode()) {
                    continue;
                }

                // 推理分片：只通知一次，内容丢弃
                JsonNode reasoning = delta.path("reasoning");
                if (reasoning.isMissingNode()) {
                    reasoning = delta.path("reasoning_content");
                }
                if (!reasoning.isMissingNode() && !reasoning.asText().isEmpty()) {
                    if (!thinkingNotified && onThinking != null) {
                        thinkingNotified = true;
                        onThinking.run();
                    }
                }

                JsonNode content = delta.path("content");
                if (!content.isMissingNode()) {
                    String text = content.asText();
                    if (!text.isEmpty()) {
                        onDelta.accept(text);
                    }
                }
            }
        }

        return new Usage(promptTokens, completionTokens);
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "…");
    }
}