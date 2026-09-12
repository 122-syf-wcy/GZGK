package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.MlModelRegistry;
import com.gzly.mapper.MlModelRegistryMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/ml")
@RequiredArgsConstructor
public class MlAdminController {

    private final MlModelRegistryMapper mapper;
    private final ObjectMapper objectMapper;

    @Value("${gzly.ml.base-url:http://127.0.0.1:8091}")
    private String baseUrl;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(900))
            .build();

    /**
     * 注册一个训练完成的模型版本到 ml_model_registry。
     *
     * <p>设计目标：服务器端训练脚本（{@code scripts/train_models_on_server.py}）训练完成后
     * 通过本接口把结果写入注册表，<strong>不再调用 ml-service 的 /ml/train 端点</strong>，
     * 避免训练递归触发 / 远程服务长连接 / 端口冲突等问题。</p>
     *
     * <p>调用方应自行确保 modelFilePath 存在且 metrics 已经评估过；本接口只做幂等 upsert。</p>
     */
    @PostMapping("/models/register")
    public Result<MlModelRegistry> registerModel(@RequestBody RegisterRequest req) {
        if (req == null || req.getModelName() == null || req.getModelName().isBlank()) {
            return Result.fail(400, "modelName 必填");
        }
        if (req.getModelVersion() == null || req.getModelVersion().isBlank()) {
            return Result.fail(400, "modelVersion 必填");
        }
        // 防伪校验（最小边界）：
        // 1) 拒绝 callMl 失败时曾伪造的 "train-request-" 前缀版本号（本次已改为抛异常，此处兜底历史/外部调用）；
        // 2) 要求声明训练产物路径 modelFilePath，杜绝完全无产物的手写注册。
        // 遗留风险：ml-service 未暴露"按版本号查询模型是否存在"的接口，本接口无法与训练产物做真实比对；
        // 仍知晓本接口的调用方可以传入任意版本号 + 任意路径完成注册。彻底修复需 ml-service 提供版本核验端点
        // （产物 checksum 或版本清单），届时本处应改为向 ML 服务核验后再落库。
        String modelVersion = req.getModelVersion().trim();
        if (modelVersion.toLowerCase().startsWith("train-request-")) {
            return Result.fail(400, "modelVersion 非法：疑似伪造的占位版本号");
        }
        if (req.getModelFilePath() == null || req.getModelFilePath().isBlank()) {
            return Result.fail(400, "modelFilePath 必填：注册必须声明训练产物路径");
        }
        MlModelRegistry registry = new MlModelRegistry();
        registry.setModelName(req.getModelName().trim());
        registry.setModelType(req.getModelType() == null || req.getModelType().isBlank()
                ? (registry.getModelName().toLowerCase().contains("rank") ? "LightGBMRegressor" : "LightGBMClassifier")
                : req.getModelType().trim());
        registry.setModelVersion(modelVersion);
        registry.setTrainYearRange(req.getTrainYearRange() == null ? "" : req.getTrainYearRange());
        registry.setTrainDataCount(req.getTrainDataCount() == null ? 0 : req.getTrainDataCount());
        registry.setMetricsJson(toJson(req.getMetrics()));
        registry.setFeatureSchemaJson(toJson(req.getFeatureSchema()));
        registry.setModelFilePath(req.getModelFilePath() == null ? "" : req.getModelFilePath());
        // status 默认 draft；调用方可显式传 active 但需通过 activate 端点统一切换
        registry.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "draft" : req.getStatus().trim());
        registry.setCreatedAt(LocalDateTime.now());
        upsertModel(registry);
        return Result.ok(mapper.selectById(registry.getId()));
    }

    /**
     * @deprecated 推荐改用 {@link #registerModel}，本接口保留兼容旧脚本。
     * 训练应由服务器本地的 {@code scripts/train_models_on_server.py} 直接调用 ml-service 内部
     * 训练函数完成，避免远程触发训练和端口/超时不确定性问题。
     */
    @Deprecated
    @PostMapping("/train")
    public Result<Map<String, Object>> train(@RequestBody(required = false) TrainRequest req) {
        TrainRequest request = req == null ? new TrainRequest() : req;
        String modelName = request.getModelName() == null || request.getModelName().isBlank()
                ? "chance-score" : request.getModelName().trim();
        String endpoint = modelName.toLowerCase().contains("rank")
                ? "/ml/train/rank-prediction"
                : "/ml/train/chance-score";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataPath", request.getDataPath());
        payload.put("outputDir", request.getOutputDir() == null || request.getOutputDir().isBlank()
                ? "models" : request.getOutputDir());
        Map<String, Object> response = callMl(endpoint, payload);

        // 训练结果校验：ml-service 对"训练失败/跳过"同样返回 HTTP 200（body 里 status=failed/skipped），
        // callMl 不会把它当异常；此前 train() 会照单落库，并用 modelName+时间戳 伪造 modelVersion，
        // 继续污染 ml_model_registry。这里要求响应明确成功且携带真实 modelVersion，否则拒绝写注册表。
        String responseStatus = response.get("status") == null ? "" : String.valueOf(response.get("status")).trim();
        if ("failed".equalsIgnoreCase(responseStatus) || "skipped".equalsIgnoreCase(responseStatus)
                || "error".equalsIgnoreCase(responseStatus)) {
            throw new BizException("ML 训练未成功（status=" + responseStatus + "），拒绝写入注册表");
        }
        Object versionNode = response.get("modelVersion");
        if (versionNode == null || String.valueOf(versionNode).isBlank()) {
            throw new BizException("ML 训练响应缺少 modelVersion，拒绝以伪造版本号写入注册表");
        }

        MlModelRegistry registry = new MlModelRegistry();
        registry.setModelName(modelName);
        registry.setModelType(modelName.toLowerCase().contains("rank") ? "LightGBMRegressor" : "LightGBMClassifier");
        registry.setModelVersion(String.valueOf(versionNode));
        registry.setTrainYearRange(String.valueOf(response.getOrDefault("trainYearRange", "")));
        registry.setTrainDataCount(asInt(response.get("trainDataCount")));
        registry.setMetricsJson(toJson(response.get("metrics")));
        registry.setFeatureSchemaJson(toJson(response.get("featureSchema")));
        registry.setModelFilePath(String.valueOf(response.getOrDefault("modelFilePath", "")));
        registry.setStatus("draft");
        registry.setCreatedAt(LocalDateTime.now());
        upsertModel(registry);

        response.put("registryId", registry.getId());
        return Result.ok(response);
    }

    @GetMapping("/models")
    public Result<List<MlModelRegistry>> models(@RequestParam(required = false) String modelName) {
        LambdaQueryWrapper<MlModelRegistry> wrapper = new LambdaQueryWrapper<MlModelRegistry>()
                .orderByDesc(MlModelRegistry::getCreatedAt);
        if (modelName != null && !modelName.isBlank()) {
            wrapper.eq(MlModelRegistry::getModelName, modelName.trim());
        }
        return Result.ok(mapper.selectList(wrapper));
    }

    @PostMapping("/models/{modelId}/activate")
    public Result<MlModelRegistry> activate(@PathVariable Long modelId) {
        MlModelRegistry model = mapper.selectById(modelId);
        if (model == null) {
            return Result.fail(404, "模型版本不存在");
        }
        mapper.update(null, new LambdaUpdateWrapper<MlModelRegistry>()
                .eq(MlModelRegistry::getModelName, model.getModelName())
                .set(MlModelRegistry::getStatus, "archived")
                .set(MlModelRegistry::getActivatedAt, null));
        model.setStatus("active");
        model.setActivatedAt(LocalDateTime.now());
        mapper.updateById(model);
        return Result.ok(mapper.selectById(modelId));
    }

    private Map<String, Object> callMl(String endpoint, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveBaseUrl() + endpoint))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ML 训练接口响应异常: " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return objectMapper.convertValue(root, Map.class);
        } catch (Exception e) {
            // 必须向上抛出：此前返回 modelVersion="train-request-"+ts 的伪造载荷，
            // 会被 train() 当成功落库，污染 ml_model_registry。调用方据异常明确失败。
            throw new BizException("ML 训练接口调用失败: " + e.getMessage());
        }
    }

    private void upsertModel(MlModelRegistry registry) {
        MlModelRegistry existing = mapper.selectOne(new LambdaQueryWrapper<MlModelRegistry>()
                .eq(MlModelRegistry::getModelName, registry.getModelName())
                .eq(MlModelRegistry::getModelVersion, registry.getModelVersion())
                .last("LIMIT 1"));
        if (existing != null) {
            registry.setId(existing.getId());
            mapper.updateById(registry);
        } else {
            mapper.insert(registry);
        }
    }

    private String resolveBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:8091" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Data
    public static class TrainRequest {
        private String modelName;
        private String dataPath;
        private String outputDir;
    }

    /**
     * 由服务器训练脚本（train_models_on_server.py / .sh）调用本控制器的 /models/register 时
     * 提交的注册表内容。所有字段都是写入 ml_model_registry 的字面值，metrics / featureSchema
     * 会被 Jackson 序列化为 JSON 存入对应列。
     */
    @Data
    public static class RegisterRequest {
        /** 模型名称：chance-score / rank-prediction 等 */
        private String modelName;
        /** 模型类型：LightGBMRegressor / LightGBMClassifier 等，可选；缺失时按 modelName 自动推断 */
        private String modelType;
        /** 模型版本号，建议格式 chance-score-vYYYYMMDD-HHmm */
        private String modelVersion;
        /** 训练年份范围，例如 "2019-2024" */
        private String trainYearRange;
        /** 训练样本数 */
        private Integer trainDataCount;
        /** 评估指标 Map（accuracy / mae / auc 等），将被序列化到 metrics_json */
        private Map<String, Object> metrics;
        /** 特征 schema Map，将被序列化到 feature_schema_json */
        private Map<String, Object> featureSchema;
        /** 模型文件绝对路径（建议放到 /var/lib/gzly-ml/models 下） */
        private String modelFilePath;
        /** 注册时的初始状态：draft / archived；active 应通过 /activate 单独激活 */
        private String status;
    }
}
