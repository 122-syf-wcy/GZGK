package com.gzly.service;

import com.gzly.service.recommend.QueryOnlyRecommendEngine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 湖北 PRE 阶段批次规则。
 *
 * <p>湖北仍走 PROFESSIONAL_GROUP_45 主链路，不能复用 SC_* 批次码给前端。
 * 这里仅声明 HB_* 批次矩阵和主批映射，不改变推荐算法和数据来源。</p>
 */
public final class HubeiBatchRuleRegistry {

    public static final String DEFAULT_BATCH_CODE = "HB_BENKE";
    public static final String OFFICIAL_SOURCE_TITLE = "湖北省教育考试院 2026 招生政策（待发布）";
    public static final String OFFICIAL_SOURCE_URL = "https://www.hbea.edu.cn/";
    public static final String OFFICIAL_SOURCE_TEXT = "湖北 2026 官方招生计划、院校专业组目录、投档线和选科要求待发布；PRE 版仅提供历史估算和策略参考。";

    private static final Map<String, BatchRule> RULES = buildRules();
    private static final Map<String, String> ALIASES = buildAliases(RULES);

    private HubeiBatchRuleRegistry() {
    }

    public static Optional<BatchRule> find(String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            return Optional.ofNullable(RULES.get(DEFAULT_BATCH_CODE));
        }
        String key = normalizeText(batchCode).toLowerCase(Locale.ROOT);
        String resolved = ALIASES.getOrDefault(key, batchCode.trim().toUpperCase(Locale.ROOT));
        return Optional.ofNullable(RULES.get(resolved));
    }

    public static BatchRule require(String batchCode) {
        return find(batchCode).orElseThrow(() -> new IllegalArgumentException("Unsupported HB batch: " + batchCode));
    }

    public static List<BatchRule> allRules() {
        return List.copyOf(RULES.values());
    }

    public static String normalizeBatchCode(String batchCode) {
        return find(batchCode).map(BatchRule::batchCode)
                .orElse(batchCode == null || batchCode.isBlank()
                        ? DEFAULT_BATCH_CODE
                        : batchCode.trim().toUpperCase(Locale.ROOT));
    }

    public static String normalizeCandidateType(String candidateType) {
        return BatchRuleRegistry.normalizeCandidateType(candidateType);
    }

    public static boolean candidateTypeMatches(String expected, String actual) {
        return BatchRuleRegistry.candidateTypeMatches(expected, actual);
    }

    private static Map<String, BatchRule> buildRules() {
        Map<String, BatchRule> rules = new LinkedHashMap<>();
        put(rules, rule("HB_BENKE", "本科普通批", "普通类", CandidateCategory.ORDINARY,
                RecommendMode.PARALLEL_GROUP, BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name(),
                "HubeiProfessionalGroup45Engine", 45, true,
                "院校专业组（平行志愿）",
                "湖北本科普通批按院校专业组 45 个平行志愿展示历史估算；2026 官方数据待发布，结果仅供参考。",
                List.of("本科普通批", "普通本科批", "本科批", "HB本科")));
        put(rules, rule("HB_ZHUANKE", "高职高专普通批", "普通类", CandidateCategory.ORDINARY,
                RecommendMode.QUERY_ONLY, BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(),
                QueryOnlyRecommendEngine.NAME, 45, true,
                "院校专业组（策略建议）",
                "湖北高职高专普通批 2026 官方计划和历史窗口仍待补齐，当前只展示策略建议和数据缺口。",
                List.of("高职高专普通批", "专科普通批", "高职专科批")));
        put(rules, rule("HB_EARLY", "普通类提前批", "普通类", CandidateCategory.EARLY,
                RecommendMode.QUERY_ONLY, BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(),
                QueryOnlyRecommendEngine.NAME, 20, true,
                "院校专业组（策略建议）",
                "湖北提前批涉及资格、顺序或单独计划，当前仅展示政策说明和人工复核提示。",
                List.of("提前批", "本科提前批", "普通类提前批")));
        put(rules, rule("HB_SPECIAL", "专项计划", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                RecommendMode.QUERY_ONLY, BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(),
                QueryOnlyRecommendEngine.NAME, 20, true,
                "专项计划（策略建议）",
                "湖北专项计划需户籍、学籍、资格审核与单独计划，当前只展示策略建议。",
                List.of("专项计划", "国家专项", "高校专项", "地方专项")));
        put(rules, rule("HB_ART", "艺术类批次", "艺术类", CandidateCategory.ART,
                RecommendMode.QUERY_ONLY, BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(),
                QueryOnlyRecommendEngine.NAME, 45, false,
                "院校专业组（艺术类策略建议）",
                "湖北艺术类需统考成绩、综合分规则和院校章程复核，不能套普通位次模型。",
                List.of("艺术类", "艺术本科批", "艺术类批次")));
        put(rules, rule("HB_SPORTS", "体育类批次", "体育类", CandidateCategory.SPORTS,
                RecommendMode.QUERY_ONLY, BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(),
                QueryOnlyRecommendEngine.NAME, 45, false,
                "院校专业组（体育类策略建议）",
                "湖北体育类需体育专业成绩、综合分或排序规则复核，不能套普通位次模型。",
                List.of("体育类", "体育本科批", "体育类批次")));
        return Collections.unmodifiableMap(rules);
    }

    private static void put(Map<String, BatchRule> rules, BatchRule rule) {
        rules.put(rule.batchCode(), rule);
    }

    private static Map<String, String> buildAliases(Map<String, BatchRule> rules) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (BatchRule rule : rules.values()) {
            aliases.put(normalizeText(rule.batchCode()).toLowerCase(Locale.ROOT), rule.batchCode());
            aliases.put(normalizeText(rule.batchName()).toLowerCase(Locale.ROOT), rule.batchCode());
            for (String alias : rule.aliases()) {
                aliases.put(normalizeText(alias).toLowerCase(Locale.ROOT), rule.batchCode());
            }
        }
        return Collections.unmodifiableMap(aliases);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replace("（", "(").replace("）", ")").replace(" ", "");
    }

    private static BatchRule rule(String batchCode,
                                  String batchName,
                                  String candidateType,
                                  CandidateCategory category,
                                  RecommendMode recommendMode,
                                  String supportLevel,
                                  String engine,
                                  int targetCount,
                                  boolean hasAdjustment,
                                  String volunteerMode,
                                  String supportNote,
                                  List<String> aliases) {
        return new BatchRule(batchCode, batchName, candidateType, category, recommendMode, supportLevel,
                engine, targetCount, 6, hasAdjustment, volunteerMode, supportNote, aliases);
    }

    public enum CandidateCategory {
        ORDINARY, EARLY, SPECIAL_PROGRAM, ART, SPORTS
    }

    public enum RecommendMode {
        PARALLEL_GROUP, QUERY_ONLY
    }

    public record BatchRule(
            String batchCode,
            String batchName,
            String candidateType,
            CandidateCategory category,
            RecommendMode recommendMode,
            String supportLevel,
            String engine,
            int targetCount,
            int majorsPerGroup,
            boolean hasAdjustment,
            String volunteerMode,
            String supportNote,
            List<String> aliases
    ) {
        public BatchRule {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        public boolean mainRankEngine() {
            return CandidateCategory.ORDINARY.equals(category)
                    && RecommendMode.PARALLEL_GROUP.equals(recommendMode)
                    && BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel);
        }
    }
}
