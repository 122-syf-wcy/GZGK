package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gzly.common.exception.BizException;
import com.gzly.entity.AiConfig;
import com.gzly.mapper.AiConfigMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private static final int MIN_MAX_TOKENS = 256;
    private static final int MAX_MAX_TOKENS = 12000;
    private static final double MIN_TEMPERATURE = 0.0D;
    private static final double MAX_TEMPERATURE = 2.0D;
    private static final int TEST_RESPONSE_BODY_LIMIT = 300;

    private final AiConfigMapper aiConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private AiCallLogService aiCallLogService;
    private final OkHttpClient testClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(45))
            .writeTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(50))
            .protocols(List.of(Protocol.HTTP_1_1))
            .build();

    @Value("${gzly.ai.base-url:}")
    private String fallbackBaseUrl;

    @Value("${gzly.ai.api-key:}")
    private String fallbackApiKey;

    @Value("${gzly.ai.model:gpt-4o-mini}")
    private String fallbackModel;

    @Value("${gzly.ai.max-tokens:2000}")
    private int fallbackMaxTokens;

    @Value("${gzly.ai.temperature:0.7}")
    private double fallbackTemperature;

    @Value("${gzly.ai.system-prompt:}")
    private String fallbackSystemPrompt;

    public RuntimeAiConfig currentRuntimeConfig() {
        AiConfig saved = loadSavedConfig();
        if (saved == null) {
            return RuntimeAiConfig.fromFallback(
                    normalizeUrl(fallbackBaseUrl),
                    trimToEmpty(fallbackApiKey),
                    defaultIfBlank(fallbackModel, "gpt-4o-mini"),
                    normalizeMaxTokens(fallbackMaxTokens),
                    normalizeTemperature(fallbackTemperature),
                    defaultSystemPrompt(fallbackSystemPrompt)
            );
        }
        String apiKey = isBlank(saved.getApiKey()) ? trimToEmpty(fallbackApiKey) : saved.getApiKey().trim();
        String chatModel = defaultIfBlank(saved.getChatModel(), fallbackModel);
        return RuntimeAiConfig.fromSaved(
                saved.getId(),
                defaultIfBlank(saved.getProviderName(), "OpenAI兼容服务"),
                normalizeUrl(defaultIfBlank(saved.getBaseUrl(), fallbackBaseUrl)),
                apiKey,
                chatModel,
                defaultIfBlank(saved.getReviewModel(), chatModel),
                defaultIfBlank(saved.getVisionModel(), chatModel),
                normalizeMaxTokens(saved.getMaxTokens() != null ? saved.getMaxTokens() : fallbackMaxTokens),
                normalizeTemperature(saved.getTemperature() != null ? saved.getTemperature() : fallbackTemperature),
                defaultSystemPrompt(defaultIfBlank(saved.getSystemPrompt(), fallbackSystemPrompt)),
                !Integer.valueOf(0).equals(saved.getEnabled()),
                saved.getUpdatedAt()
        );
    }

    public AiConfigView getSafeView() {
        return toView(loadSavedConfig(), currentRuntimeConfig());
    }

    public AiConfigView save(SaveAiConfigRequest req) {
        if (req == null) {
            throw new BizException("AI配置不能为空");
        }
        validate(req);
        AiConfig saved = loadSavedConfig();
        if (saved == null) {
            saved = new AiConfig();
            saved.setCreatedAt(LocalDateTime.now());
        }
        saved.setProviderName(defaultIfBlank(req.getProviderName(), "OpenAI兼容服务"));
        saved.setBaseUrl(normalizeUrl(req.getBaseUrl()));
        if (!isBlank(req.getApiKey())) {
            saved.setApiKey(req.getApiKey().trim());
        } else if (saved.getId() == null) {
            saved.setApiKey(trimToEmpty(fallbackApiKey));
        }
        saved.setChatModel(req.getChatModel().trim());
        saved.setReviewModel(defaultIfBlank(req.getReviewModel(), req.getChatModel()));
        saved.setVisionModel(defaultIfBlank(req.getVisionModel(), req.getChatModel()));
        saved.setMaxTokens(normalizeMaxTokens(req.getMaxTokens()));
        saved.setTemperature(normalizeTemperature(req.getTemperature()));
        saved.setSystemPrompt(defaultSystemPrompt(req.getSystemPrompt()));
        saved.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
        saved.setUpdatedAt(LocalDateTime.now());
        if (saved.getId() == null) {
            aiConfigMapper.insert(saved);
        } else {
            aiConfigMapper.updateById(saved);
        }
        return getSafeView();
    }

    public AiConfigTestResult testConnection(SaveAiConfigRequest req) {
        if (req == null) {
            throw new BizException("AI配置不能为空");
        }
        validate(req);
        RuntimeAiConfig config = buildRuntimeConfigForRequest(req, loadSavedConfig());
        if (!config.isUsable()) {
            return AiConfigTestResult.fail("AI接口地址或API Key未配置完整", null, config.getChatModel());
        }

        long startedAt = System.currentTimeMillis();
        try {
            ModelProbeResult probe = probeModel(config);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            recordTestLog(probe.success(), probe.httpStatus(), probe.errorCode(), config.getChatModel(),
                    elapsedMs, probe.message());
            if (probe.success()) {
                return AiConfigTestResult.ok("连接成功，模型可正常响应", elapsedMs,
                        defaultIfBlank(probe.model(), config.getChatModel()), probe.protocol(), probe.errorCode());
            }
            return AiConfigTestResult.fail(probe.userMessage(), elapsedMs, config.getChatModel(),
                    probe.protocol(), probe.errorCode());
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            String code = AiProviderErrorClassifier.classifyException(e);
            String message = friendlyExceptionMessage(e);
            recordTestLog(false, null, code, config.getChatModel(), elapsedMs, message);
            return AiConfigTestResult.fail(message, elapsedMs, config.getChatModel(), "", code);
        }
    }

    private ModelProbeResult probeModel(RuntimeAiConfig config) {
        boolean preferResponses = shouldPreferResponses(config.getBaseUrl(), config.getChatModel());
        if (preferResponses) {
            ModelProbeResult responses = probeResponses(config);
            if (responses.success() || !responses.unsupported()) {
                return responses;
            }
        }
        ModelProbeResult chat = probeChatCompletions(config);
        if (chat.success() || AiProviderErrorClassifier.isAccountOrPermission(chat.errorCode())
                || preferResponses || !chat.unsupported()) {
            return chat;
        }
        return probeResponses(config);
    }

    private ModelProbeResult probeChatCompletions(RuntimeAiConfig config) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getChatModel());
            body.put("max_tokens", 8);
            body.put("temperature", 0.0D);
            ArrayNode messages = body.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", "ping，请只回复 OK");
            Request request = requestBuilder(config, resolveChatCompletionsUrl(config.getBaseUrl()))
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
            try (Response response = testClient.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    return ModelProbeResult.httpFailure("chat_completions", response.code(),
                            extractErrorCode(responseBody), formatResponseError(responseBody));
                }
                JsonNode root = objectMapper.readTree(responseBody.isBlank() ? "{}" : responseBody);
                String content = root.path("choices").path(0).path("message").path("content").asText("");
                return ModelProbeResult.success("chat_completions", response.code(),
                        defaultIfBlank(root.path("model").asText(), config.getChatModel()), content);
            }
        } catch (Exception e) {
            return ModelProbeResult.exception("chat_completions", e);
        }
    }

    private ModelProbeResult probeResponses(RuntimeAiConfig config) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getChatModel());
            body.put("input", "ping，请只回复 OK");
            body.put("max_output_tokens", 16);
            Request request = requestBuilder(config, resolveResponsesUrl(config.getBaseUrl()))
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
            try (Response response = testClient.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    return ModelProbeResult.httpFailure("responses", response.code(),
                            extractErrorCode(responseBody), formatResponseError(responseBody));
                }
                JsonNode root = objectMapper.readTree(responseBody.isBlank() ? "{}" : responseBody);
                return ModelProbeResult.success("responses", response.code(),
                        defaultIfBlank(root.path("model").asText(), config.getChatModel()),
                        extractResponsesText(root));
            }
        } catch (Exception e) {
            return ModelProbeResult.exception("responses", e);
        }
    }

    private void recordTestLog(boolean success, Integer httpStatus, String errorCode, String model,
                               long latencyMs, String message) {
        if (aiCallLogService != null) {
            aiCallLogService.record(AiCallLogService.SCENE_TEST_CONNECTION, success, httpStatus, errorCode,
                    model, (int) latencyMs, message);
        }
    }

    private String extractErrorCode(String body) {
        if (isBlank(body)) {
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

    public AiModelListResult listModels(SaveAiConfigRequest req) {
        if (req == null) {
            throw new BizException("AI配置不能为空");
        }
        validateModelLookup(req);
        RuntimeAiConfig config = buildRuntimeConfigForRequest(req, loadSavedConfig());
        if (isBlank(config.getApiKey())) {
            return AiModelListResult.fail("API Key未配置，无法查询模型列表", List.of());
        }

        String lastFailure = "";
        boolean gotSuccessfulEmptyResponse = false;
        for (String modelUrl : resolveModelLookupUrls(config.getBaseUrl())) {
            try {
                Request request = new Request.Builder()
                        .url(modelUrl)
                        .addHeader("Authorization", "Bearer " + config.getApiKey())
                        .get()
                        .build();
                try (Response response = testClient.newCall(request).execute()) {
                    String responseBody = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        lastFailure = "查询模型失败：HTTP " + response.code() + formatResponseError(responseBody);
                        continue;
                    }
                    List<String> models = parseModelIds(responseBody);
                    if (models.isEmpty()) {
                        gotSuccessfulEmptyResponse = true;
                        lastFailure = "接口已响应，但没有返回可用模型";
                        continue;
                    }
                    return AiModelListResult.ok("已获取 " + models.size() + " 个模型", models);
                }
            } catch (Exception e) {
                String code = AiProviderErrorClassifier.classifyException(e);
                lastFailure = AiProviderErrorClassifier.userMessage(code, null, "模型列表接口");
            }
        }
        if (gotSuccessfulEmptyResponse) {
            return AiModelListResult.fail("接口已响应，但没有返回可用模型", List.of());
        }
        return AiModelListResult.fail(defaultIfBlank(lastFailure, "查询模型失败：模型接口不可用"), List.of());
    }

    private List<String> resolveModelLookupUrls(String rawBaseUrl) {
        String normalized = normalizeUrl(rawBaseUrl);
        List<String> urls = new ArrayList<>();
        if (normalized.endsWith("/models")) {
            urls.add(normalized);
            return urls;
        }
        if (normalized.endsWith("/responses")) {
            String apiRoot = normalized.substring(0, normalized.length() - "/responses".length());
            addIfAbsent(urls, normalizeUrl(apiRoot) + "/models");
            if (apiRoot.endsWith("/v1")) {
                addIfAbsent(urls, normalizeUrl(apiRoot.substring(0, apiRoot.length() - 3)) + "/models");
            } else {
                addIfAbsent(urls, normalizeUrl(apiRoot) + "/v1/models");
            }
            return urls;
        }
        if (normalized.endsWith("/chat/completions")) {
            String apiRoot = normalized.substring(0, normalized.length() - "/chat/completions".length());
            addIfAbsent(urls, normalizeUrl(apiRoot) + "/models");
            if (apiRoot.endsWith("/v1")) {
                addIfAbsent(urls, normalizeUrl(apiRoot.substring(0, apiRoot.length() - 3)) + "/models");
            } else {
                addIfAbsent(urls, normalizeUrl(apiRoot) + "/v1/models");
            }
            return urls;
        }
        if (normalized.endsWith("/v1")) {
            addIfAbsent(urls, normalized + "/models");
            addIfAbsent(urls, normalizeUrl(normalized.substring(0, normalized.length() - 3)) + "/models");
            return urls;
        }
        addIfAbsent(urls, normalized + "/v1/models");
        addIfAbsent(urls, normalized + "/models");
        return urls;
    }

    private void addIfAbsent(List<String> values, String value) {
        if (!isBlank(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private List<String> parseModelIds(String responseBody) throws Exception {
        Set<String> models = new LinkedHashSet<>();
        JsonNode root = objectMapper.readTree(responseBody);
        collectModelIds(root.path("data"), models);
        collectModelIds(root.path("models"), models);
        if (root.isArray()) {
            collectModelIds(root, models);
        }
        return new ArrayList<>(models);
    }

    private void collectModelIds(JsonNode node, Set<String> models) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String id = parseModelId(item);
            if (!isBlank(id) && models.size() < 200) {
                models.add(id.trim());
            }
        }
    }

    private String parseModelId(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return "";
        }
        if (item.isTextual()) {
            return item.asText("");
        }
        String id = item.path("id").asText("");
        if (!isBlank(id)) {
            return id;
        }
        id = item.path("model").asText("");
        if (!isBlank(id)) {
            return id;
        }
        return item.path("name").asText("");
    }

    private String formatResponseError(String responseBody) {
        if (isBlank(responseBody)) {
            return "";
        }
        String trimmed = responseBody.replaceAll("\\s+", " ").trim();
        if (trimmed.length() > TEST_RESPONSE_BODY_LIMIT) {
            trimmed = trimmed.substring(0, TEST_RESPONSE_BODY_LIMIT) + "...";
        }
        return "，" + trimmed;
    }

    private AiConfigView toView(AiConfig saved, RuntimeAiConfig runtime) {
        AiConfigView view = new AiConfigView();
        view.setId(saved != null ? saved.getId() : null);
        view.setProviderName(runtime.getProviderName());
        view.setBaseUrl(runtime.getBaseUrl());
        view.setChatModel(runtime.getChatModel());
        view.setReviewModel(runtime.getReviewModel());
        view.setVisionModel(runtime.getVisionModel());
        view.setMaxTokens(runtime.getMaxTokens());
        view.setTemperature(runtime.getTemperature());
        view.setSystemPrompt(runtime.getSystemPrompt());
        view.setEnabled(runtime.isEnabled());
        view.setHasApiKey(!isBlank(runtime.getApiKey()));
        view.setApiKeyMasked(maskApiKey(runtime.getApiKey()));
        view.setConfigSource(saved == null ? "environment" : "database");
        view.setUpdatedAt(runtime.getUpdatedAt());
        return view;
    }

    private Request.Builder requestBuilder(RuntimeAiConfig config, String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "GZLY-AI-Provider/1.0");
    }

    private boolean shouldPreferResponses(String baseUrl, String model) {
        String normalizedUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        return normalizedUrl.endsWith("/responses");
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

    private AiConfig loadSavedConfig() {
        return aiConfigMapper.selectOne(new LambdaQueryWrapper<AiConfig>()
                .orderByDesc(AiConfig::getUpdatedAt)
                .orderByDesc(AiConfig::getId)
                .last("LIMIT 1"));
    }

    private void validate(SaveAiConfigRequest req) {
        String baseUrl = normalizeUrl(req.getBaseUrl());
        if (isBlank(baseUrl)) {
            throw new BizException("请输入AI接口地址");
        }
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            throw new BizException("AI接口地址仅支持 http 或 https");
        }
        if (baseUrl.length() > 500) {
            throw new BizException("AI接口地址过长");
        }
        assertOutboundUrlAllowed(baseUrl);
        if (isBlank(req.getChatModel())) {
            throw new BizException("请输入对话模型名称");
        }
        if (req.getChatModel().length() > 100) {
            throw new BizException("模型名称过长");
        }
        if (!isBlank(req.getReviewModel()) && req.getReviewModel().length() > 100) {
            throw new BizException("审核模型名称过长");
        }
        if (!isBlank(req.getVisionModel()) && req.getVisionModel().length() > 100) {
            throw new BizException("视觉模型名称过长");
        }
        if (req.getApiKey() != null && req.getApiKey().length() > 500) {
            throw new BizException("API Key过长");
        }
        if (req.getMaxTokens() == null || req.getMaxTokens() < MIN_MAX_TOKENS || req.getMaxTokens() > MAX_MAX_TOKENS) {
            throw new BizException("maxTokens需在256到12000之间");
        }
        if (req.getTemperature() == null || req.getTemperature() < MIN_TEMPERATURE || req.getTemperature() > MAX_TEMPERATURE) {
            throw new BizException("temperature需在0到2之间");
        }
    }

    private void validateModelLookup(SaveAiConfigRequest req) {
        String baseUrl = normalizeUrl(req.getBaseUrl());
        if (isBlank(baseUrl)) {
            throw new BizException("请输入AI接口地址");
        }
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            throw new BizException("AI接口地址仅支持 http 或 https");
        }
        if (baseUrl.length() > 500) {
            throw new BizException("AI接口地址过长");
        }
        assertOutboundUrlAllowed(baseUrl);
        if (req.getApiKey() != null && req.getApiKey().length() > 500) {
            throw new BizException("API Key过长");
        }
    }

    /**
     * SSRF 防护：禁止 AI 出站地址指向回环/内网/链路本地/云元数据地址，
     * 防止管理员凭证被盗后用服务端探测内网或读取云元数据。
     */
    private void assertOutboundUrlAllowed(String baseUrl) {
        String host;
        try {
            host = java.net.URI.create(baseUrl).getHost();
        } catch (Exception e) {
            throw new BizException("AI接口地址格式不正确");
        }
        if (host == null || host.isBlank()) {
            throw new BizException("AI接口地址缺少主机名");
        }
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".localhost")
                || lower.equals("metadata.google.internal")) {
            throw new BizException("AI接口地址不允许指向本机或内网地址");
        }
        try {
            for (java.net.InetAddress addr : java.net.InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                        || isUniqueLocalIpv6(addr)) {
                    throw new BizException("AI接口地址不允许指向本机或内网地址");
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 解析失败不阻断（可能是临时 DNS 故障）；字面量为内网的已在上面拦截。
            log.warn("AI 出站地址解析失败，跳过 SSRF 解析校验: host={}, err={}", host, e.toString());
        }
    }

    private boolean isUniqueLocalIpv6(java.net.InetAddress addr) {
        if (!(addr instanceof java.net.Inet6Address)) {
            return false;
        }
        byte[] b = addr.getAddress();
        // fc00::/7 (ULA)
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    private RuntimeAiConfig buildRuntimeConfigForRequest(SaveAiConfigRequest req, AiConfig saved) {
        String apiKey = !isBlank(req.getApiKey())
                ? req.getApiKey().trim()
                : saved != null && !isBlank(saved.getApiKey())
                ? saved.getApiKey().trim()
                : trimToEmpty(fallbackApiKey);
        String chatModel = defaultIfBlank(req.getChatModel(), fallbackModel);
        return RuntimeAiConfig.fromSaved(
                saved != null ? saved.getId() : null,
                defaultIfBlank(req.getProviderName(), "OpenAI兼容服务"),
                normalizeUrl(defaultIfBlank(req.getBaseUrl(), fallbackBaseUrl)),
                apiKey,
                chatModel,
                defaultIfBlank(req.getReviewModel(), chatModel),
                defaultIfBlank(req.getVisionModel(), chatModel),
                normalizeMaxTokens(req.getMaxTokens()),
                normalizeTemperature(req.getTemperature()),
                defaultSystemPrompt(req.getSystemPrompt()),
                true,
                saved != null ? saved.getUpdatedAt() : null
        );
    }

    private String resolveChatCompletionsUrl(String rawBaseUrl) {
        String normalized = normalizeUrl(rawBaseUrl);
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
        String normalized = normalizeUrl(rawBaseUrl);
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

    private String normalizeUrl(String value) {
        String normalized = trimToEmpty(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int normalizeMaxTokens(Integer value) {
        int normalized = value == null ? fallbackMaxTokens : value;
        return Math.max(MIN_MAX_TOKENS, Math.min(MAX_MAX_TOKENS, normalized));
    }

    private double normalizeTemperature(Double value) {
        double normalized = value == null ? fallbackTemperature : value;
        return Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, normalized));
    }

    private String defaultSystemPrompt(String value) {
        return defaultIfBlank(value, "你是一名资深的贵州省高考志愿填报顾问。请基于提供的志愿方案数据，给出专业的匹配度分析和风险复核建议。严禁承诺录取。");
    }

    private String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "已配置";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? trimToEmpty(fallback) : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String friendlyExceptionMessage(Exception e) {
        String code = AiProviderErrorClassifier.classifyException(e);
        return AiProviderErrorClassifier.userMessage(code, null, null);
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
    public static class RuntimeAiConfig {
        private Long id;
        private String providerName;
        private String baseUrl;
        private String apiKey;
        private String chatModel;
        private String reviewModel;
        private String visionModel;
        private Integer maxTokens;
        private Double temperature;
        private String systemPrompt;
        private boolean enabled;
        private LocalDateTime updatedAt;

        static RuntimeAiConfig fromFallback(String baseUrl, String apiKey, String model, int maxTokens, double temperature, String systemPrompt) {
            return fromSaved(null, "环境变量配置", baseUrl, apiKey, model, model, model, maxTokens, temperature, systemPrompt, true, null);
        }

        static RuntimeAiConfig fromSaved(Long id, String providerName, String baseUrl, String apiKey, String chatModel,
                                         String reviewModel, String visionModel, int maxTokens, double temperature,
                                         String systemPrompt, boolean enabled, LocalDateTime updatedAt) {
            RuntimeAiConfig config = new RuntimeAiConfig();
            config.setId(id);
            config.setProviderName(providerName);
            config.setBaseUrl(baseUrl);
            config.setApiKey(apiKey);
            config.setChatModel(chatModel);
            config.setReviewModel(reviewModel);
            config.setVisionModel(visionModel);
            config.setMaxTokens(maxTokens);
            config.setTemperature(temperature);
            config.setSystemPrompt(systemPrompt);
            config.setEnabled(enabled);
            config.setUpdatedAt(updatedAt);
            return config;
        }

        public boolean isUsable() {
            return enabled && baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    public static class AiConfigView {
        private Long id;
        private String providerName;
        private String baseUrl;
        private String chatModel;
        private String reviewModel;
        private String visionModel;
        private Integer maxTokens;
        private Double temperature;
        private String systemPrompt;
        private Boolean enabled;
        private Boolean hasApiKey;
        private String apiKeyMasked;
        private String configSource;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class AiConfigTestResult {
        private Boolean success;
        private String message;
        private Long latencyMs;
        private String model;
        private String endpoint;
        private String errorCode;

        static AiConfigTestResult ok(String message, Long latencyMs, String model, String endpoint, String errorCode) {
            AiConfigTestResult result = new AiConfigTestResult();
            result.setSuccess(true);
            result.setMessage(message);
            result.setLatencyMs(latencyMs);
            result.setModel(model);
            result.setEndpoint(endpoint);
            result.setErrorCode(errorCode);
            return result;
        }

        static AiConfigTestResult fail(String message, Long latencyMs, String model) {
            return fail(message, latencyMs, model, "", "");
        }

        static AiConfigTestResult fail(String message, Long latencyMs, String model, String endpoint, String errorCode) {
            AiConfigTestResult result = new AiConfigTestResult();
            result.setSuccess(false);
            result.setMessage(message);
            result.setLatencyMs(latencyMs);
            result.setModel(model);
            result.setEndpoint(endpoint);
            result.setErrorCode(errorCode);
            return result;
        }
    }

    @Data
    public static class AiModelListResult {
        private Boolean success;
        private String message;
        private List<String> models;

        static AiModelListResult ok(String message, List<String> models) {
            AiModelListResult result = new AiModelListResult();
            result.setSuccess(true);
            result.setMessage(message);
            result.setModels(models);
            return result;
        }

        static AiModelListResult fail(String message, List<String> models) {
            AiModelListResult result = new AiModelListResult();
            result.setSuccess(false);
            result.setMessage(message);
            result.setModels(models);
            return result;
        }
    }

    @Data
    public static class SaveAiConfigRequest {
        private String providerName;
        private String baseUrl;
        private String apiKey;
        private String chatModel;
        private String reviewModel;
        private String visionModel;
        private Integer maxTokens;
        private Double temperature;
        private String systemPrompt;
        private Boolean enabled;
    }

    private record ModelProbeResult(String protocol, boolean success, boolean unsupported, Integer httpStatus,
                                    String errorCode, String model, String message, String userMessage) {
        static ModelProbeResult success(String protocol, Integer httpStatus, String model, String content) {
            boolean ok = content != null && !content.isBlank();
            return new ModelProbeResult(protocol, ok, false, httpStatus, ok ? null : AiProviderErrorClassifier.NO_VALID_RESPONSE,
                    model, ok ? "OK" : "AI 返回空内容",
                    ok ? "" : AiProviderErrorClassifier.userMessage(AiProviderErrorClassifier.NO_VALID_RESPONSE, httpStatus, protocol));
        }

        static ModelProbeResult httpFailure(String protocol, int status, String errorCode, String detail) {
            boolean unsupported = status == 404 || status == 405 || status == 501;
            String code = AiProviderErrorClassifier.classifyHttp(status, errorCode, detail);
            String message = "HTTP " + status + detail;
            String userMessage = AiProviderErrorClassifier.userMessage(code, status, protocol);
            return new ModelProbeResult(protocol, false, unsupported, status, code, "", message, userMessage);
        }

        static ModelProbeResult exception(String protocol, Exception e) {
            String code = AiProviderErrorClassifier.classifyException(e);
            String message = code.equals(AiProviderErrorClassifier.TIMEOUT)
                    ? "AI 服务响应超时"
                    : code.equals(AiProviderErrorClassifier.HANDSHAKE_ERROR)
                    ? "TLS 握手失败"
                    : e.getMessage() == null ? "未知异常" : e.getMessage();
            String userMessage = AiProviderErrorClassifier.userMessage(code, null, protocol);
            return new ModelProbeResult(protocol, false, false, null, code,
                    "", message, userMessage);
        }
    }
}
