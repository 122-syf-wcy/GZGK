package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.recommend.RecommendEngineDecision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 非"普通本科批/普通高职专科批/本科提前批 C 段"之外的 13 个批次的列表查询服务。
 *
 * <p>这些批次按贵州省研究报告与招生工作规定，不能套用普通类位次推荐模型：</p>
 * <ul>
 *   <li>本科提前批 A/B 段、高职专科提前批：1 个院校顺序志愿（SequentialCollegeEngine）。</li>
 *   <li>艺术 / 体育本科批 + 高职专科批：60 个专业（类）平行志愿，按艺术综合分 / 体育综合分录取
 *       （ArtCompositeRecommendEngine / SportsCompositeRecommendEngine）。</li>
 *   <li>国家专项 / 地方专项 / 高校专项 / 民族班 / 预科 / 定向 / 免费医学 / 优师专项 8 项特殊计划：
 *       仅资格 + 计划查询，不出概率（SpecialPlanEligibilityEngine）。</li>
 * </ul>
 *
 * <p>本服务对每个批次返回 items：基于 data_admission_plan_gz + data_major_score_gz 已导入的官方
 * 公开数据生成"可查询院校 / 专业列表"，并显式标注"仅参考、非录取承诺"。当目标年份数据缺失时，
 * items 为空并通过 dataQualityWarning 暴露具体缺口。</p>
 */
@Service
public class BatchListingRecommendationService {

    private static final int DEFAULT_LIMIT = 60;

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;

    public BatchListingRecommendationService(JdbcTemplate jdbcTemplate,
                                             AdmissionYearService admissionYearService) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionYearService = admissionYearService;
    }

    /**
     * 为非主链路批次构造 items 列表。命中数据时返回结构化候选；缺数据时返回空列表 + 警告。
     *
     * @param request    用户提交的生成请求（已经被 controller 注入 batchCode / candidateType / 安全码）
     * @param policy     批次政策配置（用于年化政策口径）
     * @param decision   引擎决策（含 BatchRule 元数据）
     * @return 直接挂到 PlanResult.items 的列表条目
     */
    public BatchListingResult listForBatch(VolunteerService.GenerateRequest request,
                                           PolicyRuleConfig policy,
                                           RecommendEngineDecision decision) {
        if (request == null || decision == null || decision.getRule() == null) {
            return BatchListingResult.empty("批次政策上下文缺失，无法返回可查询列表");
        }
        BatchRuleRegistry.BatchRule rule = decision.getRule();
        Integer year = request.getYear() == null ? admissionYearService.getActiveAdmissionYear() : request.getYear();
        if (year == null || year <= 0) {
            year = admissionYearService.getActiveAdmissionYear();
        }
        try {
            return switch (rule.category()) {
                case EARLY -> listEarlyBatch(rule, year, request);
                case ART -> listCompositeBatch(rule, year, request, "艺术综合分");
                case SPORTS -> listCompositeBatch(rule, year, request, "体育综合分");
                case SPECIAL_PROGRAM -> listSpecialProgramBatch(rule, year, request);
                case ORDINARY -> listOrdinaryBatchPreOfficial(rule, year, request);
                default -> BatchListingResult.empty(rule.supportNote());
            };
        } catch (Exception ex) {
            return BatchListingResult.empty("批次查询服务暂不可用：" + ex.getMessage());
        }
    }

    /**
     * 普通批次（NORMAL_UNDERGRADUATE / NORMAL_SPECIALTY）在 PRE_OFFICIAL_DATA 阶段或 ML 模型未就绪时，
     * 走主链路会被门禁拦截到 QUERY_ONLY。此时仍可基于最近一年的官方招生计划行向用户提供
     * "本批次去年招生过的院校 / 专业"参考列表，但显式打上"历史口径，仅供参考"标签。
     */
    private BatchListingResult listOrdinaryBatchPreOfficial(BatchRuleRegistry.BatchRule rule, int year,
                                                            VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        Integer effectiveYear = year;
        List<Map<String, Object>> rows = queryEarlyPlanRows(rule, year, subjectType);
        if (rows.isEmpty()) {
            for (int fallback : new int[]{2025, 2024, 2023}) {
                if (fallback == year) continue;
                rows = queryEarlyPlanRows(rule, fallback, subjectType);
                if (!rows.isEmpty()) {
                    effectiveYear = fallback;
                    break;
                }
            }
        }
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + "年" + rule.batchName() + "尚未导入" + subjectType
                    + "招生计划，本系统仅提示批次规则与数据缺口。");
        }
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        int index = 1;
        boolean fallback = !effectiveYear.equals(year);
        for (Map<String, Object> row : rows) {
            VolunteerService.VolunteerItem item = buildBaseItem(rule, index++, row, effectiveYear);
            String fallbackSuffix = fallback
                    ? String.format("（参考 %d 年口径；%d 年官方数据公布后会自动切换到完整推荐）", effectiveYear, year)
                    : "";
            item.setRecommendReason(String.format(
                    "梯度定位：%s 候选位 | 适配理由：%d 年%s贵州物理类官方招生计划已导入%s | 风险信号：官方 %d 年数据正在准备中，列表仅供参考 | 可调节项：%d 年官方数据公布并完成训练后会切换到完整推荐",
                    rule.batchName(), effectiveYear, rule.batchName(), fallbackSuffix, year, year));
            item.setRiskReason("官方数据未就绪前，仅展示历史招生计划，不构成任何录取承诺。");
            item.setSuitableFor("符合" + rule.batchName() + "报考条件的考生（最终请按贵州省招生考试院与高校招生章程为准）");
            items.add(item);
        }
        String message = String.format(
                "%d 年%s官方数据尚未发布，已基于 %d 年招生计划返回 %d 条参考列表。",
                year, rule.batchName(), effectiveYear, items.size());
        return new BatchListingResult(items, message, buildWarnings(rule));
    }

    private BatchListingResult listEarlyBatch(BatchRuleRegistry.BatchRule rule, int year,
                                              VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        // 优先目标年份；目标年没数据时回退最近一年（2025 → 2024 → 2023）。
        Integer effectiveYear = year;
        List<Map<String, Object>> rows = queryEarlyPlanRows(rule, year, subjectType);
        if (rows.isEmpty()) {
            for (int fallback : new int[]{2025, 2024, 2023}) {
                if (fallback == year) continue;
                rows = queryEarlyPlanRows(rule, fallback, subjectType);
                if (!rows.isEmpty()) {
                    effectiveYear = fallback;
                    break;
                }
            }
        }
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + "年" + rule.batchName() + "尚未导入" + subjectType
                    + "招生计划，仅展示批次规则。");
        }
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        int index = 1;
        boolean fallback = !effectiveYear.equals(year);
        for (Map<String, Object> row : rows) {
            VolunteerService.VolunteerItem item = buildBaseItem(rule, index++, row, effectiveYear);
            String fallbackSuffix = fallback ? String.format("（参考 %d 年口径，待 %d 年官方计划公布后更新）", effectiveYear, year) : "";
            item.setRecommendReason(String.format(
                    "梯度定位：%s 候选位 | 适配理由：%d 年贵州官方招生计划已导入%s | 风险信号：%s仅展示官方计划行，需自行核对资格、签约与履约要求 | 可调节项：建议结合招生章程与高校招办进一步咨询",
                    rule.batchName(), effectiveYear, fallbackSuffix, rule.batchName()));
            item.setRiskReason(rule.supportNote());
            item.setSuitableFor("满足" + rule.batchName() + "资格要求的考生");
            items.add(item);
        }
        String message = String.format(
                "已检索 %d 条 %d 年%s%s招生计划%s，仅参考；具体录取以学校招生章程与考试院公告为准。",
                items.size(), effectiveYear, rule.batchName(), subjectType,
                fallback ? "（回退口径）" : "");
        return new BatchListingResult(items, message, buildWarnings(rule));
    }

    private List<Map<String, Object>> queryEarlyPlanRows(BatchRuleRegistry.BatchRule rule,
                                                         int year, String subjectType) {
        return jdbcTemplate.queryForList(
                "SELECT school_name, school_code, school_id, major_name, major_code, plan_count, "
                        + "tuition, duration, campus, remarks, special_limit "
                        + "FROM data_admission_plan_gz "
                        + "WHERE year = ? AND batch_code = ? AND subject_type = ? "
                        + "ORDER BY school_name, major_name LIMIT ?",
                year, rule.batchCode(), subjectType, DEFAULT_LIMIT);
    }

    private BatchListingResult listCompositeBatch(BatchRuleRegistry.BatchRule rule, int year,
                                                  VolunteerService.GenerateRequest request,
                                                  String compositeLabel) {
        String subjectKeyword = rule.category() == BatchRuleRegistry.CandidateCategory.ART ? "艺术" : "体育";
        String subjectFilter = subjectKeyword + "%";
        // data_major_score_gz 历史录取按 subject_type 模糊匹配（"艺术类"/"艺术类（物理）"/"艺术文" 等）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT university_name, school_id, major_name, min_score, min_rank, batch, year, "
                        + "subject_type, plan_count "
                        + "FROM data_major_score_gz "
                        + "WHERE year >= ? AND batch LIKE ? AND subject_type LIKE ? "
                        + "ORDER BY year DESC, university_name, major_name LIMIT ?",
                Math.max(2021, year - 3), "%" + subjectKeyword + "%", subjectFilter, DEFAULT_LIMIT);
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + "年" + rule.batchName()
                    + "暂未检索到历史录取与" + compositeLabel + "数据，仅展示批次规则。");
        }
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> row : rows) {
            VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
            item.setIndex(index++);
            item.setUniversityName(stringValue(row, "university_name"));
            item.setSchoolId(stringValue(row, "school_id"));
            item.setMajorName(stringValue(row, "major_name"));
            item.setProvince("贵州");
            item.setGradient("查询参考");
            int rowYear = intValueOrDefault(row, "year", year);
            item.setReferenceYear(rowYear);
            item.setHistoryMinRank(intValueOrDefault(row, "min_rank", 0));
            item.setLatestPlanCount(intValue(row, "plan_count"));
            item.setRecommendReason(String.format(
                    "梯度定位：%s 候选位 | 适配理由：%d 年%s招生历史数据已导入 | 风险信号：%s按%s录取，本系统不套用普通位次模型，请按高校公布的%s公式自行折算 | 可调节项：可在高校招生章程内查找具体折算公式与文化、专业最低控制线",
                    rule.batchName(), rowYear, rule.batchName(), rule.batchName(),
                    compositeLabel, compositeLabel));
            item.setRiskReason(rule.supportNote());
            item.setSuitableFor("已取得" + subjectKeyword + "类专业合格成绩、达到文化与专业最低控制线的考生");
            item.setDataSourceType("历史录取");
            items.add(item);
        }
        return new BatchListingResult(items, String.format(
                "已检索 %d 条%s历史录取参考；最终录取按高校招生章程公布的%s公式与最低控制线执行。",
                items.size(), rule.batchName(), compositeLabel), buildWarnings(rule));
    }

    private BatchListingResult listSpecialProgramBatch(BatchRuleRegistry.BatchRule rule, int year,
                                                       VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        String[] keywordsByBatch = keywordsFor(rule.batchCode());
        if (keywordsByBatch.length == 0) {
            return BatchListingResult.empty(rule.batchName() + "需结合资格审查表与高校招生章程使用，暂不返回可查询列表。");
        }
        // 1) 首选当前年份的 admission_plan_gz remarks/special_limit/major_name 关键词匹配
        List<Map<String, Object>> rows = querySpecialProgramRows(year, subjectType, keywordsByBatch);
        int effectiveYear = year;
        String source = "admission_plan_remarks";
        if (rows.isEmpty()) {
            // 2) 回退最近一年的 admission_plan 关键词匹配
            for (int fallback : new int[]{2025, 2024, 2023}) {
                if (fallback == year) continue;
                rows = querySpecialProgramRows(fallback, subjectType, keywordsByBatch);
                if (!rows.isEmpty()) {
                    effectiveYear = fallback;
                    break;
                }
            }
        }
        if (rows.isEmpty()) {
            // 3) 兜底：从 data_major_score_gz 历史专项批次（2021-2025 "国家专项计划本科批" / "地方专项计划本科批" /
            // "高校专项计划本科批" 等）拉院校 + 专业列表。注：2024+ 新高考统一 subject_type 为物理类/历史类，
            // 2021-2023 旧高考是 文科/理科，按用户首选科目兼容映射。
            String[] legacyBatchPatterns = legacyBatchPatternsFor(rule.batchCode());
            if (legacyBatchPatterns.length > 0) {
                rows = queryLegacySpecialProgramRows(legacyBatchPatterns, request);
                if (!rows.isEmpty()) {
                    source = "historical_score_line";
                    effectiveYear = pickLatestYear(rows);
                }
            }
        }
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + "年" + rule.batchName()
                    + "暂未匹配到官方招生计划行，请按高校招生章程与" + rule.batchName() + "资格审查表手动核对。");
        }
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        int index = 1;
        boolean fallback = effectiveYear != year;
        for (Map<String, Object> row : rows) {
            VolunteerService.VolunteerItem item;
            if ("historical_score_line".equals(source)) {
                item = buildItemFromMajorScore(rule, index++, row, effectiveYear);
            } else {
                item = buildBaseItem(rule, index++, row, effectiveYear);
            }
            String fallbackSuffix = fallback ? String.format("（参考 %d 年口径，待 %d 年官方计划公布后更新）", effectiveYear, year) : "";
            String sourceNote = "historical_score_line".equals(source) ? "（来源：贵州历史专项录取数据 data_major_score_gz）" : "";
            item.setRecommendReason(String.format(
                    "梯度定位：%s 候选位 | 适配理由：%d 年%s%s%s | 风险信号：%s须先通过资格审查与名单公示，本列表仅参考 | 可调节项：请按高校招生章程与%s资格审查表手动核对",
                    rule.batchName(), effectiveYear, rule.batchName(), fallbackSuffix, sourceNote, rule.batchName(), rule.batchName()));
            item.setSuitableFor("通过" + rule.batchName() + "资格审查与名单公示的考生");
            items.add(item);
        }
        String message = String.format(
                "已为%s返回 %d 条 %d 年%s参考列表%s，仅供资格审查通过的考生参考。",
                rule.batchName(), items.size(), effectiveYear,
                "historical_score_line".equals(source) ? "历史录取" : "招生计划",
                fallback ? "（回退口径）" : "");
        return new BatchListingResult(items, message, buildWarnings(rule));
    }

    private List<Map<String, Object>> queryLegacySpecialProgramRows(String[] batchPatterns,
                                                                    VolunteerService.GenerateRequest request) {
        if (batchPatterns.length == 0) return List.of();
        StringBuilder sql = new StringBuilder(
                "SELECT university_name AS school_name, NULL AS school_code, school_id, "
                        + "major_name, NULL AS major_code, plan_count, NULL AS tuition, NULL AS duration, "
                        + "NULL AS campus, NULL AS remarks, NULL AS special_limit, year, batch, min_rank "
                        + "FROM data_major_score_gz WHERE (");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < batchPatterns.length; i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("batch LIKE ?");
            params.add("%" + batchPatterns[i] + "%");
        }
        sql.append(") AND year >= 2021 ORDER BY year DESC, university_name LIMIT ?");
        params.add(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private int pickLatestYear(List<Map<String, Object>> rows) {
        int max = 0;
        for (Map<String, Object> row : rows) {
            Integer y = intValue(row, "year");
            if (y != null && y > max) max = y;
        }
        return max > 0 ? max : 2024;
    }

    private VolunteerService.VolunteerItem buildItemFromMajorScore(BatchRuleRegistry.BatchRule rule, int index,
                                                                   Map<String, Object> row, int year) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setIndex(index);
        item.setUniversityName(stringValue(row, "school_name"));
        item.setSchoolId(stringValue(row, "school_id"));
        item.setMajorName(stringValue(row, "major_name"));
        item.setLatestPlanCount(intValue(row, "plan_count"));
        item.setReferenceYear(intValueOrDefault(row, "year", year));
        item.setHistoryMinRank(intValueOrDefault(row, "min_rank", 0));
        item.setProvince("贵州");
        item.setGradient("查询参考");
        item.setDataSourceType("历史专项录取");
        item.setRangeNote("批次：" + stringValue(row, "batch"));
        return item;
    }

    private List<Map<String, Object>> querySpecialProgramRows(int year, String subjectType, String[] keywords) {
        StringBuilder sql = new StringBuilder(
                "SELECT school_name, school_code, school_id, major_name, major_code, plan_count, remarks, special_limit "
                        + "FROM data_admission_plan_gz WHERE year = ? AND subject_type = ? AND (");
        List<Object> params = new ArrayList<>();
        params.add(year);
        params.add(subjectType);
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("remarks LIKE ? OR special_limit LIKE ? OR major_name LIKE ?");
            String like = "%" + keywords[i] + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(") ORDER BY school_name, major_name LIMIT ?");
        params.add(DEFAULT_LIMIT);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private VolunteerService.VolunteerItem buildBaseItem(BatchRuleRegistry.BatchRule rule, int index,
                                                         Map<String, Object> row) {
        return buildBaseItem(rule, index, row, null);
    }

    private VolunteerService.VolunteerItem buildBaseItem(BatchRuleRegistry.BatchRule rule, int index,
                                                         Map<String, Object> row, Integer year) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setIndex(index);
        item.setUniversityName(stringValue(row, "school_name"));
        item.setSchoolId(stringValue(row, "school_id"));
        item.setMajorName(stringValue(row, "major_name"));
        item.setLatestPlanCount(intValue(row, "plan_count"));
        item.setProvince("贵州");
        item.setGradient("查询参考");
        item.setDataSourceType("官方招生计划");
        if (year != null) {
            item.setReferenceYear(year);
        }
        if (row.containsKey("tuition")) {
            String tuition = stringValue(row, "tuition");
            if (!tuition.isBlank()) {
                item.setRangeNote("学费：" + tuition);
            }
        }
        return item;
    }

    private String[] keywordsFor(String batchCode) {
        return switch (batchCode) {
            case "NATIONAL_SPECIAL" -> new String[]{"国家专项"};
            case "LOCAL_SPECIAL" -> new String[]{"地方专项"};
            case "UNIVERSITY_SPECIAL" -> new String[]{"高校专项"};
            case "ETHNIC_CLASS" -> new String[]{"民族班", "民族预科"};
            case "PREPARATORY" -> new String[]{"预科"};
            case "ORIENTED" -> new String[]{"定向"};
            case "FREE_MEDICAL" -> new String[]{"免费医学", "农村订单定向医学", "免费医学定向"};
            case "TEACHER_EXCELLENCE" -> new String[]{"优师", "公费师范", "免费师范", "公费教育"};
            default -> new String[0];
        };
    }

    /**
     * 旧高考年份（2021-2023）"国家专项计划本科批" / "地方专项计划本科批" 等独立批次
     * 在 data_major_score_gz 里的 batch 字段模糊匹配 pattern，用于 2024+ 新高考无独立
     * 专项数据时的兜底列表查询。
     */
    private String[] legacyBatchPatternsFor(String batchCode) {
        return switch (batchCode) {
            case "NATIONAL_SPECIAL" -> new String[]{"国家专项"};
            case "LOCAL_SPECIAL" -> new String[]{"地方专项"};
            case "UNIVERSITY_SPECIAL" -> new String[]{"高校专项"};
            case "FREE_MEDICAL" -> new String[]{"免费医学", "农村订单"};
            case "TEACHER_EXCELLENCE" -> new String[]{"公费师范", "优师"};
            case "ORIENTED" -> new String[]{"定向"};
            case "ETHNIC_CLASS", "PREPARATORY" -> new String[0];
            default -> new String[0];
        };
    }

    private String normalizeSubjectType(VolunteerService.GenerateRequest req) {
        if (req == null || req.getFirstSubject() == null) {
            return "物理类";
        }
        return "历史".equals(req.getFirstSubject().trim()) ? "历史类" : "物理类";
    }

    private List<String> buildWarnings(BatchRuleRegistry.BatchRule rule) {
        List<String> warnings = new ArrayList<>();
        warnings.add(rule.supportNote());
        warnings.add("本批次仅展示官方公开数据，不构成任何录取承诺。");
        return warnings;
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return "";
        return value.toString().trim();
    }

    private Integer intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int intValueOrDefault(Map<String, Object> row, String key, int defaultValue) {
        Integer value = intValue(row, key);
        return value == null ? defaultValue : value;
    }

    /** 批次列表查询结果。items 可空；message 用于 dataQualityWarning；warnings 拼到 plan.warnings。 */
    public record BatchListingResult(List<VolunteerService.VolunteerItem> items,
                                     String message,
                                     List<String> warnings) {
        public static BatchListingResult empty(String message) {
            return new BatchListingResult(List.of(), message, List.of());
        }

        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }

        @SuppressWarnings("unused")
        public String summary() {
            return String.format(Locale.ROOT, "items=%d message=%s", items == null ? 0 : items.size(), message);
        }
    }
}
