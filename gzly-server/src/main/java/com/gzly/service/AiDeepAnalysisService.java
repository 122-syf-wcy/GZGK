package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.VolunteerDiagnosisEngine;
import com.gzly.common.ComplianceConstants;
import com.gzly.common.exception.BizException;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.VolunteerAiAnalysis;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.mapper.VolunteerAiAnalysisMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDeepAnalysisService {

    private final VolunteerService volunteerService;
    private final VolunteerAiAnalysisMapper analysisMapper;
    private final PlanHistoryMapper planHistoryMapper;
    private final ObjectMapper objectMapper;
    private final ComplianceTextGuard complianceTextGuard;
    private final AiService aiService;
    private final VolunteerDiagnosisEngine volunteerDiagnosisEngine;

    public AiAnalysisVO generate(Long planId, String accessKey, boolean forceRefresh) {
        VolunteerService.PlanResult plan = requirePlan(planId, accessKey);
        if (!forceRefresh) {
            AiAnalysisVO existing = get(planId, accessKey);
            if (existing != null && "completed".equals(existing.getStatus())) {
                return existing;
            }
        }

        List<VolunteerService.VolunteerItem> items = plan.getItems() == null ? List.of() : plan.getItems();
        // collectDataIssues 可能返回 immutable List.of()，下面 appendDiagnosisWarnings 需要能 add，这里包一层 ArrayList
        List<String> dataIssues = new java.util.ArrayList<>(collectDataIssues(items));
        // 补充诊断警报到 dataIssues（VolunteerDiagnosisEngine 13 维详情中的 warnings）
        Map<String, Object> diagnosis = safeDiagnose(plan, items);
        appendDiagnosisWarnings(dataIssues, diagnosis);
        AiAnalysisVO aiVo = tryGenerateWithAi(plan);
        if (aiVo != null) {
            aiVo.setDataIssues(dataIssues);
            if (aiVo.getAiFallbackUsed() == null) aiVo.setAiFallbackUsed(false);
            if (aiVo.getAiModelVersion() == null || aiVo.getAiModelVersion().isBlank()) {
                String model = aiService.currentChatModel();
                aiVo.setAiModelVersion(model.isBlank() ? "ai-runtime-unknown" : model);
            }
            save(planId, plan, aiVo);
            return aiVo;
        }
        VolunteerService.VolunteerItem best = items.stream()
                .max(Comparator.comparingInt((VolunteerService.VolunteerItem item) -> item.getChanceScore())
                        .thenComparingDouble(VolunteerService.VolunteerItem::getDataConfidence))
                .orElse(null);
        VolunteerService.VolunteerItem risk = items.stream()
                .min(Comparator.comparingInt((VolunteerService.VolunteerItem item) -> item.getChanceScore())
                        .thenComparingInt(VolunteerService.VolunteerItem::getDataConfidenceScore))
                .orElse(null);

        AiAnalysisVO vo = new AiAnalysisVO();
        vo.setPlanId(planId);
        vo.setStatus("completed");
        vo.setProgress(100);
        vo.setGradientSummary(Map.of(
                "rush", count(items, "冲"),
                "stable", count(items, "稳"),
                "safe", count(items, "保"),
                "floor", count(items, "垫")));
        vo.setBestKeepItem(toItemMap(best));
        vo.setHighestRiskItem(toItemMap(risk));
        // 优先用 VolunteerDiagnosisEngine.summary 作 conclusion 主身，避免纯模板语义不匹配实际方案状态
        String diagnosisSummary = readStringFromMap(diagnosis, "summary");
        String conclusion = diagnosisSummary != null && !diagnosisSummary.isBlank()
                ? diagnosisSummary
                : String.format("当前方案共%d个志愿项，建议先保留数据参考度较高的稳妥参考项，再逐条复核冲刺参考和高风险项。", items.size());
        vo.setConclusion(complianceTextGuard.sanitizeText("ai_analysis_conclusion", String.valueOf(planId), conclusion));
        vo.setDiagnosisSections(List.of(
                section("梯度是否合理", String.format("当前梯度为冲%d / 稳%d / 保%d / 垫%d。请优先确认稳妥参考和兜底参考数量是否满足你的风险偏好。",
                        count(items, "冲"), count(items, "稳"), count(items, "保"), count(items, "垫"))),
                section("专业是否集中", "如果多个志愿集中在同一专业方向，建议横向比较专业目录、课程设置、学费、校区和培养模式。"),
                section("兜底参考是否足够", count(items, "垫") < 8
                        ? "兜底参考偏少，建议放宽专业、城市、学校层次或费用限制后重新生成。"
                        : "兜底参考数量具备一定支撑，但仍需结合当年招生计划和正式投档情况复核。")));
        vo.setTopKeepDirections(items.stream()
                .sorted(Comparator.comparingInt(VolunteerService.VolunteerItem::getChanceScore).reversed())
                .limit(5)
                .map(item -> item.getUniversityName() + " · " + item.getMajorName())
                .toList());
        vo.setTopRiskPoints(items.stream()
                .sorted(Comparator.comparingInt(VolunteerService.VolunteerItem::getChanceScore))
                .limit(3)
                .map(item -> item.getUniversityName() + " · " + item.getMajorName() + " 需要优先核对招生章程、位次波动和专业限制。")
                .toList());
        vo.setActionSteps(List.of(
                section("优先保留主锚点", best == null ? "先筛选数据参考度较高的稳妥参考项。" : best.getUniversityName() + " · " + best.getMajorName() + " 可作为当前方案里的核心保留项之一。"),
                section("先处理最高风险项", risk == null ? "先复核冲刺参考项。" : risk.getUniversityName() + " · " + risk.getMajorName() + " 需要优先核对招生章程、位次波动和专业限制。"),
                section("按梯度重新排位", "先把稳妥参考和兜底参考的核心项排顺，再决定哪些冲刺参考项值得放在前段。"),
                section("优先看专业级数据", "如果两项接近，优先保留专业级目录可信度更高的志愿，再把院校级回退数据放到后段做备选。")));
        vo.setReorderAdvice(List.of("前段放置经过章程复核后的冲刺参考项。", "中段保留机会指数和数据参考度都较高的主体项。", "末段保留兜底参考项，避免全部集中在同一城市或专业。"));
        vo.setDisclaimer(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
        vo.setAiModelVersion("rule-template-v1");
        vo.setAiFallbackUsed(true);
        if (vo.getAiFallbackReason() == null) {
            vo.setAiFallbackReason("AI 未配置或返回为空，已使用规则模板生成解读");
        }
        vo.setDataIssues(dataIssues);

        save(planId, plan, vo);
        return vo;
    }

    /**
     * 调用 {@link VolunteerDiagnosisEngine} 对当前方案做 13 维诊断；
     * 任何异常都吞掉并返回空 Map，避免影响主流程。
     */
    private Map<String, Object> safeDiagnose(VolunteerService.PlanResult plan, List<VolunteerService.VolunteerItem> items) {
        try {
            // 优先用 plan 中已经计算好的 diagnosis（VolunteerService.generate 末尾产出）
            if (plan != null && plan.getDiagnosis() != null && !plan.getDiagnosis().isEmpty()) {
                return plan.getDiagnosis();
            }
            int policyMaxCount = plan == null || plan.getTargetCount() <= 0 ? 96 : plan.getTargetCount();
            VolunteerDiagnosisEngine.DiagnosisContext ctx = new VolunteerDiagnosisEngine.DiagnosisContext();
            ctx.setRiskPreference(plan == null ? null : plan.getStrategyMode());
            return volunteerDiagnosisEngine.diagnose(items, policyMaxCount, ctx);
        } catch (Exception e) {
            log.warn("[ai-analysis] VolunteerDiagnosisEngine 诊断失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 把 diagnosis.warnings 合并到 dataIssues 末尾，控制总长在 12 条内防止泛滥。 */
    private void appendDiagnosisWarnings(List<String> dataIssues, Map<String, Object> diagnosis) {
        if (dataIssues == null || diagnosis == null || diagnosis.isEmpty()) return;
        Object warnings = diagnosis.get("warnings");
        if (!(warnings instanceof List<?> list)) return;
        for (Object w : list) {
            if (dataIssues.size() >= 12) break;
            if (w instanceof String s && !s.isBlank() && !dataIssues.contains(s)) {
                dataIssues.add(s);
            }
        }
    }

    /** 从 Map 安全读取字符串字段，键缺失或类型不匹配返回 null。 */
    private String readStringFromMap(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    /**
     * 提取数据质量问题清单：低参考度志愿、缺失关键字段、需人工复核标记等。
     * 输出长度封顶 8 条避免噪声，文案统一加合规审查兜底。
     */
    private List<String> collectDataIssues(List<VolunteerService.VolunteerItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        List<String> issues = new java.util.ArrayList<>();
        long lowConfidence = items.stream().filter(it -> it.getDataConfidenceScore() > 0 && it.getDataConfidenceScore() < 55).count();
        if (lowConfidence > 0) {
            issues.add(String.format("有 %d 条志愿数据参考度低于 55，建议优先用招生章程逐条复核。", lowConfidence));
        }
        long manualReview = items.stream().filter(VolunteerService.VolunteerItem::isNeedsManualReview).count();
        if (manualReview > 0) {
            issues.add(String.format("有 %d 条志愿被标记为需人工复核，请结合官方目录与章程二次确认。", manualReview));
        }
        long collegeLevel = items.stream().filter(it -> "院校级".equals(it.getDataSourceType())).count();
        if (collegeLevel > 0) {
            issues.add(String.format("有 %d 条志愿仅有院校级历史数据，专业级数据缺失，请重点确认目标专业当年招生。", collegeLevel));
        }
        long missingPlan = items.stream().filter(it -> it.getLatestPlanCount() == null || it.getLatestPlanCount() <= 0).count();
        if (missingPlan > 0) {
            issues.add(String.format("有 %d 条志愿当年招生计划数缺失或为零，请优先核对最新招生计划。", missingPlan));
        }
        return issues.size() > 8 ? issues.subList(0, 8) : issues;
    }

    private AiAnalysisVO tryGenerateWithAi(VolunteerService.PlanResult plan) {
        String raw = null;
        try {
            raw = aiService.generateStructuredAnalysisJson(buildAnalysisContext(plan));
            if (raw == null || raw.isBlank()) {
                log.warn("[ai-analysis] 结构化 AI 解读返回空，planId={}", plan.getId());
                return null;
            }
            String stripped = stripJsonFence(raw);
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(stripped);
            AiAnalysisVO vo = fromJsonNode(root);
            if (vo.getConclusion() == null || vo.getConclusion().isBlank()) {
                log.warn("[ai-analysis] AI 返回 JSON 缺少 conclusion，planId={}，raw 前 240 字：{}",
                        plan.getId(), raw.substring(0, Math.min(240, raw.length())));
                return null;
            }
            vo.setPlanId(plan.getId());
            vo.setStatus("completed");
            vo.setProgress(100);
            sanitizeStructured(vo, plan.getId());
            return vo;
        } catch (Exception e) {
            String preview = raw == null ? "<null>" : raw.substring(0, Math.min(240, raw.length()));
            log.warn("[ai-analysis] AI 解读解析失败，回退规则模板 planId={}, error={}, raw240={}",
                    plan.getId(), e.getMessage(), preview);
            return null;
        }
    }

    /**
     * 宽容的 VO 反序列化：diagnosisSections / actionSteps 的元素若是字符串而非 {title,content} 对象，自动归一化为 {title:"", content:str}。
     */
    private AiAnalysisVO fromJsonNode(com.fasterxml.jackson.databind.JsonNode root) {
        AiAnalysisVO vo = new AiAnalysisVO();
        vo.setConclusion(root.path("conclusion").asText(""));
        vo.setDisclaimer(root.path("disclaimer").asText(""));
        com.fasterxml.jackson.databind.JsonNode gs = root.path("gradientSummary");
        if (gs.isObject()) {
            Map<String, Integer> map = new LinkedHashMap<>();
            gs.fields().forEachRemaining(e -> {
                try { map.put(e.getKey(), e.getValue().asInt(0)); } catch (Exception ignored) {}
            });
            vo.setGradientSummary(map);
        }
        vo.setBestKeepItem(toMap(root.path("bestKeepItem")));
        vo.setHighestRiskItem(toMap(root.path("highestRiskItem")));
        vo.setDiagnosisSections(toSectionList(root.path("diagnosisSections")));
        vo.setActionSteps(toSectionList(root.path("actionSteps")));
        vo.setTopKeepDirections(toStringList(root.path("topKeepDirections")));
        vo.setTopRiskPoints(toStringList(root.path("topRiskPoints")));
        vo.setReorderAdvice(toStringList(root.path("reorderAdvice")));
        return vo;
    }

    private List<Map<String, Object>> toSectionList(com.fasterxml.jackson.databind.JsonNode node) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (com.fasterxml.jackson.databind.JsonNode item : node) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (item.isObject()) {
                m.put("title", item.path("title").asText(""));
                m.put("content", item.path("content").asText(""));
            } else {
                m.put("title", "");
                m.put("content", item.asText(""));
            }
            result.add(m);
        }
        return result;
    }

    private List<String> toStringList(com.fasterxml.jackson.databind.JsonNode node) {
        List<String> result = new java.util.ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (com.fasterxml.jackson.databind.JsonNode item : node) {
            result.add(item.isTextual() ? item.asText() : item.toString());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String buildAnalysisContext(VolunteerService.PlanResult plan) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("planId", plan.getId());
        root.put("provinceCode", plan.getProvinceCode());
        root.put("score", plan.getTotalScore());
        root.put("rank", plan.getProvinceRank());
        root.put("batch", plan.getTargetBatch());
        root.put("subject", plan.getFirstSubject());
        root.put("strategyMode", plan.getStrategyMode());
        root.put("gradientSummary", Map.of(
                "rush", count(plan.getItems() == null ? List.of() : plan.getItems(), "冲"),
                "stable", count(plan.getItems() == null ? List.of() : plan.getItems(), "稳"),
                "safe", count(plan.getItems() == null ? List.of() : plan.getItems(), "保"),
                "floor", count(plan.getItems() == null ? List.of() : plan.getItems(), "垫")));
        root.put("items", plan.getItems() == null ? List.of() : plan.getItems().stream().limit(96).map(item -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("index", item.getIndex());
            map.put("gradient", item.getGradient());
            map.put("universityName", item.getUniversityName());
            map.put("majorName", item.getMajorName());
            map.put("city", item.getCity());
            map.put("chanceScore", item.getChanceScore());
            map.put("chanceLevel", item.getChanceLevel());
            map.put("riskLevel", item.getRiskLevel());
            map.put("dataConfidence", item.getDataConfidence());
            map.put("predictedMinRank", item.getPredictedMinRank());
            map.put("rankDiff", item.getRankDiff());
            map.put("planExpansionIndex", item.getPlanExpansionIndex());
            map.put("schoolEnrollmentIndex", item.getSchoolEnrollmentIndex());
            map.put("recommendReason", item.getRecommendReason());
            map.put("riskReason", item.getRiskReason());
            return map;
        }).toList());
        return objectMapper.writeValueAsString(root);
    }

    private void sanitizeStructured(AiAnalysisVO vo, Long planId) {
        vo.setConclusion(complianceTextGuard.sanitizeText("ai_analysis_conclusion", String.valueOf(planId), vo.getConclusion()));
        if (vo.getDiagnosisSections() != null) {
            vo.setDiagnosisSections(vo.getDiagnosisSections().stream()
                    .map(section -> sanitizeSection(section, planId, "ai_analysis_section"))
                    .toList());
        }
        if (vo.getActionSteps() != null) {
            vo.setActionSteps(vo.getActionSteps().stream()
                    .map(section -> sanitizeSection(section, planId, "ai_analysis_action"))
                    .toList());
        }
        if (vo.getTopKeepDirections() != null) {
            vo.setTopKeepDirections(vo.getTopKeepDirections().stream()
                    .map(text -> complianceTextGuard.sanitizeText("ai_analysis_keep", String.valueOf(planId), text))
                    .toList());
        }
        if (vo.getTopRiskPoints() != null) {
            vo.setTopRiskPoints(vo.getTopRiskPoints().stream()
                    .map(text -> complianceTextGuard.sanitizeText("ai_analysis_risk", String.valueOf(planId), text))
                    .toList());
        }
        if (vo.getReorderAdvice() != null) {
            vo.setReorderAdvice(vo.getReorderAdvice().stream()
                    .map(text -> complianceTextGuard.sanitizeText("ai_analysis_reorder", String.valueOf(planId), text))
                    .toList());
        }
        vo.setDisclaimer(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
    }

    private Map<String, Object> sanitizeSection(Map<String, Object> section, Long planId, String type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", section == null ? "" : section.getOrDefault("title", ""));
        map.put("content", complianceTextGuard.sanitizeText(type, String.valueOf(planId),
                section == null ? "" : String.valueOf(section.getOrDefault("content", ""))));
        return map;
    }

    private String stripJsonFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }

    public AiAnalysisVO get(Long planId, String accessKey) {
        requirePlan(planId, accessKey);
        VolunteerAiAnalysis row = analysisMapper.selectOne(new LambdaQueryWrapper<VolunteerAiAnalysis>()
                .eq(VolunteerAiAnalysis::getPlanId, planId)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        try {
            return objectMapper.readValue(row.getSanitizedAnalysisText(), AiAnalysisVO.class);
        } catch (Exception e) {
            return null;
        }
    }

    private VolunteerService.PlanResult requirePlan(Long planId, String accessKey) {
        VolunteerService.PlanResult plan = volunteerService.getPlanResult(planId, accessKey);
        if (plan == null) {
            throw new BizException("方案不存在或访问密钥无效");
        }
        return plan;
    }

    private void save(Long planId, VolunteerService.PlanResult plan, AiAnalysisVO vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            VolunteerAiAnalysis row = analysisMapper.selectOne(new LambdaQueryWrapper<VolunteerAiAnalysis>()
                    .eq(VolunteerAiAnalysis::getPlanId, planId)
                    .last("LIMIT 1"));
            if (row == null) {
                row = new VolunteerAiAnalysis();
                row.setPlanId(planId);
                row.setUserId(0L);
                row.setCreatedAt(LocalDateTime.now());
            }
            row.setAnalysisStatus("completed");
            row.setConclusionText(vo.getConclusion());
            row.setGradientSummaryJson(objectMapper.writeValueAsString(vo.getGradientSummary()));
            row.setKeyKeepItemsJson(objectMapper.writeValueAsString(vo.getBestKeepItem()));
            row.setHighRiskItemsJson(objectMapper.writeValueAsString(vo.getHighestRiskItem()));
            row.setDiagnosisText(objectMapper.writeValueAsString(vo.getDiagnosisSections()));
            row.setActionStepsJson(objectMapper.writeValueAsString(vo.getActionSteps()));
            row.setSanitizedAnalysisText(json);
            row.setSensitiveWordsJson("[]");
            row.setComplianceStatus("pass");
            row.setUpdatedAt(LocalDateTime.now());
            if (row.getId() == null) analysisMapper.insert(row); else analysisMapper.updateById(row);
            planHistoryMapper.update(null, new LambdaUpdateWrapper<PlanHistory>()
                    .eq(PlanHistory::getId, planId)
                    .set(PlanHistory::getAiAnalysis, json));
        } catch (Exception ignored) {
        }
    }

    private int count(List<VolunteerService.VolunteerItem> items, String gradient) {
        return (int) items.stream().filter(item -> gradient.equals(item.getGradient())).count();
    }

    private Map<String, Object> section(String title, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title);
        map.put("content", complianceTextGuard.sanitizeText("ai_analysis_section", title, content));
        return map;
    }

    private Map<String, Object> toItemMap(VolunteerService.VolunteerItem item) {
        if (item == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", item.getIndex());
        map.put("gradient", item.getGradient());
        map.put("universityName", item.getUniversityName());
        map.put("majorName", item.getMajorName());
        map.put("chanceScore", item.getChanceScore());
        map.put("riskLevel", item.getRiskLevel());
        map.put("dataConfidence", item.getDataConfidence());
        map.put("reason", item.getRecommendReason());
        return map;
    }

    @Data
    public static class AiAnalysisVO {
        private Long planId;
        private String status;
        private int progress;
        private String conclusion;
        private Map<String, Integer> gradientSummary;
        private Map<String, Object> bestKeepItem;
        private Map<String, Object> highestRiskItem;
        private List<Map<String, Object>> diagnosisSections;
        private List<String> topKeepDirections;
        private List<String> topRiskPoints;
        private List<Map<String, Object>> actionSteps;
        private List<String> reorderAdvice;
        private String disclaimer;
        /** 真实使用的 AI 模型名（如 "gpt-4o-mini"），fallback 时为 "rule-template-v1"。 */
        private String aiModelVersion;
        /** 是否走规则模板兜底（true=未真正调用 AI 或 AI 异常）。 */
        private Boolean aiFallbackUsed;
        /** 兜底原因（仅 fallback 时填，方便诊断）。 */
        private String aiFallbackReason;
        /** 数据质量问题清单：低参考度志愿、缺失字段等，让前端高亮提示。 */
        private List<String> dataIssues;
    }
}
