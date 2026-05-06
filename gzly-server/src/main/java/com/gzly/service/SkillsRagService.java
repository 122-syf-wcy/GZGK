package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.common.ComplianceConstants;
import com.gzly.common.exception.BizException;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.SkillsChunk;
import com.gzly.entity.SkillsQueryLog;
import com.gzly.mapper.SkillsChunkMapper;
import com.gzly.mapper.SkillsQueryLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SkillsRagService {

    private final VolunteerService volunteerService;
    private final SkillsChunkMapper chunkMapper;
    private final SkillsQueryLogMapper queryLogMapper;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final ComplianceTextGuard complianceTextGuard;

    public SkillsAnswer ask(Long planId, String accessKey, String question, String aiReport,
                            List<AiService.AdvisorSkillChatMessage> history) {
        if (planId == null || accessKey == null || accessKey.isBlank()) {
            throw new BizException("方案参数不能为空");
        }
        if (question == null || question.trim().length() < 2) {
            throw new BizException("请输入要咨询的问题");
        }
        VolunteerService.PlanResult plan = volunteerService.getPlanResult(planId, accessKey);
        if (plan == null) {
            throw new BizException("方案不存在或访问密钥无效");
        }
        PlanHistory rawPlan = volunteerService.getPlanById(planId);
        List<SourceChunk> chunks = retrieve(question);
        String rawAnswer;
        if (chunks.isEmpty()) {
            rawAnswer = "当前策略库没有足够依据。可以先基于当前志愿清单做三件事：优先复核高风险项，保留数据参考度较高的稳妥参考项，再结合招生章程核对专业限制。";
        } else {
            String digest = buildDigest(chunks);
            rawAnswer = aiService.chatWithAdvisorSkill(
                    digest,
                    buildPlanContext(plan),
                    aiReport == null || aiReport.isBlank() ? (rawPlan == null ? "" : rawPlan.getAiAnalysis()) : aiReport,
                    question,
                    history);
        }
        String sanitized = complianceTextGuard.sanitizeText("skills_qa", String.valueOf(planId), rawAnswer);
        saveLog(planId, question, chunks, rawAnswer, sanitized);

        SkillsAnswer answer = new SkillsAnswer();
        answer.setAnswer(sanitized);
        answer.setActionItems(defaultActionItems(plan));
        answer.setReferencedVolunteers(referencedVolunteers(plan, question));
        answer.setSourceChunks(chunks);
        answer.setRiskWarnings(defaultRiskWarnings(plan));
        answer.setDisclaimer("该服务基于公开 skills 策略库和当前志愿方案上下文生成，仅供参考，不代表张雪峰本人或任何机构意见，不构成录取承诺。");
        return answer;
    }

    public List<String> suggestedQuestions() {
        return List.of(
                "按就业优先，帮我筛掉最不值得保留的冲档项",
                "这些志愿里哪些更值得保专业，哪些更值得保学校",
                "结合扩招指数和院校招生指数，重排前15个志愿",
                "哪些专业需要避坑？",
                "如果想考研，哪些专业更适合？",
                "如果想进体制，哪些专业更合适？",
                "学校优先和专业优先怎么取舍？");
    }

    private List<SourceChunk> retrieve(String question) {
        Map<Long, ScoredChunk> scored = new LinkedHashMap<>();
        try {
            List<String> keywords = keywords(question);
            for (String keyword : keywords) {
                List<SkillsChunk> rows = chunkMapper.selectList(new LambdaQueryWrapper<SkillsChunk>()
                        .like(SkillsChunk::getChunkText, keyword)
                        .or()
                        .like(SkillsChunk::getTagsJson, keyword)
                        .orderByAsc(SkillsChunk::getId)
                        .last("LIMIT 12"));
                for (SkillsChunk row : rows) {
                    addScore(scored, row, score(row, keywords, question));
                }
            }
            if (scored.isEmpty()) {
                List<SkillsChunk> rows = chunkMapper.selectList(new LambdaQueryWrapper<SkillsChunk>()
                        .orderByDesc(SkillsChunk::getId)
                        .last("LIMIT 60"));
                for (SkillsChunk row : rows) {
                    addScore(scored, row, vectorScore(row, question));
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return scored.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(8)
                .map(ScoredChunk::getChunk)
                .toList();
    }

    private List<String> keywords(String question) {
        List<String> words = new ArrayList<>();
        String value = question == null ? "" : question;
        for (String word : List.of("就业", "考研", "考公", "专业", "学校", "城市", "医学", "计算机", "法学", "财经", "避坑", "民办", "中外合作", "冲", "稳", "保")) {
            if (value.contains(word)) words.add(word);
        }
        if (words.isEmpty()) words.add("专业");
        return words;
    }

    private SourceChunk toSourceChunk(SkillsChunk row) {
        SourceChunk chunk = new SourceChunk();
        chunk.setId(row.getId());
        chunk.setDocumentId(row.getDocumentId());
        chunk.setChunkIndex(row.getChunkIndex());
        chunk.setText(row.getChunkText());
        chunk.setTagsJson(row.getTagsJson());
        return chunk;
    }

    private void addScore(Map<Long, ScoredChunk> scored, SkillsChunk row, double score) {
        if (row == null || row.getId() == null) return;
        ScoredChunk existing = scored.get(row.getId());
        if (existing == null || score > existing.getScore()) {
            scored.put(row.getId(), new ScoredChunk(toSourceChunk(row), score));
        }
    }

    private double score(SkillsChunk row, List<String> keywords, String question) {
        double score = vectorScore(row, question);
        String text = row.getChunkText() == null ? "" : row.getChunkText();
        String tags = row.getTagsJson() == null ? "" : row.getTagsJson();
        for (String keyword : keywords) {
            if (text.contains(keyword)) score += 1.0D;
            if (tags.contains(keyword)) score += 1.5D;
        }
        return score;
    }

    private double vectorScore(SkillsChunk row, String question) {
        try {
            List<Double> a = lightVector(question);
            List<Double> b = objectMapper.readValue(row.getEmbeddingVector() == null ? "[]" : row.getEmbeddingVector(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
            double dot = 0D, na = 0D, nb = 0D;
            int n = Math.min(a.size(), b.size());
            for (int i = 0; i < n; i++) {
                dot += a.get(i) * b.get(i);
                na += a.get(i) * a.get(i);
                nb += b.get(i) * b.get(i);
            }
            if (na == 0 || nb == 0) return 0D;
            return dot / (Math.sqrt(na) * Math.sqrt(nb));
        } catch (Exception e) {
            return 0D;
        }
    }

    private List<Double> lightVector(String text) {
        String value = text == null ? "" : text;
        List<String> anchors = List.of("就业", "考研", "考公", "城市", "学校", "专业", "医学", "计算机",
                "师范", "法学", "财经", "土木", "机械", "材料", "化工", "民办", "中外合作", "冲", "稳", "保");
        List<Double> vector = new ArrayList<>();
        int length = Math.max(1, value.length());
        for (String anchor : anchors) {
            vector.add(count(value, anchor) * 1.0D / length);
        }
        return vector;
    }

    private int count(String text, String keyword) {
        int count = 0;
        int index = 0;
        while (text != null && keyword != null && !keyword.isBlank()
                && (index = text.indexOf(keyword, index)) >= 0) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    private String buildDigest(List<SourceChunk> chunks) {
        StringBuilder builder = new StringBuilder("以下为公开 skills 策略库检索片段，只可作为辅助参考：\n");
        for (int i = 0; i < chunks.size(); i++) {
            builder.append("片段").append(i + 1).append("：")
                    .append(limit(chunks.get(i).getText(), 900))
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String buildPlanContext(VolunteerService.PlanResult plan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("planId", plan.getId());
        root.put("provinceCode", plan.getProvinceCode());
        root.put("targetBatch", plan.getTargetBatch());
        root.put("student", Map.of(
                "totalScore", plan.getTotalScore(),
                "provinceRank", plan.getProvinceRank(),
                "firstSubject", plan.getFirstSubject(),
                "resubjects", plan.getResubjects() == null ? List.of() : plan.getResubjects(),
                "strategyMode", plan.getStrategyMode()));
        root.put("metrics", plan.getMetrics());
        root.put("advisorAdvice", plan.getAdvisorAdvice());
        root.put("manualReviewItems", plan.getManualReviewItems() == null ? List.of() : plan.getManualReviewItems());
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
            map.put("rankDiff", item.getRankDiff());
            map.put("planExpansionIndex", item.getPlanExpansionIndex());
            map.put("schoolEnrollmentIndex", item.getSchoolEnrollmentIndex());
            map.put("recommendReason", item.getRecommendReason());
            map.put("riskReason", item.getRiskReason());
            return map;
        }).toList());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<Map<String, Object>> referencedVolunteers(VolunteerService.PlanResult plan, String question) {
        if (plan.getItems() == null) return List.of();
        List<Map<String, Object>> matched = plan.getItems().stream()
                .filter(item -> itemMatchesQuestion(item, question))
                .limit(15)
                .map(this::volunteerRef)
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        return plan.getItems().stream()
                .limit(15)
                .map(this::volunteerRef)
                .toList();
    }

    private boolean itemMatchesQuestion(VolunteerService.VolunteerItem item, String question) {
        if (question == null || question.isBlank() || question.contains("前15")) return true;
        if (contains(question, item.getGradient())) return true;
        if (contains(question, item.getUniversityName())) return true;
        String major = item.getMajorName() == null ? "" : item.getMajorName();
        for (String keyword : keywords(question)) {
            if (major.contains(keyword)
                    || contains(item.getUniversityName(), keyword)
                    || contains(item.getRecommendReason(), keyword)
                    || contains(item.getRiskReason(), keyword)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> volunteerRef(VolunteerService.VolunteerItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", item.getIndex());
        map.put("gradient", item.getGradient());
        map.put("universityName", item.getUniversityName());
        map.put("majorName", item.getMajorName());
        map.put("chanceScore", item.getChanceScore());
        map.put("riskLevel", item.getRiskLevel());
        return map;
    }

    private List<String> defaultActionItems(VolunteerService.PlanResult plan) {
        return List.of(
                "优先核对高风险项的招生章程、选科限制、学费和校区。",
                "保留数据参考度较高的稳妥参考项作为主锚点。",
                "若兜底参考不足，放宽专业、城市、学校层次或费用限制后重新生成。");
    }

    private List<String> defaultRiskWarnings(VolunteerService.PlanResult plan) {
        List<String> warnings = new ArrayList<>();
        if (plan.getDataQualityWarning() != null && !plan.getDataQualityWarning().isBlank()) {
            warnings.add(plan.getDataQualityWarning());
        }
        warnings.add(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
        return warnings;
    }

    private void saveLog(Long planId, String question, List<SourceChunk> chunks, String rawAnswer, String sanitized) {
        try {
            SkillsQueryLog log = new SkillsQueryLog();
            log.setPlanId(planId);
            log.setUserId(0L);
            log.setQuestion(question);
            log.setRetrievedChunksJson(objectMapper.writeValueAsString(chunks));
            log.setRawAnswer(rawAnswer);
            log.setSanitizedAnswer(sanitized);
            log.setComplianceStatus("pass");
            log.setCreatedAt(LocalDateTime.now());
            queryLogMapper.insert(log);
        } catch (Exception ignored) {
        }
    }

    private String limit(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "\n[片段已截断]";
    }

    private boolean contains(String source, String keyword) {
        return source != null && keyword != null && !keyword.isBlank() && source.contains(keyword);
    }

    @Data
    public static class SkillsAnswer {
        private String answer;
        private List<String> actionItems;
        private List<Map<String, Object>> referencedVolunteers;
        private List<SourceChunk> sourceChunks;
        private List<String> riskWarnings;
        private String disclaimer;
    }

    @Data
    private static class ScoredChunk {
        private final SourceChunk chunk;
        private final double score;
    }

    @Data
    public static class SourceChunk {
        private Long id;
        private Long documentId;
        private Integer chunkIndex;
        private String text;
        private String tagsJson;
    }
}
