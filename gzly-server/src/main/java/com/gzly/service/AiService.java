package com.gzly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gzly.common.ComplianceConstants;
import com.gzly.compliance.ComplianceTextGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * AI 深度解读服务 — 调用 OpenAI 格式接口，SSE 流式输出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiConfigService aiConfigService;
    private final VolunteerMetricsRecorder metricsRecorder;

    @Autowired(required = false)
    private ComplianceTextGuard complianceTextGuard;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private final OkHttpClient advisorChatHttpClient = httpClient.newBuilder()
            .callTimeout(80, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 系统级硬性约束，覆盖任何运营动态配置中可能的"放飞"措辞。
     * 永远拼接到 system prompt 之后，确保 AI 不会越过结构化任务边界。
     */
    private static final String SYSTEM_GUARDRAIL =
            "你是一名严谨的志愿填报数据助手，只能基于用户消息中的结构化 JSON 数据进行分析，"
                    + "不得编造官方来源、内部渠道、学校、专业、分数线、招生计划或录取结论。"
                    + "不得给出录取承诺，不得使用“内部信息/内部数据/保录取/不滑档/不脱档/100%录取/"
                    + "成功率/录取率/上榜率/录取概率/命中率/稳上/必上/保送/包过/稳进”这类绝对化或营销化措辞。"
                    + "正文开头必须显式输出：“" + ComplianceConstants.AI_GENERATED_NOTICE + "”。"
                    + "回答必须使用规范的 Markdown，结构必须包含但不限于：①一句话总判断；"
                    + "②张雪峰式报考建议；③梯度结构诊断；④最值得保留的志愿；⑤最需要警惕的风险；⑥强制人工复核清单；"
                    + "⑦下一步建议。最后一段必须输出固定免责声明：\n"
                    + "> " + ComplianceConstants.AI_GENERATED_NOTICE
                    + ComplianceConstants.REFERENCE_PROBABILITY_NOTICE + "\n"
                    + "若某条志愿数据来源是院校级或推断的选科要求，必须在该条目后用括号注明 (需复核)，"
                    + "并在“强制人工复核清单”中再次列出。";

    /**
     * 默认任务模板，强制 AI 按结构化 JSON 输入产出固定段落。
     */
    private static final String USER_TASK_TEMPLATE =
            "下面是经过系统校验的「贵州 96 志愿方案结构化数据」（JSON），请严格按照结构产出报告：\n\n"
                    + "1. 必须使用 Markdown 标题层级，依次包含：\n"
                    + "## 一句话总判断\n"
                    + "## 张雪峰式报考建议\n"
                    + "## 梯度结构诊断\n"
                    + "## 最值得保留的 5 个志愿\n"
                    + "## 最需要警惕的 3 个风险点\n"
                    + "## 强制人工复核清单\n"
                    + "## 排列式重排建议\n"
                    + "## 下一步执行清单\n"
                    + "## 免责声明\n\n"
                    + "2. 正文开头必须先单独输出：“> " + ComplianceConstants.AI_GENERATED_NOTICE + "”。\n"
                    + "3. 「张雪峰式报考建议」必须优先引用输入 JSON 中 `advisorAdvice`，围绕可行集、就业倒推、中位数原则、城市/学校/专业取舍、家庭成本、计划变化和复核清单输出；只能说明“参考 GitHub 开源项目 Eric-Yibo-Shen/zhangxuefeng-skillset 与 alchaincyf/zhangxuefeng-skill 的公开策略框架”，不得角色扮演，不得冒充张雪峰本人或任何机构官方意见。\n"
                    + "4. 「最值得保留的 5 个志愿」每条必须写明：序号、学校-专业、所在梯度、机会指数、风险等级、数据参考度、计划数/计划趋势、保留原因、建议排位、需复核的点。\n"
                    + "5. 「最需要警惕的 3 个风险点」必须包含：风险标题、涉及的学校-专业、风险来源、处理建议；若 planTrend=缩招/计划数暂缺 必须优先考虑。\n"
                    + "6. 「强制人工复核清单」必须严格复述输入 JSON 中 `manualReview[]` 的每一条，原因不能省略，并附带 `evidenceLinks` 中的官方链接（最多 3 条），如未提供则写「待补充」。\n"
                    + "7. 「免责声明」必须完整复述系统已经给出的 `referenceProbabilityNotice`，不得改写或精简。\n"
                    + "8. 严禁臆造任何 JSON 中没有的学校、专业、分数线、年份。如需要更多信息，请改写为“需要进一步查询招生章程”。\n"
                    + "9. 严禁使用“内部信息、内部数据、保录取、不滑档、不脱档、100%录取、成功率、录取率、上榜率、录取概率、命中率、稳上、必上、保送、包过、稳进”等承诺性词汇；只能使用“机会指数、风险等级、数据参考度、冲刺参考、稳妥参考、兜底参考”等描述。\n\n"
                    + "结构化输入 JSON：\n```json\n{{PLAN_SUMMARY_JSON}}\n```";

    private static final String ADVISOR_SKILL_DIGEST =
            "已读取 GitHub 公开 skills 摘要：\n"
                    + "来源 A：Eric-Yibo-Shen/zhangxuefeng-skillset，采用高考志愿顾问框架、可行集、就业倒推、AI 时代风险校正、冲稳保方案与城市/学校/专业取舍。\n"
                    + "来源 B：alchaincyf/zhangxuefeng-skill，采用数据优先、中位数原则、家庭背景分流、城市机会密度、不可替代性检验等公开策略抽象。\n"
                    + "产品修正：原 skill 中的角色扮演与绝对化表达不能直接使用；本系统只输出“张雪峰式公开策略参考”，不得冒充本人或机构，不得给录取承诺。\n"
                    + "执行规则：先看省份、分数/位次、选科和当前志愿可行集；再按就业倒推、城市机会、专业壁垒、家庭成本、招生计划变化、位次风险和人工复核清单给建议。";

    private static final String ADVISOR_SKILL_CHAT_TEMPLATE =
            "你要先读取下面的 GitHub skills 摘要，再结合当前 AI 志愿分析结果和结构化志愿方案回答用户问题。\n\n"
                    + "【skills 摘要】\n%s\n\n"
                    + "【当前 AI 志愿分析正文，可能为空】\n%s\n\n"
                    + "【结构化志愿方案 JSON】\n```json\n%s\n```\n\n"
                    + "【最近对话】\n%s\n\n"
                    + "【用户本轮问题】\n%s\n\n"
                    + "回答要求：\n"
                    + "1. 开头必须输出：“> " + ComplianceConstants.AI_GENERATED_NOTICE + "”。\n"
                    + "2. 只能基于上面的 JSON 和 AI 志愿分析回答，不得新增不存在的院校、专业、计划、分数线或位次。\n"
                    + "3. 必须明确这是 GitHub 公开 skills 策略参考，不是张雪峰本人或机构意见。\n"
                    + "4. 尽量短句、高密度，但不得使用录取承诺或绝对化营销措辞。\n"
                    + "5. 优先指出：该保留谁、该删谁、为什么、还要复核什么；必须具体到当前志愿里的院校和专业。";

    private static final Pattern AI_NOTICE_PATTERN = Pattern.compile("AI\\s*生成|本内容由\\s*AI\\s*生成");
    private static final List<String> BANNED_TERMS = List.of(
            "内部信息", "内部数据", "保录取", "不滑档", "不脱档", "100%录取",
            "成功率", "录取率", "上榜率", "录取概率", "命中率", "上岸概率",
            "稳上", "必上", "保送", "包过", "稳进"
    );
    private static final String SAFE_AI_FALLBACK =
            "> " + ComplianceConstants.AI_GENERATED_NOTICE + "\n\n"
                    + "AI 解读结果触发了安全复核，系统已停止展示原始输出。请以当前志愿列表、近三年录取记录、官方招生计划、招生章程、专业目录和对应省级考试院信息为准逐条复核。";

    @lombok.Data
    public static class AdvisorSkillChatMessage {
        private String role;
        private String content;
    }

    /**
     * SSE 流式分析志愿方案。
     * @param emitter SSE 通道
     * @param planSummary 已序列化的结构化 JSON 字符串
     */
    public void streamAnalysis(SseEmitter emitter, String planSummary) {
        metricsRecorder.incr(VolunteerMetricsRecorder.AI_ANALYSIS_TOTAL);
        boolean failed = false;
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                emitter.send(SseEmitter.event().data("[ERROR] AI 服务未配置或已停用"));
                emitter.complete();
                failed = true;
                return;
            }
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", config.getChatModel());
            requestBody.put("max_tokens", Math.max(config.getMaxTokens(), 2600));
            // 强制低温度，降低幻觉与措辞跑偏概率。
            double temperature = Math.min(0.4D, Math.max(0D, config.getTemperature()));
            requestBody.put("temperature", temperature);
            requestBody.put("stream", true);

            ArrayNode messages = requestBody.putArray("messages");

            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            String sysPrompt = config.getSystemPrompt() == null ? "" : config.getSystemPrompt();
            sysMsg.put("content", sysPrompt + "\n\n" + SYSTEM_GUARDRAIL);

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", buildAnalysisUserPrompt(planSummary));

            Request request = new Request.Builder()
                    .url(resolveChatCompletionsUrl(config.getBaseUrl()))
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    emitter.send(SseEmitter.event().data("[ERROR] AI 服务不可用"));
                    emitter.complete();
                    failed = true;
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                StringBuilder fullText = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                            if (!delta.isMissingNode() && !delta.isNull()) {
                                fullText.append(delta.asText());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                emitter.send(SseEmitter.event().data(sanitizeAiOutput(fullText.toString())));
                emitter.send(SseEmitter.event().data("[DONE]"));
            }
            emitter.complete();
        } catch (Exception e) {
            log.error("AI 流式分析异常", e);
            failed = true;
            try {
                emitter.send(SseEmitter.event().data("[ERROR] AI 服务暂时不可用"));
                emitter.complete();
            } catch (Exception ignored) {
            }
        } finally {
            if (failed) {
                metricsRecorder.incr(VolunteerMetricsRecorder.AI_ANALYSIS_FAILURE);
            }
        }
    }

    /**
     * 张雪峰.skills 对话：先注入公开 skills 摘要，再结合当前志愿方案与 AI 报告回答。
     */
    public String chatWithAdvisorSkill(String planSummary,
                                       String aiReport,
                                       String userQuestion,
                                       List<AdvisorSkillChatMessage> history) {
        return chatWithAdvisorSkill(ADVISOR_SKILL_DIGEST, planSummary, aiReport, userQuestion, history);
    }

    public String chatWithAdvisorSkill(String skillDigest,
                                       String planSummary,
                                       String aiReport,
                                       String userQuestion,
                                       List<AdvisorSkillChatMessage> history) {
        metricsRecorder.incr(VolunteerMetricsRecorder.AI_ADVISOR_CHAT_TOTAL);
        boolean failed = false;
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                failed = true;
                return SAFE_AI_FALLBACK;
            }

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", config.getChatModel());
            requestBody.put("max_tokens", Math.min(Math.max(config.getMaxTokens(), 1200), 1800));
            requestBody.put("temperature", Math.min(0.35D, Math.max(0D, config.getTemperature())));
            requestBody.put("stream", false);

            ArrayNode messages = requestBody.putArray("messages");

            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            String sysPrompt = config.getSystemPrompt() == null ? "" : config.getSystemPrompt();
            sysMsg.put("content", sysPrompt + "\n\n" + SYSTEM_GUARDRAIL
                    + "\n\n你现在提供的是“张雪峰.skills 填报服务”的对话式辅助，只能作为公开策略框架参考。");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", buildAdvisorSkillChatPrompt(skillDigest, planSummary, aiReport, userQuestion, history));

            Request request = new Request.Builder()
                    .url(resolveChatCompletionsUrl(config.getBaseUrl()))
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = advisorChatHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    failed = true;
                    log.warn("张雪峰.skills 对话调用失败: status={}", response.code());
                    return SAFE_AI_FALLBACK;
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                String reply = root.path("choices").path(0).path("message").path("content").asText("");
                return sanitizeAiOutput(reply);
            }
        } catch (Exception e) {
            failed = true;
            log.error("张雪峰.skills 对话异常", e);
            return SAFE_AI_FALLBACK;
        } finally {
            if (failed) {
                metricsRecorder.incr(VolunteerMetricsRecorder.AI_ADVISOR_CHAT_FAILURE);
            }
        }
    }

    /**
     * 返回当前 chat 模型名；未配置时返回空串，供调用方区分真实 AI vs 规则降级。
     */
    public String currentChatModel() {
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                return "";
            }
            String model = config.getChatModel();
            return model == null ? "" : model.trim();
        } catch (Exception e) {
            return "";
        }
    }

    public String generateStructuredAnalysisJson(String planSummary) {
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                return "";
            }
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", config.getChatModel());
            // 放宽 max_tokens 上限到 8000 容纳 thinking/reasoning 类模型（如 mimo-v2.5-pro）在长 prompt 下的推理消耗
            int maxTokens = Math.min(Math.max(config.getMaxTokens(), 4000), 8000);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", Math.min(0.25D, Math.max(0D, config.getTemperature())));
            requestBody.put("stream", false);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", (config.getSystemPrompt() == null ? "" : config.getSystemPrompt())
                    + "\n\n你必须只输出合法 JSON，不要输出 Markdown。所有用户可见字段必须使用机会指数、风险等级、数据参考度、冲刺参考、稳妥参考、兜底参考、仅供辅助参考。");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", "请基于下面志愿方案输出结构化 JSON，必须严格遵守以下字段约定，字段名和类型不能变：\n"
                    + "- conclusion: string\n"
                    + "- gradientSummary: object，键为 rush/stable/safe/floor，值为整数\n"
                    + "- bestKeepItem: object，含 universityName、majorName 等字段\n"
                    + "- highestRiskItem: object，含 universityName、majorName 等字段\n"
                    + "- diagnosisSections: array，每项必须是 {\"title\": string, \"content\": string} 两字段的 object，**不能是字符串**\n"
                    + "- topKeepDirections: array of string\n"
                    + "- topRiskPoints: array of string\n"
                    + "- actionSteps: array，每项必须是 {\"title\": string, \"content\": string} 两字段的 object\n"
                    + "- reorderAdvice: array of string\n"
                    + "- disclaimer: string\n\n"
                    + "不得编造方案外院校或专业。免责声明必须提醒以贵州省招生考试院、高校招生章程、当年招生计划和正式投档录取结果为准。\n```json\n"
                    + limitText(planSummary, 26000) + "\n```");

            Request request = new Request.Builder()
                    .url(resolveChatCompletionsUrl(config.getBaseUrl()))
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();
            try (Response response = advisorChatHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("结构化 AI 解读响应异常: status={}", response.code());
                    return "";
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode messageNode = root.path("choices").path(0).path("message");
                String content = messageNode.path("content").asText("");
                // thinking/reasoning 类模型（如 mimo-v2.5-pro）在长 prompt 下可能把 tokens 消耗在 reasoning_content，content 为空时尝试从 reasoning 里提取 JSON 片段
                if (content == null || content.isBlank()) {
                    String reasoning = messageNode.path("reasoning_content").asText("");
                    String extracted = extractJsonFromThinking(reasoning);
                    if (!extracted.isBlank()) {
                        log.warn("AI 主 content 为空，已从 reasoning_content 抽取 JSON 片段 {} 字", extracted.length());
                        return extracted;
                    }
                    log.warn("结构化 AI 解读 content 与 reasoning 均为空: finishReason={}, usage={}",
                            root.path("choices").path(0).path("finish_reason").asText(""),
                            root.path("usage").toString());
                    return "";
                }
                return content;
            }
        } catch (Exception e) {
            log.warn("结构化 AI 解读生成失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 思维链模型有时会在 reasoning_content 里以自然语言描述 JSON 结构；本方法尝试从中抽出第一个完整 JSON 对象，失败返回空。
     */
    private String extractJsonFromThinking(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) {
            return "";
        }
        int start = reasoning.indexOf('{');
        int end = reasoning.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        String candidate = reasoning.substring(start, end + 1);
        try {
            objectMapper.readTree(candidate);
            return candidate;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String renderAdvisorHistory(List<AdvisorSkillChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        int start = Math.max(0, history.size() - 8);
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            AdvisorSkillChatMessage message = history.get(i);
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(message.getRole()) ? "助手" : "用户";
            builder.append(role).append("：")
                    .append(limitText(message.getContent(), 1200))
                    .append('\n');
        }
        String rendered = builder.toString().trim();
        return rendered.isBlank() ? "无" : rendered;
    }

    private String buildAnalysisUserPrompt(String planSummary) {
        return USER_TASK_TEMPLATE.replace("{{PLAN_SUMMARY_JSON}}", planSummary == null ? "{}" : planSummary);
    }

    private String buildAdvisorSkillChatPrompt(String skillDigest,
                                               String planSummary,
                                               String aiReport,
                                               String userQuestion,
                                               List<AdvisorSkillChatMessage> history) {
        return ADVISOR_SKILL_CHAT_TEMPLATE
                .replace("【skills 摘要】\n%s", "【skills 摘要】\n" + limitText(skillDigest, 8000))
                .replace("【当前 AI 志愿分析正文，可能为空】\n%s", "【当前 AI 志愿分析正文，可能为空】\n" + limitText(aiReport, 3500))
                .replace("【结构化志愿方案 JSON】\n```json\n%s", "【结构化志愿方案 JSON】\n```json\n" + limitText(planSummary == null ? "{}" : planSummary, 26000))
                .replace("【最近对话】\n%s", "【最近对话】\n" + renderAdvisorHistory(history))
                .replace("【用户本轮问题】\n%s", "【用户本轮问题】\n" + limitText(userQuestion, 1000));
    }

    private String limitText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength)) + "\n[内容已截断]";
    }

    private String resolveChatCompletionsUrl(String rawBaseUrl) {
        String normalized = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private String sanitizeAiOutput(String raw) {
        String output = raw == null ? "" : raw.trim();
        if (output.isBlank()) {
            return SAFE_AI_FALLBACK;
        }
        boolean missingNotice = !AI_NOTICE_PATTERN.matcher(output).find();
        if (missingNotice) {
            output = "> " + ComplianceConstants.AI_GENERATED_NOTICE + "\n\n" + output;
        }
        if (complianceTextGuard != null) {
            return complianceTextGuard.sanitizeText("ai_output", "runtime", output);
        }
        boolean containsBannedTerm = BANNED_TERMS.stream().anyMatch(output::contains);
        if (containsBannedTerm) {
            log.warn("AI 输出触发安全兜底: containsBannedTerm=true");
            return SAFE_AI_FALLBACK;
        }
        return output + "\n\n" + ComplianceConstants.SAFE_ASSISTANT_NOTICE;
    }
}
