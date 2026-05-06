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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final OkHttpClient testClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(20))
            .writeTimeout(Duration.ofSeconds(10))
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
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getChatModel());
            body.put("max_tokens", 8);
            body.put("temperature", 0.0D);
            ArrayNode messages = body.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", "ping");

            Request request = new Request.Builder()
                    .url(resolveChatCompletionsUrl(config.getBaseUrl()))
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = testClient.newCall(request).execute()) {
                long elapsedMs = System.currentTimeMillis() - startedAt;
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    return AiConfigTestResult.fail(
                            "连接失败：HTTP " + response.code() + formatResponseError(responseBody),
                            elapsedMs,
                            config.getChatModel()
                    );
                }
                String model = config.getChatModel();
                if (!isBlank(responseBody)) {
                    JsonNode root = objectMapper.readTree(responseBody);
                    model = defaultIfBlank(root.path("model").asText(), model);
                }
                return AiConfigTestResult.ok("连接成功，模型可正常响应", elapsedMs, model);
            }
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            return AiConfigTestResult.fail("连接失败：" + e.getMessage(), elapsedMs, config.getChatModel());
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
                lastFailure = "查询模型失败：" + e.getMessage();
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
        if (req.getApiKey() != null && req.getApiKey().length() > 500) {
            throw new BizException("API Key过长");
        }
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
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
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

        static AiConfigTestResult ok(String message, Long latencyMs, String model) {
            AiConfigTestResult result = new AiConfigTestResult();
            result.setSuccess(true);
            result.setMessage(message);
            result.setLatencyMs(latencyMs);
            result.setModel(model);
            return result;
        }

        static AiConfigTestResult fail(String message, Long latencyMs, String model) {
            AiConfigTestResult result = new AiConfigTestResult();
            result.setSuccess(false);
            result.setMessage(message);
            result.setLatencyMs(latencyMs);
            result.setModel(model);
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
}
