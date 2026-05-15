package com.gzly.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BatchRuleRegistry {

    public static final String DEFAULT_BATCH_CODE = "NORMAL_UNDERGRADUATE";

    public enum SupportLevel {
        FULL_RECOMMEND,
        TRIAL_RECOMMEND,
        QUERY_ONLY,
        UNSUPPORTED
    }

    public enum CandidateCategory {
        ORDINARY,
        EARLY,
        ART,
        SPORTS,
        SPECIAL_PROGRAM,
        OTHER
    }

    public enum RecommendMode {
        PARALLEL_MAJOR,
        PARALLEL_MAJOR_60,
        SEQUENTIAL_COLLEGE,
        ART_COMPOSITE,
        SPORTS_COMPOSITE,
        ELIGIBILITY_QUERY,
        QUERY_ONLY
    }

    public record BatchRule(
            String batchCode,
            String batchName,
            String candidateType,
            CandidateCategory category,
            SupportLevel baseSupportLevel,
            RecommendMode recommendMode,
            int targetCount,
            String volunteerMode,
            String engine,
            boolean mainRankEngine,
            List<String> aliases,
            List<String> candidateTypeAliases,
            String supportNote
    ) {
        public BatchRule {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            candidateTypeAliases = candidateTypeAliases == null ? List.of() : List.copyOf(candidateTypeAliases);
        }
    }

    private static final Map<String, BatchRule> RULES = buildRules();
    private static final Map<String, String> ALIASES = buildAliases(RULES.values());
    private static final String OFFICIAL_SOURCE_TITLE = "贵州省2025年普通高校招生工作规定";
    private static final String OFFICIAL_SOURCE_URL = "https://zsksy.guizhou.gov.cn/";
    private static final String OFFICIAL_SOURCE_TEXT = "贵州2025普通类本科提前批A段、B段和高职专科提前批为1个院校顺序志愿，本科提前批C段为60个专业（类）平行志愿，本科批和高职（专科）批为96个专业（类）平行志愿；艺术本科A段为1个院校顺序志愿，艺术本科B段和艺术高职（专科）批为60个专业（类）平行志愿；体育本科批和体育高职（专科）批为60个专业（类）平行志愿。艺术、体育和特殊类型招生须按对应专业成绩、综合分、资格审核和高校章程执行，不套用普通类位次推荐模型。";

    private BatchRuleRegistry() {
    }

    public static List<BatchRule> allRules() {
        return List.copyOf(RULES.values());
    }

    public static String officialSourceTitle() {
        return OFFICIAL_SOURCE_TITLE;
    }

    public static String officialSourceUrl() {
        return OFFICIAL_SOURCE_URL;
    }

    public static String officialSourceText() {
        return OFFICIAL_SOURCE_TEXT;
    }

    public static Optional<BatchRule> find(String batchCode) {
        String normalized = normalizeBatchCode(batchCode);
        return Optional.ofNullable(RULES.get(normalized));
    }

    public static BatchRule require(String batchCode) {
        return find(batchCode).orElseThrow(() -> new IllegalArgumentException("Unsupported batch code: " + batchCode));
    }

    public static String normalizeBatchCode(String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            return DEFAULT_BATCH_CODE;
        }
        String raw = batchCode.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        if (RULES.containsKey(upper)) {
            return upper;
        }
        String key = normalizeText(raw).toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(key, upper.startsWith("NORMAL_") || upper.startsWith("EARLY_") || upper.startsWith("ART_") || upper.startsWith("SPORTS_") || upper.startsWith("SPECIAL_") || upper.endsWith("_SPECIAL") ? upper : raw);
    }

    public static String normalizeCandidateType(String candidateType) {
        if (candidateType == null || candidateType.isBlank()) {
            return "普通类";
        }
        String value = candidateType.trim();
        String normalized = normalizeText(value);
        if (containsAny(normalized, List.of("艺术", "美术", "音乐", "舞蹈", "播音", "戏剧", "影视", "书法"))) {
            return "艺术类";
        }
        if (containsAny(normalized, List.of("体育", "运动训练"))) {
            return "体育类";
        }
        return value;
    }

    public static String normalizeBatchName(String batch) {
        return find(batch).map(BatchRule::batchName).orElseGet(() -> batch == null ? "" : batch.trim());
    }

    public static boolean batchMatches(String requestedBatchCode, String dataBatch) {
        if (requestedBatchCode == null || requestedBatchCode.isBlank() || dataBatch == null || dataBatch.isBlank()) {
            return true;
        }
        BatchRule rule = find(requestedBatchCode).orElse(null);
        if (rule == null) {
            return normalizeText(requestedBatchCode).equals(normalizeText(dataBatch));
        }
        String data = normalizeText(dataBatch).toLowerCase(Locale.ROOT);
        String knownDataCode = ALIASES.get(data);
        if (knownDataCode != null && !knownDataCode.equals(rule.batchCode())) {
            return false;
        }
        if (matchesKnownOtherRule(data, rule.batchCode())) {
            return false;
        }
        if (data.equals(normalizeText(rule.batchCode()).toLowerCase(Locale.ROOT))
                || data.equals(normalizeText(rule.batchName()).toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (String alias : rule.aliases()) {
            String normalizedAlias = normalizeText(alias).toLowerCase(Locale.ROOT);
            if (normalizedAlias.isBlank()) {
                continue;
            }
            if (data.equals(normalizedAlias) || data.contains(normalizedAlias)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMainRankEngine(String batchCode, String candidateType) {
        BatchRule rule = find(batchCode).orElse(null);
        if (rule == null || !rule.mainRankEngine()) {
            return false;
        }
        return "普通类".equals(normalizeCandidateType(candidateType));
    }

    public static boolean allowsRecruitType(String recruitType, String requestBatchCode, String candidateType, String dataBatch) {
        if (recruitType == null || recruitType.isBlank() || "NORMAL".equals(recruitType)) {
            return true;
        }
        BatchRule rule = find(requestBatchCode).orElse(null);
        if (rule == null) {
            return false;
        }
        CandidateCategory category = rule.category();
        String type = normalizeCandidateType(candidateType);
        if (category == CandidateCategory.ART && "艺术类".equals(type)) {
            return "ART_SPORTS".equals(recruitType) || batchMatches(rule.batchCode(), dataBatch);
        }
        if (category == CandidateCategory.SPORTS && "体育类".equals(type)) {
            return "ART_SPORTS".equals(recruitType) || batchMatches(rule.batchCode(), dataBatch);
        }
        if (category == CandidateCategory.EARLY) {
            return "PRE_BATCH".equals(recruitType) || "MILITARY_POLICE".equals(recruitType) || batchMatches(rule.batchCode(), dataBatch);
        }
        if (category == CandidateCategory.SPECIAL_PROGRAM) {
            return "SPECIAL_PROGRAM".equals(recruitType) || "FREE_NORMAL".equals(recruitType) || "DIRECTED".equals(recruitType) || batchMatches(rule.batchCode(), dataBatch);
        }
        return false;
    }

    public static boolean candidateTypeMatches(String ruleCandidateType, String requestedCandidateType) {
        return normalizeCandidateType(ruleCandidateType).equals(normalizeCandidateType(requestedCandidateType));
    }

    public static List<String> aliasesForSql(String batchCode) {
        BatchRule rule = find(batchCode).orElse(null);
        if (rule == null) {
            return List.of(batchCode);
        }
        List<String> aliases = new ArrayList<>();
        aliases.add(rule.batchName());
        aliases.addAll(rule.aliases());
        return aliases.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
    }

    private static boolean matchesKnownOtherRule(String data, String currentBatchCode) {
        if (data == null || data.isBlank()) {
            return false;
        }
        for (BatchRule other : RULES.values()) {
            if (other.batchCode().equals(currentBatchCode)) {
                continue;
            }
            if (dataContains(data, other.batchName())) {
                return true;
            }
            for (String alias : other.aliases()) {
                if (isSpecificAlias(alias) && dataContains(data, alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean dataContains(String data, String value) {
        String normalized = normalizeText(value).toLowerCase(Locale.ROOT);
        return !normalized.isBlank() && (data.equals(normalized) || data.contains(normalized));
    }

    private static boolean isSpecificAlias(String alias) {
        String normalized = normalizeText(alias);
        return normalized.length() >= 4 && containsAny(normalized, List.of("普通", "艺术", "体育", "提前", "专项", "民族", "预科", "定向", "医学", "优师", "师范", "军", "警"));
    }

    private static Map<String, BatchRule> buildRules() {
        Map<String, BatchRule> rules = new LinkedHashMap<>();
        add(rules, new BatchRule("NORMAL_UNDERGRADUATE", "普通本科批", "普通类", CandidateCategory.ORDINARY,
                SupportLevel.FULL_RECOMMEND, RecommendMode.PARALLEL_MAJOR, 96, "专业（类）+ 院校", "OrdinaryParallelMajorEngine", true,
                List.of("ordinary_undergraduate", "normal_undergraduate", "本科批", "普通本科批", "普通类本科批", "本科批次"), List.of("普通类"), "普通类本科批按96个专业（类）+院校推荐"));
        add(rules, new BatchRule("NORMAL_SPECIALTY", "普通类高职专科批", "普通类", CandidateCategory.ORDINARY,
                SupportLevel.FULL_RECOMMEND, RecommendMode.PARALLEL_MAJOR, 96, "专业（类）+ 院校", "OrdinaryParallelMajorEngine", true,
                List.of("ordinary_specialty", "normal_specialty", "NORMAL_SPECIALIST", "normal_specialist", "高职专科批", "普通类高职专科批", "普通专科批", "专科批"), List.of("普通类"), "普通类高职专科批按96个专业（类）+院校推荐，专科梯度窗口采用更宽口径并保留保档自动扩展"));
        add(rules, new BatchRule("EARLY_A_B", "普通类本科提前批A/B段", "普通类", CandidateCategory.EARLY,
                SupportLevel.QUERY_ONLY, RecommendMode.SEQUENTIAL_COLLEGE, 1, "院校顺序志愿", "SequentialCollegeEngine", false,
                List.of("early_a_b", "early_ab", "提前批A/B段", "提前批AB段", "本科提前批A段", "本科提前批B段", "普通类本科提前批A段", "普通类本科提前批B段", "普通类本科提前批A/B段"), List.of("普通类"), "提前A/B段为1个院校顺序志愿，当前仅展示顺序志愿规则、资格条件和数据缺口"));
        add(rules, new BatchRule("EARLY_C", "普通类本科提前批C段", "普通类", CandidateCategory.EARLY,
                SupportLevel.QUERY_ONLY, RecommendMode.PARALLEL_MAJOR_60, 60, "专业（类）平行志愿", "EarlyCParallelMajorEngine", false,
                List.of("early_c", "提前批C段", "本科提前批C段", "普通类本科提前批C段"), List.of("普通类"), "提前C段为60个专业（类）平行志愿，需单独处理公费师范、优师、免费医学、定向和履约风险"));
        add(rules, new BatchRule("SPECIALTY_EARLY", "普通类高职专科提前批", "普通类", CandidateCategory.EARLY,
                SupportLevel.QUERY_ONLY, RecommendMode.SEQUENTIAL_COLLEGE, 1, "院校顺序志愿", "SequentialCollegeEngine", false,
                List.of("specialty_early", "early_specialty", "专科提前批", "高职专科提前批", "普通类专科提前批", "普通类高职专科提前批"), List.of("普通类"), "高职专科提前批为1个院校顺序志愿，需单独计划与资格规则，当前仅返回规则与数据缺口"));
        add(rules, new BatchRule("ART_UNDERGRADUATE_A", "艺术类本科A段", "艺术类", CandidateCategory.ART,
                SupportLevel.QUERY_ONLY, RecommendMode.SEQUENTIAL_COLLEGE, 1, "院校顺序志愿", "ArtCompositeRecommendEngine", false,
                List.of("art_undergraduate_a", "艺术本科A段", "艺术类本科A段", "艺术本科提前A段"), List.of("艺术类", "美术类", "音乐类", "舞蹈类", "播音类"), "艺术本科A段为1个院校顺序志愿，需艺术专业成绩、文化成绩、综合分规则和院校章程共同复核"));
        add(rules, new BatchRule("ART_UNDERGRADUATE_B", "艺术类本科B段", "艺术类", CandidateCategory.ART,
                SupportLevel.QUERY_ONLY, RecommendMode.ART_COMPOSITE, 60, "专业（类）平行志愿", "ArtCompositeRecommendEngine", false,
                List.of("art_undergraduate", "art_undergraduate_b", "ART_UNDERGRADUATE", "艺术本科批", "艺术类本科批", "艺术类本科", "艺术本科B段", "艺术类本科B段"), List.of("艺术类", "美术类", "音乐类", "舞蹈类", "播音类"), "艺术本科B段为60个专业（类）平行志愿，必须按艺术综合成绩口径，不套用普通位次推荐模型"));
        add(rules, new BatchRule("ART_SPECIALTY", "艺术类高职专科批", "艺术类", CandidateCategory.ART,
                SupportLevel.QUERY_ONLY, RecommendMode.ART_COMPOSITE, 60, "专业（类）平行志愿", "ArtCompositeRecommendEngine", false,
                List.of("art_specialty", "艺术专科批", "艺术类专科批", "艺术类高职专科批"), List.of("艺术类", "美术类", "音乐类", "舞蹈类", "播音类"), "艺术高职专科批为60个专业（类）平行志愿，必须按艺术综合成绩口径，不套用普通位次推荐模型"));
        add(rules, new BatchRule("SPORTS_UNDERGRADUATE", "体育类本科批", "体育类", CandidateCategory.SPORTS,
                SupportLevel.QUERY_ONLY, RecommendMode.SPORTS_COMPOSITE, 60, "专业（类）平行志愿", "SportsCompositeRecommendEngine", false,
                List.of("sports_undergraduate", "体育本科批", "体育类本科批", "体育类本科"), List.of("体育类"), "体育本科批为60个专业（类）平行志愿，必须按体育综合成绩口径，不套用普通位次推荐模型"));
        add(rules, new BatchRule("SPORTS_SPECIALTY", "体育类高职专科批", "体育类", CandidateCategory.SPORTS,
                SupportLevel.QUERY_ONLY, RecommendMode.SPORTS_COMPOSITE, 60, "专业（类）平行志愿", "SportsCompositeRecommendEngine", false,
                List.of("sports_specialty", "体育专科批", "体育类专科批", "体育类高职专科批"), List.of("体育类"), "体育高职专科批为60个专业（类）平行志愿，必须按体育综合成绩口径，不套用普通位次推荐模型"));
        add(rules, new BatchRule("NATIONAL_SPECIAL", "国家专项计划", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "专项计划", "SpecialPlanEligibilityEngine", false,
                List.of("national_special", "special_national", "国家专项", "国家专项计划"), List.of("普通类"), "国家专项计划需资格校验和单独计划数据"));
        add(rules, new BatchRule("LOCAL_SPECIAL", "地方专项计划", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "专项计划", "SpecialPlanEligibilityEngine", false,
                List.of("local_special", "special_local", "地方专项", "地方专项计划"), List.of("普通类"), "地方专项需资格校验和单独计划数据"));
        add(rules, new BatchRule("UNIVERSITY_SPECIAL", "高校专项计划", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "专项计划", "SpecialPlanEligibilityEngine", false,
                List.of("university_special", "高校专项", "高校专项计划"), List.of("普通类"), "高校专项需报名审核结果与学校名单"));
        add(rules, new BatchRule("ETHNIC_CLASS", "民族班", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "特殊计划", "SpecialPlanEligibilityEngine", false,
                List.of("ethnic_class", "民族班", "民族预科"), List.of("普通类"), "民族班需民族与资格条件校验"));
        add(rules, new BatchRule("PREPARATORY", "预科班", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "特殊计划", "SpecialPlanEligibilityEngine", false,
                List.of("preparatory", "预科", "预科班", "少数民族预科"), List.of("普通类"), "预科班需资格与计划数据校验"));
        add(rules, new BatchRule("ORIENTED", "定向招生", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "特殊计划", "SpecialPlanEligibilityEngine", false,
                List.of("oriented", "directed", "定向", "定向招生"), List.of("普通类"), "定向招生需协议与地区资格校验"));
        add(rules, new BatchRule("FREE_MEDICAL", "免费医学定向", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "特殊计划", "SpecialPlanEligibilityEngine", false,
                List.of("free_medical", "免费医学", "免费医学定向", "农村订单定向医学生"), List.of("普通类"), "免费医学定向需资格与履约条件校验"));
        add(rules, new BatchRule("TEACHER_EXCELLENCE", "优师专项", "普通类", CandidateCategory.SPECIAL_PROGRAM,
                SupportLevel.QUERY_ONLY, RecommendMode.ELIGIBILITY_QUERY, 0, "特殊计划", "SpecialPlanEligibilityEngine", false,
                List.of("teacher_excellence", "free_teacher", "优师", "优师计划", "优师专项", "公费师范"), List.of("普通类"), "优师专项需资格、履约和单独计划数据"));
        return Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    private static void add(Map<String, BatchRule> rules, BatchRule rule) {
        rules.put(rule.batchCode(), rule);
    }

    private static Map<String, String> buildAliases(Collection<BatchRule> rules) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (BatchRule rule : rules) {
            aliases.put(normalizeText(rule.batchCode()).toLowerCase(Locale.ROOT), rule.batchCode());
            aliases.put(normalizeText(rule.batchName()).toLowerCase(Locale.ROOT), rule.batchCode());
            for (String alias : rule.aliases()) {
                aliases.put(normalizeText(alias).toLowerCase(Locale.ROOT), rule.batchCode());
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }

    private static boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return keywords.stream().anyMatch(text::contains);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace(" ", "")
                .replace("　", "")
                .replace("（", "(")
                .replace("）", ")")
                .replace("／", "/")
                .replace("-", "_");
    }
}
