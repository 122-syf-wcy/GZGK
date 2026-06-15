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
import java.net.SocketTimeoutException;
import java.util.Locale;
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

    @Autowired(required = false)
    private AiCallLogService aiCallLogService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private final OkHttpClient advisorChatHttpClient = httpClient.newBuilder()
            .protocols(List.of(Protocol.HTTP_1_1))
            .callTimeout(115, TimeUnit.SECONDS)
            .readTimeout(110, TimeUnit.SECONDS)
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
    private static final String AI_UNAVAILABLE_FALLBACK =
            "> " + ComplianceConstants.AI_GENERATED_NOTICE + "\n\n"
                    + "AI 服务本次没有返回有效回复。当前志愿方案上下文仍已保留，你可以稍后重试，或先按页面中的志愿表、近三年录取记录、官方招生计划、招生章程和专业目录逐条复核。";

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
        long startedAt = System.currentTimeMillis();
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                failed = true;
                recordAiLog(false, null, null, config.getChatModel(), startedAt, "NOT_CONFIGURED", "AI 通道未配置或已停用");
                return temporaryUnavailableFallback("AI 通道未配置或已停用");
            }

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            String sysPrompt = config.getSystemPrompt() == null ? "" : config.getSystemPrompt();
            sysMsg.put("content", sysPrompt + "\n\n" + SYSTEM_GUARDRAIL
                    + "\n\n你现在提供的是“张雪峰.skills 填报服务”的对话式辅助，只能作为公开策略框架参考。");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", buildAdvisorSkillChatPrompt(skillDigest, planSummary, aiReport, userQuestion, history));

            ModelCallResult call = callAdvisorModel(config, messages,
                    Math.min(Math.max(config.getMaxTokens(), 900), 1200),
                    Math.min(0.25D, Math.max(0D, config.getTemperature())));
            recordAiLog(call.success(), call.httpStatus(), call.protocol(), config.getChatModel(),
                    startedAt, call.errorCode(), call.message());
            if (!call.success()) {
                failed = true;
                log.warn("张雪峰.skills 对话调用失败: protocol={}, status={}, error={}",
                        call.protocol(), call.httpStatus(), call.errorCode());
                return temporaryUnavailableFallback(call.userMessage());
            }
            return sanitizeAiOutput(call.content());
        } catch (Exception e) {
            failed = true;
            log.error("张雪峰.skills 对话异常", e);
            recordAiLog(false, null, null, null, startedAt, classifyException(e), friendlyExceptionMessage(e));
            return temporaryUnavailableFallback(friendlyExceptionMessage(e));
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

    /**
     * 专业选择规划 AI 解读：只解释规则评分结果，不生成正式志愿表、不编造院校分数线。
     */
    public String chatForMajorPlanner(String plannerContextJson) {
        long startedAt = System.currentTimeMillis();
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                recordMajorPlannerLog(false, null, null, null, startedAt, "NOT_CONFIGURED", "AI 通道未配置或已停用");
                return "";
            }
            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", (config.getSystemPrompt() == null ? "" : config.getSystemPrompt())
                    + "\n\n你是一名公益高考专业选择规划助手。只能基于用户问卷和系统规则评分结果解释专业方向，"
                    + "不得编造院校、分数线、招生计划、就业承诺或录取结论。"
                    + "必须使用 Markdown，结构包含：优势画像、推荐主线、备选方向、需要避开的坑、下一步怎么查学校和专业。"
                    + "必须使用“更适合优先了解 / 可以重点关注 / 建议谨慎选择”等保守表述。"
                    + "最后必须提醒：专业规划结果仅供参考，请结合个人兴趣、家庭情况、院校招生章程、选科要求和官方信息综合判断。");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", "请基于下面专业规划 JSON 输出一份简洁、可执行的专业选择解读。"
                    + "不要生成志愿表，不要生成院校清单，不要输出任何内部 code，不要承诺就业。\n\n```json\n"
                    + limitText(plannerContextJson == null ? "{}" : plannerContextJson, 18000)
                    + "\n```");

            ModelCallResult call = callAdvisorModel(config, messages,
                    Math.min(Math.max(config.getMaxTokens(), 1000), 1800),
                    Math.min(0.35D, Math.max(0D, config.getTemperature())));
            recordMajorPlannerLog(call.success(), call.httpStatus(), call.protocol(), config.getChatModel(),
                    startedAt, call.errorCode(), call.message());
            if (!call.success()) {
                log.warn("专业规划 AI 解读调用失败: protocol={}, status={}, error={}",
                        call.protocol(), call.httpStatus(), call.errorCode());
                return "";
            }
            return sanitizeAiOutput(call.content());
        } catch (Exception e) {
            log.warn("专业规划 AI 解读异常: {}", e.getMessage());
            recordMajorPlannerLog(false, null, null, null, startedAt, classifyException(e), friendlyExceptionMessage(e));
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
                    .append(limitText(message.getContent(), 500))
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
                .replace("【skills 摘要】\n%s", "【skills 摘要】\n" + limitText(skillDigest, 3600))
                .replace("【当前 AI 志愿分析正文，可能为空】\n%s", "【当前 AI 志愿分析正文，可能为空】\n" + limitText(aiReport, 1800))
                .replace("【结构化志愿方案 JSON】\n```json\n%s", "【结构化志愿方案 JSON】\n```json\n" + limitText(planSummary == null ? "{}" : planSummary, 12000))
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

    private void recordAiLog(boolean success, Integer httpStatus, String protocol, String model,
                             long startedAt, String errorCode, String message) {
        if (aiCallLogService != null) {
            String display = protocol == null || protocol.isBlank() ? message : protocol + " · " + message;
            long latency = startedAt <= 0 ? 0 : System.currentTimeMillis() - startedAt;
            aiCallLogService.record(AiCallLogService.SCENE_ADVISOR_CHAT, success, httpStatus, errorCode,
                    model, (int) Math.min(Integer.MAX_VALUE, Math.max(0, latency)), display);
        }
    }

    private void recordMajorPlannerLog(boolean success, Integer httpStatus, String protocol, String model,
                                       long startedAt, String errorCode, String message) {
        if (aiCallLogService != null) {
            String display = protocol == null || protocol.isBlank() ? message : protocol + " · " + message;
            long latency = startedAt <= 0 ? 0 : System.currentTimeMillis() - startedAt;
            aiCallLogService.record(AiCallLogService.SCENE_MAJOR_PLANNER, success, httpStatus, errorCode,
                    model, (int) Math.min(Integer.MAX_VALUE, Math.max(0, latency)), display);
        }
    }

    private ModelCallResult callAdvisorModel(AiConfigService.RuntimeAiConfig config,
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
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getChatModel());
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("stream", false);
        requestBody.set("messages", messages);
        Request request = requestBuilder(config, resolveChatCompletionsUrl(config.getBaseUrl()))
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = advisorChatHttpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                return ModelCallResult.httpFailure("chat_completions", response.code(), extractErrorCode(responseBody),
                        friendlyHttpMessage(response.code(), responseBody), briefBody(responseBody));
            }
            JsonNode root = objectMapper.readTree(responseBody.isBlank() ? "{}" : responseBody);
            JsonNode messageNode = root.path("choices").path(0).path("message");
            String content = messageNode.path("content").asText("");
            if (content == null || content.isBlank()) {
                content = messageNode.path("reasoning_content").asText("");
            }
            return ModelCallResult.fromContent("chat_completions", response.code(), content);
        } catch (Exception e) {
            return ModelCallResult.exception("chat_completions", e);
        }
    }

    private ModelCallResult callResponses(AiConfigService.RuntimeAiConfig config,
                                          ArrayNode messages,
                                          int maxTokens) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getChatModel());
        requestBody.put("input", renderMessagesForResponses(messages));
        requestBody.put("max_output_tokens", maxTokens);
        Request request = requestBuilder(config, resolveResponsesUrl(config.getBaseUrl()))
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = advisorChatHttpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                return ModelCallResult.httpFailure("responses", response.code(), extractErrorCode(responseBody),
                        friendlyHttpMessage(response.code(), responseBody), briefBody(responseBody));
            }
            JsonNode root = objectMapper.readTree(responseBody.isBlank() ? "{}" : responseBody);
            return ModelCallResult.fromContent("responses", response.code(), extractResponsesText(root));
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

    private String extractErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
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

    private String temporaryUnavailableFallback(String reason) {
        String suffix = reason == null || reason.isBlank() ? "" : "\n\n本次状态：" + reason;
        return AI_UNAVAILABLE_FALLBACK + suffix;
    }

    private String friendlyHttpMessage(int status, String responseBody) {
        String lower = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        if (status == 401 || status == 403 || lower.contains("invalid_api_key") || lower.contains("insufficient_balance")) {
            return "AI 服务返回鉴权、权限或余额错误，请在后台检查 API Key、模型权限和账户余额。";
        }
        if (status == 404 || status == 405 || status == 501) {
            return "当前服务商不支持本次尝试的 API 协议，系统已尝试兼容调用。";
        }
        return "AI 服务返回 HTTP " + status + "，请稍后重试或检查服务商状态。";
    }

    private String friendlyExceptionMessage(Exception e) {
        if (isTimeout(e)) {
            return "AI 服务响应超时，请稍后重试；如果连续出现，请在后台测试连接或更换服务商线路。";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "AI 服务暂时不可用，请稍后重试。";
        }
        return "AI 服务连接失败：" + message;
    }

    private String classifyException(Exception e) {
        if (isTimeout(e)) {
            return "TIMEOUT";
        }
        return "EXCEPTION";
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

    private String sanitizeAiOutput(String raw) {
        String output = raw == null ? "" : raw.trim();
        if (output.isBlank()) {
            return temporaryUnavailableFallback("AI 返回空内容，请稍后重试。");
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

    private record ModelCallResult(String protocol, boolean success, boolean unsupported, Integer httpStatus,
                                   String errorCode, String content, String userMessage, String message) {
        static ModelCallResult fromContent(String protocol, Integer httpStatus, String content) {
            if (content == null || content.isBlank()) {
                return new ModelCallResult(protocol, false, false, httpStatus, "EMPTY_CONTENT", "",
                        "AI 返回空内容，请稍后重试。", "AI 返回空内容");
            }
            return new ModelCallResult(protocol, true, false, httpStatus, null, content, "", "OK");
        }

        static ModelCallResult httpFailure(String protocol, int status, String errorCode, String userMessage, String body) {
            boolean unsupported = status == 404 || status == 405 || status == 501;
            String code = errorCode == null || errorCode.isBlank() ? "HTTP_" + status : errorCode;
            return new ModelCallResult(protocol, false, unsupported, status, code, "", userMessage, body);
        }

        static ModelCallResult exception(String protocol, Exception e) {
            boolean timeout = e instanceof SocketTimeoutException
                    || (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("timeout"));
            String userMessage = timeout
                    ? "AI 服务响应超时，请稍后重试；如果连续出现，请在后台测试连接或更换服务商线路。"
                    : "AI 服务连接失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage());
            return new ModelCallResult(protocol, false, false, null, timeout ? "TIMEOUT" : "EXCEPTION",
                    "", userMessage, userMessage);
        }
    }
}
