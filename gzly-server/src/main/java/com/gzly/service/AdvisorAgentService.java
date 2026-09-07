package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gzly.common.ComplianceConstants;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.entity.University;
import com.gzly.mapper.UniversityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 志愿问答 Agent：意图理解 → 工具调用循环 → 带依据回答。
 *
 * 与旧的"单轮塞上下文"模式不同，这里让模型通过 OpenAI tools 协议按需调用内部数据工具
 * （方案条目 / 一分一段 / 院校历年线 / skills 策略库检索），每一步以 SSE 事件透出，
 * 前端可以像 ChatGPT 一样展示"正在查询…"过程；最终回答经合规清洗后下发。
 *
 * 事件协议（data 均为 JSON，或 [DONE] / [ERROR] 前缀字符串）：
 *   {"type":"tool_call","tool":"query_rank","label":"查询一分一段位次"}
 *   {"type":"tool_result","tool":"query_rank","summary":"..."}
 *   {"type":"reasoning","text":"..."}   思考增量
 *   {"type":"content","text":"..."}     正文增量（分块）
 *   {"type":"final","text":"..."}       合规清洗后的全文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorAgentService {

    private static final int MAX_TOOL_ROUNDS = 4;
    private static final int TOOL_RESULT_MAX_CHARS = 900;
    private static final int CONTENT_CHUNK_CHARS = 48;

    private static final String AGENT_GUARDRAIL =
            "你是志愿填报智能助手，服务高考考生家庭。回答基于工具查询到的真实数据，"
                    + "不得编造学校、专业、分数线、招生计划或录取结论，不得给出录取承诺，"
                    + "不得使用保录取/不滑档/100%录取/稳上/必上/包过等绝对化措辞，概率只能表述为参考。"
                    + "需要数据时优先调用工具查询，而不是凭记忆回答；工具结果不足时如实说明。"
                    + "回答使用简洁的 Markdown，先给结论再给依据，引用工具数据时注明数据年份。"
                    + "结尾提醒：结果仅供参考，请以省考试院和高校官方信息为准。";

    private static final String REPORT_GUARDRAIL =
            "你是一名严谨的志愿填报数据助手，任务是为考生生成完整的方案解读报告。"
                    + "只能基于用户消息中的结构化 JSON 数据和工具查询结果进行分析，"
                    + "不得编造官方来源、学校、专业、分数线、招生计划或录取结论，不得给出录取承诺，"
                    + "不得使用保录取/不滑档/100%录取/稳上/必上/包过等绝对化或营销化措辞。"
                    + "生成报告前可调用工具核对关键数据（重点志愿的院校历年线、位次区间），最多查询少量必要项。"
                    + "报告必须使用规范 Markdown，结构包含：①一句话总判断；②报考建议；③梯度结构诊断；"
                    + "④最值得保留的志愿；⑤最需要警惕的风险；⑥强制人工复核清单；⑦下一步建议。"
                    + "正文开头必须显式输出：“" + ComplianceConstants.AI_GENERATED_NOTICE + "”，"
                    + "最后一段输出固定免责声明并提醒以省考试院和高校官方信息为准。";

    /** 循环降级动作：模型不支持 tools 或循环失败时执行。 */
    @FunctionalInterface
    interface FallbackAction {
        void run() throws Exception;
    }

    private final AiConfigService aiConfigService;
    private final AiService aiService;
    private final VolunteerService volunteerService;
    private final SkillsRagService skillsRagService;
    private final AlgorithmService algorithmService;
    private final ScoreLineService scoreLineService;
    private final UniversityMapper universityMapper;
    private final ObjectMapper objectMapper;
    private final ComplianceTextGuard complianceTextGuard;
    private final VolunteerMetricsRecorder metricsRecorder;

    /** 单次问答的请求级状态：累积 search_skills 命中的来源片段，用于结尾回传与落库。 */
    static class AgentRunContext {
        final String question;
        final List<SkillsRagService.SourceChunk> sources = new java.util.ArrayList<>();

        AgentRunContext(String question) {
            this.question = question;
        }
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(100, TimeUnit.SECONDS)
            .build();

    // ══════════════════ 主入口 ══════════════════

    public void streamAgentChat(SseEmitter emitter, Long planId, String accessKey, String question,
                                List<AiService.AdvisorSkillChatMessage> history) {
        metricsRecorder.incr(VolunteerMetricsRecorder.AGENT_CHAT_TOTAL);
        try {
            VolunteerService.PlanResult plan = volunteerService.getPlanResult(planId, accessKey);
            if (plan == null) {
                fail(emitter, "方案不存在或访问密钥无效");
                return;
            }
            if (question == null || question.trim().length() < 2) {
                fail(emitter, "请输入要咨询的问题");
                return;
            }
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                fail(emitter, "AI 服务未配置或已停用");
                return;
            }

            AgentRunContext context = new AgentRunContext(question.trim());
            ArrayNode messages = buildInitialMessages(config, plan, question, history);
            FallbackAction fallback = () -> {
                String reply = aiService.chatWithAdvisorSkill(
                        skillsRagService.planContextJson(plan), "", context.question, history);
                streamOutContent(emitter, reply);
                finishWithFinal(emitter, plan, context, reply);
            };
            runAgentLoop(emitter, config, plan, messages, context, fallback);
        } catch (Exception e) {
            log.error("Agent 问答异常", e);
            fail(emitter, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * AI 深度解读主报告的 Agent 版：生成前可调用工具核对关键数据；
     * 模型不支持 tools 或循环异常时无缝回落旧的 streamAnalysis 链路。
     */
    public void streamAgentReport(SseEmitter emitter, Long planId, String accessKey, String planSummary) {
        VolunteerService.PlanResult plan = null;
        try {
            plan = volunteerService.getPlanResult(planId, accessKey);
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (plan == null || !config.isUsable()) {
                aiService.streamAnalysis(emitter, planSummary);
                return;
            }
            metricsRecorder.incr(VolunteerMetricsRecorder.AGENT_CHAT_TOTAL);

            AgentRunContext context = new AgentRunContext("生成完整AI解读报告");
            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            String sysPrompt = config.getSystemPrompt() == null ? "" : config.getSystemPrompt();
            system.put("content", sysPrompt + "\n\n" + REPORT_GUARDRAIL);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", "请基于以下志愿方案数据生成完整解读报告；"
                    + "必要时先调用工具核对关键数据，再输出报告。\n\n" + planSummary);

            FallbackAction fallback = () -> aiService.streamAnalysis(emitter, planSummary);
            runAgentLoop(emitter, config, plan, messages, context, fallback);
        } catch (Exception e) {
            log.warn("Agent 报告链路异常，回落旧链路: {}", e.getMessage());
            aiService.streamAnalysis(emitter, planSummary);
        }
    }

    private void runAgentLoop(SseEmitter emitter, AiConfigService.RuntimeAiConfig config,
                              VolunteerService.PlanResult plan, ArrayNode messages,
                              AgentRunContext context, FallbackAction fallback) throws Exception {
        boolean toolsSupported = true;
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            boolean allowTools = toolsSupported && round < MAX_TOOL_ROUNDS;
            JsonNode message;
            try {
                message = callLlm(config, messages, allowTools);
            } catch (ToolsUnsupportedException e) {
                // 网关/模型不支持 tools：执行调用方提供的降级动作，保证链路可用
                log.warn("模型不支持 tools，Agent 触发降级: {}", e.getMessage());
                fallback.run();
                return;
            }

            String reasoning = message.path("reasoning_content").asText("");
            if (!reasoning.isBlank()) {
                sendJson(emitter, "reasoning", reasoning);
            }

            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && toolCalls.size() > 0) {
                messages.add(message.deepCopy());
                for (JsonNode toolCall : toolCalls) {
                    String callId = toolCall.path("id").asText("");
                    String toolName = toolCall.path("function").path("name").asText("");
                    String argsJson = toolCall.path("function").path("arguments").asText("{}");
                    log.info("Agent 工具调用: planId={}, round={}, tool={}", plan.getId(), round + 1, toolName);
                    metricsRecorder.incr(VolunteerMetricsRecorder.AGENT_TOOL_CALLS);
                    sendToolEvent(emitter, "tool_call", toolName, toolLabel(toolName, argsJson));
                    String result = executeTool(toolName, argsJson, plan, context);
                    sendToolEvent(emitter, "tool_result", toolName, brief(result, 140));
                    ObjectNode toolMsg = messages.addObject();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", callId);
                    toolMsg.put("content", result);
                }
                continue;
            }

            String content = message.path("content").asText("");
            streamOutContent(emitter, content);
            finishWithFinal(emitter, plan, context, content);
            return;
        }
        fail(emitter, "分析轮次超限，请换个问法再试");
    }

    // ══════════════════ LLM 调用 ══════════════════

    static class ToolsUnsupportedException extends RuntimeException {
        ToolsUnsupportedException(String message) {
            super(message);
        }
    }

    private JsonNode callLlm(AiConfigService.RuntimeAiConfig config, ArrayNode messages,
                             boolean allowTools) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getChatModel());
        body.put("max_tokens", Math.min(Math.max(config.getMaxTokens(), 1500), 2200));
        body.put("temperature", Math.min(0.3D, Math.max(0D, config.getTemperature())));
        body.put("stream", false);
        body.set("messages", messages.deepCopy());
        if (allowTools) {
            body.set("tools", buildToolDefinitions());
            body.put("tool_choice", "auto");
        }

        Request request = new Request.Builder()
                .url(chatCompletionsUrl(config.getBaseUrl()))
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                if (allowTools && response.code() == 400
                        && responseBody.toLowerCase(Locale.ROOT).contains("tool")) {
                    throw new ToolsUnsupportedException("HTTP 400: " + brief(responseBody, 200));
                }
                throw new IllegalStateException("AI 服务响应异常: HTTP " + response.code());
            }
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").path(0).path("message");
        }
    }

    private ArrayNode buildInitialMessages(AiConfigService.RuntimeAiConfig config,
                                           VolunteerService.PlanResult plan, String question,
                                           List<AiService.AdvisorSkillChatMessage> history) {
        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        String sysPrompt = config.getSystemPrompt() == null ? "" : config.getSystemPrompt();
        system.put("content", sysPrompt + "\n\n" + AGENT_GUARDRAIL
                + "\n\n当前考生方案概要（JSON）：\n" + brief(skillsRagService.planContextJson(plan), 2600));

        if (history != null) {
            for (AiService.AdvisorSkillChatMessage item : history) {
                if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(item.getRole()) ? "assistant" : "user";
                ObjectNode node = messages.addObject();
                node.put("role", role);
                node.put("content", brief(item.getContent(), 1200));
            }
        }

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", question.trim());
        return messages;
    }

    // ══════════════════ 工具定义与执行 ══════════════════

    private ArrayNode buildToolDefinitions() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(toolDef("get_plan_items",
                "查询当前志愿方案里的条目。可按梯度（冲/稳/保/垫）或院校/专业关键词过滤。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"gradient\":{\"type\":\"string\",\"description\":\"冲/稳/保/垫，可选\"},"
                        + "\"keyword\":{\"type\":\"string\",\"description\":\"院校或专业关键词，可选\"},"
                        + "\"limit\":{\"type\":\"integer\",\"description\":\"最多返回条数，默认10\"}},"
                        + "\"required\":[]}"));
        tools.add(toolDef("query_rank",
                "按分数查询官方一分一段位次区间（科类自动使用考生本人的）。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"score\":{\"type\":\"integer\",\"description\":\"高考总分\"}},"
                        + "\"required\":[\"score\"]}"));
        tools.add(toolDef("query_school_history",
                "查询某院校（可指定专业）近三年的录取最低分与最低位次。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"schoolName\":{\"type\":\"string\",\"description\":\"院校名称，可为简称\"},"
                        + "\"majorName\":{\"type\":\"string\",\"description\":\"专业名称，可选\"}},"
                        + "\"required\":[\"schoolName\"]}"));
        tools.add(toolDef("search_skills",
                "检索张雪峰公开填报策略库，获取报考方法论片段。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"检索关键词或问题\"}},"
                        + "\"required\":[\"query\"]}"));
        return tools;
    }

    private ObjectNode toolDef(String name, String description, String parametersJson) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode fn = tool.putObject("function");
        fn.put("name", name);
        fn.put("description", description);
        try {
            fn.set("parameters", objectMapper.readTree(parametersJson));
        } catch (Exception e) {
            fn.putObject("parameters").put("type", "object");
        }
        return tool;
    }

    /** 执行工具并返回给模型的 JSON 字符串；任何异常都转为可读错误信息，避免中断循环。包级可见便于单测。 */
    String executeTool(String toolName, String argsJson, VolunteerService.PlanResult plan, AgentRunContext context) {
        try {
            JsonNode args = objectMapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            return switch (toolName) {
                case "get_plan_items" -> toolGetPlanItems(args, plan);
                case "query_rank" -> toolQueryRank(args, plan);
                case "query_school_history" -> toolQuerySchoolHistory(args, plan);
                case "search_skills" -> toolSearchSkills(args, context);
                default -> "{\"error\":\"未知工具 " + toolName + "\"}";
            };
        } catch (Exception e) {
            log.warn("Agent 工具执行失败: tool={}, err={}", toolName, e.getMessage());
            return "{\"error\":\"工具执行失败，请基于已有信息回答\"}";
        }
    }

    private String toolGetPlanItems(JsonNode args, VolunteerService.PlanResult plan) throws Exception {
        String gradient = args.path("gradient").asText("");
        String keyword = args.path("keyword").asText("");
        int limit = Math.max(1, Math.min(20, args.path("limit").asInt(10)));
        ArrayNode result = objectMapper.createArrayNode();
        for (VolunteerService.VolunteerItem item : plan.getItems()) {
            if (!gradient.isBlank() && !gradient.equals(item.getGradient())) {
                continue;
            }
            if (!keyword.isBlank()
                    && !contains(item.getUniversityName(), keyword)
                    && !contains(item.getMajorName(), keyword)) {
                continue;
            }
            ObjectNode node = result.addObject();
            node.put("index", item.getIndex());
            node.put("university", item.getUniversityName());
            node.put("major", item.getMajorName());
            node.put("gradient", item.getGradient());
            node.put("chanceScore", item.getChanceScore());
            node.put("historyMinRank", item.getHistoryMinRank());
            node.put("riskLevel", item.getRiskLevel());
            if (result.size() >= limit) {
                break;
            }
        }
        return brief(result.toString(), TOOL_RESULT_MAX_CHARS);
    }

    private String toolQueryRank(JsonNode args, VolunteerService.PlanResult plan) throws Exception {
        int score = args.path("score").asInt(0);
        if (score <= 0 || score > 750) {
            return "{\"error\":\"分数不合法\"}";
        }
        String subjectType = subjectTypeOf(plan);
        AlgorithmService.RankEstimate estimate = algorithmService.estimateRank(score, subjectType);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("score", score);
        node.put("subjectType", subjectType);
        node.put("estimatedRank", estimate.getEstimatedRank());
        node.put("rankLow", estimate.getRankLow());
        node.put("rankHigh", estimate.getRankHigh());
        node.put("referenceYear", estimate.getReferenceYear());
        node.put("note", brief(estimate.getNote(), 200));
        return node.toString();
    }

    private String toolQuerySchoolHistory(JsonNode args, VolunteerService.PlanResult plan) throws Exception {
        String schoolName = args.path("schoolName").asText("").trim();
        String majorName = args.path("majorName").asText("").trim();
        if (schoolName.isBlank()) {
            return "{\"error\":\"缺少院校名称\"}";
        }
        University university = universityMapper.selectList(new LambdaQueryWrapper<University>()
                        .like(University::getName, schoolName)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (university == null) {
            return "{\"error\":\"未找到院校：" + schoolName + "\"}";
        }
        List<ScoreLineService.ScoreLineView> views = scoreLineService.findRecentHistory(
                university.getSchoolId(), majorName.isBlank() ? null : majorName, subjectTypeOf(plan), 3);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("university", university.getName());
        ArrayNode rows = node.putArray("history");
        for (ScoreLineService.ScoreLineView view : views) {
            ObjectNode row = rows.addObject();
            row.put("year", view.getYear());
            row.put("major", view.getMajorName());
            row.put("minScore", view.getMinScore());
            row.put("minRank", view.getMinRank());
            row.put("batch", view.getBatch());
        }
        if (rows.isEmpty()) {
            node.put("note", "该院校在当前科类下暂无可用历年记录");
        }
        return brief(node.toString(), TOOL_RESULT_MAX_CHARS);
    }

    private String toolSearchSkills(JsonNode args, AgentRunContext context) {
        String query = args.path("query").asText("").trim();
        if (query.isBlank()) {
            return "{\"error\":\"缺少检索关键词\"}";
        }
        SkillsRagService.RetrievedDigest digest = skillsRagService.retrieveDigest(query);
        if (digest.getDigest() == null || digest.getDigest().isBlank()) {
            return "{\"note\":\"策略库没有命中相关片段\"}";
        }
        if (context != null && digest.getChunks() != null) {
            context.sources.addAll(digest.getChunks());
        }
        return brief(digest.getDigest(), TOOL_RESULT_MAX_CHARS);
    }

    // ══════════════════ 输出与工具函数 ══════════════════

    /** 全文按小块下发 content 事件，前端获得与流式一致的渐显体验。 */
    private void streamOutContent(SseEmitter emitter, String content) throws Exception {
        String text = content == null ? "" : content;
        for (int i = 0; i < text.length(); i += CONTENT_CHUNK_CHARS) {
            sendJson(emitter, "content", text.substring(i, Math.min(text.length(), i + CONTENT_CHUNK_CHARS)));
        }
    }

    private void finishWithFinal(SseEmitter emitter, VolunteerService.PlanResult plan,
                                 AgentRunContext context, String content) throws Exception {
        String sanitized = complianceTextGuard.sanitizeText(
                "skills_agent", String.valueOf(plan.getId()), content == null ? "" : content);
        if (!sanitized.contains(ComplianceConstants.AI_GENERATED_NOTICE)) {
            sanitized = "> " + ComplianceConstants.AI_GENERATED_NOTICE + "\n\n" + sanitized;
        }
        // 命中的策略库来源片段回传前端（来源弹窗），并与旧链路共用日志表落库审计
        if (context != null && !context.sources.isEmpty()) {
            ObjectNode sourcesNode = objectMapper.createObjectNode();
            sourcesNode.put("type", "sources");
            sourcesNode.set("chunks", objectMapper.valueToTree(context.sources));
            emitter.send(SseEmitter.event().data(sourcesNode.toString()));
        }
        try {
            skillsRagService.logAgentAnswer(plan.getId(),
                    context == null ? "" : context.question,
                    context == null ? List.of() : context.sources,
                    content, sanitized);
        } catch (Exception e) {
            log.warn("Agent 问答落库失败: {}", e.getMessage());
        }
        sendJson(emitter, "final", sanitized);
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    }

    private void fail(SseEmitter emitter, String message) {
        metricsRecorder.incr(VolunteerMetricsRecorder.AGENT_CHAT_FAILURE);
        try {
            emitter.send(SseEmitter.event().data("[ERROR] " + message));
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private void sendJson(SseEmitter emitter, String type, String text) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("text", text);
        emitter.send(SseEmitter.event().data(node.toString()));
    }

    private void sendToolEvent(SseEmitter emitter, String type, String tool, String label) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("tool", tool);
        node.put("label", label);
        emitter.send(SseEmitter.event().data(node.toString()));
    }

    /** 工具调用的用户可读标签。包级可见便于单测。 */
    String toolLabel(String toolName, String argsJson) {
        String detail = "";
        try {
            JsonNode args = objectMapper.readTree(argsJson == null ? "{}" : argsJson);
            detail = switch (toolName) {
                case "query_rank" -> args.path("score").asInt(0) > 0 ? args.path("score").asInt(0) + " 分" : "";
                case "query_school_history" -> args.path("schoolName").asText("");
                case "search_skills" -> args.path("query").asText("");
                case "get_plan_items" -> args.path("gradient").asText(args.path("keyword").asText(""));
                default -> "";
            };
        } catch (Exception ignored) {
        }
        String base = switch (toolName) {
            case "query_rank" -> "查询官方一分一段";
            case "query_school_history" -> "查询院校历年录取线";
            case "search_skills" -> "检索填报策略库";
            case "get_plan_items" -> "读取当前志愿方案";
            default -> "调用工具 " + toolName;
        };
        return detail.isBlank() ? base : base + "：" + brief(detail, 24);
    }

    private String subjectTypeOf(VolunteerService.PlanResult plan) {
        return "历史".equals(plan.getFirstSubject()) ? "历史类" : "物理类";
    }

    private String chatCompletionsUrl(String rawBaseUrl) {
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

    private boolean contains(String text, String keyword) {
        return text != null && keyword != null && text.contains(keyword);
    }

    private String brief(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…";
    }
}
