package com.gzly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 未上线地区 AI 问答专用对话客户端。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>只<b>复用管理员端现有 AI 中转配置</b>（{@link AiConfigService#currentRuntimeConfig()}：
 *       Base URL / API Key / 模型名），不新增任何搜索/第三方配置。</li>
 *   <li>独立于 {@link AiService}（旧志愿解读链路），不复用其 prompt 与对话逻辑。</li>
 *   <li>非流式 {@code /v1/chat/completions}，与仓库既有调用口径一致。</li>
 *   <li>绝不打印 API Key；异常只记录截断后的状态信息。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQaChatClient {

    private final AiConfigService aiConfigService;
    private final AiCallLogService aiCallLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(105, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(110, TimeUnit.SECONDS)
            .protocols(List.of(Protocol.HTTP_1_1))
            .build();

    /** AI 通道是否可用（未配置时返回 false，由上层降级）。 */
    public boolean isUsable() {
        try {
            return aiConfigService.currentRuntimeConfig().isUsable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发起一次问答补全。
     *
     * @param systemPrompt  系统提示词
     * @param messages      多轮消息（role/content）
     * @param maxTokens     期望最大输出 token（受配置上限约束）
     * @param temperature   采样温度
     */
    public ChatResult complete(String systemPrompt, List<ChatTurn> messages, int maxTokens, double temperature) {
        return complete(AiCallLogService.SCENE_AI_QA, systemPrompt, messages, maxTokens, temperature);
    }

    public ChatResult complete(String scene, String systemPrompt, List<ChatTurn> messages, int maxTokens, double temperature) {
        ChatResult result = new ChatResult();
        long startedAt = System.currentTimeMillis();
        String model = "";
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                result.setUsable(false);
                aiCallLogService.record(scene, false, null, "NOT_CONFIGURED", null, 0, "AI 通道未配置或已停用");
                return result;
            }
            model = config.getChatModel();

            int boundedMax = Math.min(Math.max(maxTokens, 512), Math.max(config.getMaxTokens(), 512));
            ArrayNode arr = objectMapper.createArrayNode();
            ObjectNode sys = arr.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt == null ? "" : systemPrompt);
            if (messages != null) {
                for (ChatTurn turn : messages) {
                    if (turn == null || turn.getContent() == null || turn.getContent().isBlank()) {
                        continue;
                    }
                    ObjectNode m = arr.addObject();
                    m.put("role", "assistant".equalsIgnoreCase(turn.getRole()) ? "assistant" : "user");
                    m.put("content", turn.getContent());
                }
            }

            ModelCallResult call = callModel(config, arr, boundedMax, Math.min(0.6D, Math.max(0D, temperature)));
            long latency = System.currentTimeMillis() - startedAt;
            result.setUsable(true);
            result.setSuccess(call.success());
            result.setContent(call.content() == null ? "" : call.content());
            aiCallLogService.record(scene, call.success(), call.httpStatus(), call.errorCode(), model,
                    (int) latency, call.protocol() + " · " + call.message());
            return result;
        } catch (Exception e) {
            log.warn("AI 问答调用异常: {}", e.getMessage());
            result.setUsable(true);
            result.setSuccess(false);
            aiCallLogService.record(scene, false, null, isTimeout(e) ? "TIMEOUT" : "EXCEPTION", model,
                    (int) (System.currentTimeMillis() - startedAt), friendlyExceptionMessage(e));
            return result;
        }
    }

    private ModelCallResult callModel(AiConfigService.RuntimeAiConfig config,
                                      ArrayNode messages,
                                      int maxTokens,
                                      double temperature) {
        boolean preferResponses = shouldPreferResponses(config.getBaseUrl(), config.getChatModel());
        if (preferResponses) {
            ModelCallResult responses = callResponses(config, messages, maxTokens);
            if (responses.success() || !responses.unsupported()) {
                return responses;
            }
        }
        ModelCallResult chat = callChatCompletions(config, messages, maxTokens, temperature);
        if (chat.success() || preferResponses || !chat.unsupported()) {
            return chat;
        }
        return callResponses(config, messages, maxTokens);
    }

    private ModelCallResult callChatCompletions(AiConfigService.RuntimeAiConfig config,
                                                ArrayNode messages,
                                                int maxTokens,
                                                double temperature) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getChatModel());
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            body.put("stream", false);
            body.set("messages", messages);
            Request request = requestBuilder(config, resolveChatCompletionsUrl(config.getBaseUrl()))
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String bodyStr = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    return ModelCallResult.httpFailure("chat_completions", response.code(), extractErrorCode(bodyStr), briefBody(bodyStr));
                }
                JsonNode root = objectMapper.readTree(bodyStr.isBlank() ? "{}" : bodyStr);
                JsonNode messageNode = root.path("choices").path(0).path("message");
                String content = messageNode.path("content").asText("");
                if (content == null || content.isBlank()) {
                    content = messageNode.path("reasoning_content").asText("");
                }
                return ModelCallResult.fromContent("chat_completions", response.code(), content);
            }
        } catch (Exception e) {
            return ModelCallResult.exception("chat_completions", e);
        }
    }

    private ModelCallResult callResponses(AiConfigService.RuntimeAiConfig config,
                                          ArrayNode messages,
                                          int maxTokens) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getChatModel());
            body.put("input", renderMessagesForResponses(messages));
            body.put("max_output_tokens", maxTokens);
            Request request = requestBuilder(config, resolveResponsesUrl(config.getBaseUrl()))
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String bodyStr = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    return ModelCallResult.httpFailure("responses", response.code(), extractErrorCode(bodyStr), briefBody(bodyStr));
                }
                JsonNode root = objectMapper.readTree(bodyStr.isBlank() ? "{}" : bodyStr);
                return ModelCallResult.fromContent("responses", response.code(), extractResponsesText(root));
            }
        } catch (Exception e) {
            return ModelCallResult.exception("responses", e);
        }
    }

    private Request.Builder requestBuilder(AiConfigService.RuntimeAiConfig config, String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "GZLY-AI-Provider/1.0");
    }

    private String extractErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = root.path("code").asText("");
            if (code.isBlank()) {
                code = root.path("error").path("code").asText("");
            }
            if (code.isBlank()) {
                code = root.path("error").path("type").asText("");
            }
            return code.isBlank() ? null : code;
        } catch (Exception e) {
            return null;
        }
    }

    private String briefBody(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    private boolean shouldPreferResponses(String baseUrl, String model) {
        String normalizedUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        String normalizedModel = model == null ? "" : model.toLowerCase(Locale.ROOT);
        return normalizedUrl.endsWith("/responses")
                || normalizedModel.startsWith("gpt-5")
                || normalizedModel.contains("gpt-5.");
    }

    private String renderMessagesForResponses(ArrayNode messages) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode message : messages) {
            String role = message.path("role").asText("user");
            String content = message.path("content").asText("");
            if (!content.isBlank()) {
                builder.append(role).append(":\n").append(content).append("\n\n");
            }
        }
        return builder.toString().trim();
    }

    private String extractResponsesText(JsonNode root) {
        String outputText = root.path("output_text").asText("");
        if (!outputText.isBlank()) {
            return outputText;
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        String text = part.path("text").asText("");
                        if (text.isBlank()) {
                            text = part.path("content").asText("");
                        }
                        if (!text.isBlank()) {
                            builder.append(text).append('\n');
                        }
                    }
                }
            }
            return builder.toString().trim();
        }
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String resolveChatCompletionsUrl(String rawBaseUrl) {
        String normalized = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/responses")) {
            normalized = normalized.substring(0, normalized.length() - "/responses".length());
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private String resolveResponsesUrl(String rawBaseUrl) {
        String normalized = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/responses")) {
            return normalized;
        }
        if (normalized.endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/responses";
        }
        return normalized + "/v1/responses";
    }

    private String friendlyExceptionMessage(Exception e) {
        if (isTimeout(e)) {
            return "AI 服务响应超时";
        }
        return e.getMessage() == null || e.getMessage().isBlank() ? "AI 服务调用异常" : e.getMessage();
    }

    private boolean isTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            String message = cur.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    @Data
    public static class ChatTurn {
        private String role;
        private String content;

        public ChatTurn() {
        }

        public ChatTurn(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    public static class ChatResult {
        /** AI 通道是否已配置可用。 */
        private boolean usable;
        /** 本次调用是否拿到有效回复。 */
        private boolean success;
        private String content = "";
    }

    private record ModelCallResult(String protocol, boolean success, boolean unsupported, Integer httpStatus,
                                   String errorCode, String content, String message) {
        static ModelCallResult fromContent(String protocol, Integer httpStatus, String content) {
            if (content == null || content.isBlank()) {
                return new ModelCallResult(protocol, false, false, httpStatus, "EMPTY_CONTENT", "", "AI 返回空内容");
            }
            return new ModelCallResult(protocol, true, false, httpStatus, null, content, "OK");
        }

        static ModelCallResult httpFailure(String protocol, int status, String errorCode, String body) {
            boolean unsupported = status == 404 || status == 405 || status == 501;
            String code = errorCode == null || errorCode.isBlank() ? "HTTP_" + status : errorCode;
            return new ModelCallResult(protocol, false, unsupported, status, code, "", body.isBlank() ? "HTTP " + status : body);
        }

        static ModelCallResult exception(String protocol, Exception e) {
            boolean timeout = e instanceof SocketTimeoutException
                    || (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("timeout"));
            String message = timeout ? "AI 服务响应超时"
                    : e.getMessage() == null ? "AI 服务调用异常" : e.getMessage();
            return new ModelCallResult(protocol, false, false, null, timeout ? "TIMEOUT" : "EXCEPTION", "", message);
        }
    }
}
