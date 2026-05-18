package com.gzly.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 四川 2026 新高考 16 个批次完整规则定义。
 *
 * <p>口径来源：</p>
 * <ul>
 *   <li>四川省 2026 年普通高校招生实施规定（2026-04-29，四川省教育厅 / 教育考试院）。</li>
 *   <li>四川省教育考试院"志愿填报 100 问" ④⑤（2025-06-23）。</li>
 *   <li>四川 2026 艺术体育报名公告（2025-10-24，四川省教育考试院）。</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>与 {@link BatchRuleRegistry}（贵州 18 批次）独立维护，避免不同省份口径互相污染。</li>
 *   <li>批次 code 命名 SC_ 前缀防止与贵州批次重名（贵州 NORMAL_UNDERGRADUATE vs 四川 SC_BENKE_B 等）。</li>
 *   <li>顺序志愿与平行志愿明确区分；艺术 / 体育走综合分平行志愿；高校专项 / 高水平运动队 走顺序志愿。</li>
 *   <li>2026 vs 2025 变更项（如国家专项 2→6 平行、高校专项顺序→平行 20）以官方实施细则为准，
 *       当前先按 2026 实施规定公布的最新口径建表。</li>
 * </ul>
 *
 * <p>志愿数：每个院校专业组 6 个专业 + 是否服从专业调剂选项（前端 form 体现）。</p>
 */
public final class SichuanBatchRuleRegistry {

    /** 四川主流程默认批次。 */
    public static final String DEFAULT_BATCH_CODE = "SC_BENKE_B";

    public enum CandidateCategory {
        ORDINARY,
        EARLY,
        ART,
        SPORTS,
        SPECIAL_PROGRAM,
        OTHER
    }

    public enum RecommendMode {
        /** 院校专业组平行志愿（普通类 + 艺术 + 体育 + 专项 + 高职专科）。 */
        PARALLEL_GROUP,
        /** 院校顺序志愿（提前 A 段 / 高水平运动队 / 高职专科提前 / 艺术本科提前 等）。 */
        SEQUENTIAL_COLLEGE,
        /** 仅查询规则与资格条件，不出概率（高校专项 2026 官方口径未稳定时降级用）。 */
        QUERY_ONLY
    }

    public record BatchRule(
            String batchCode,
            String batchName,
            String candidateType,
            CandidateCategory category,
            RecommendMode recommendMode,
            int targetCount,
            int majorsPerGroup,
            boolean hasAdjustment,
            String volunteerMode,
            boolean mainRankEngine,
            List<String> aliases,
            String supportNote
    ) {
        public BatchRule {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    private static final Map<String, BatchRule> RULES = buildRules();
    private static final Map<String, String> ALIASES = buildAliases(RULES.values());

    public static final String OFFICIAL_SOURCE_TITLE = "四川省 2026 年普通高校招生实施规定";
    public static final String OFFICIAL_SOURCE_URL = "https://www.sceea.cn/";
    public static final String OFFICIAL_SOURCE_TEXT =
            "四川 2026 新高考：本科提前批分 A 段前国家专项（6 平行院校专业组）、A 段（1+2 顺序）、A/B 段间高校专项（按 2026 细则确认 20 平行 vs 1 顺序）、B 段（30 平行）；"
                    + "本科批分 A 段国家专项与地方专项（各 20 平行）、A/B 段间高水平运动队（1 顺序）、B 段（45 平行）、B 段后区域均衡（20 平行）、省属高校少民预科（20 平行）；"
                    + "高职专科批（数量以 2026 细则为准）；艺术类本科批、艺术类高职专科批、体育类本科批、体育类高职专科批均为 45 平行院校专业组，按综合分排序录取。"
                    + "顺序志愿走\"根据志愿、从高分到低分、按比例投档\"，平行志愿走\"位次优先、遵循志愿、一轮投档\"。";

    private SichuanBatchRuleRegistry() {
    }

    public static List<BatchRule> allRules() {
        return List.copyOf(RULES.values());
    }

    public static Optional<BatchRule> find(String batchCode) {
        String normalized = normalizeBatchCode(batchCode);
        return Optional.ofNullable(RULES.get(normalized));
    }

    public static BatchRule require(String batchCode) {
        return find(batchCode).orElseThrow(
                () -> new IllegalArgumentException("Unsupported Sichuan batch code: " + batchCode));
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
        return ALIASES.getOrDefault(key, upper);
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

    public static boolean candidateTypeMatches(String ruleCandidateType, String requestedCandidateType) {
        return normalizeCandidateType(ruleCandidateType).equals(normalizeCandidateType(requestedCandidateType));
    }

    /**
     * 16 批次规则定义。批次顺序按官方投档顺序排（提前批 → 本科 A/B → 高职 → 艺术 → 体育）。
     */
    private static Map<String, BatchRule> buildRules() {
        Map<String, BatchRule> rules = new LinkedHashMap<>();

        // ===== 1. 普通类本科提前批 =====
        // A 段前国家专项：6 个平行院校专业组（2026 由 2 → 6）
        add(rules, new BatchRule(
                "SC_TIQIAN_BEFORE_A_NATIONAL", "本科提前批 A 段前国家专项", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 6, 6, true,
                "院校专业组（平行志愿）", false,
                List.of("提前批A段前国家专项", "本科提前批A段前国家专项", "国家专项A段前"),
                "普通类本科提前批 A 段前国家专项计划，2026 由 2 增至 6 个平行院校专业组志愿，需先通过国家专项资格审查。"));

        // A 段顺序志愿：1 第一 + 2 平行第二
        add(rules, new BatchRule(
                "SC_TIQIAN_A", "本科提前批 A 段", "普通类",
                CandidateCategory.EARLY, RecommendMode.SEQUENTIAL_COLLEGE, 3, 6, true,
                "院校顺序志愿（1 第一 + 2 平行第二）", false,
                List.of("本科提前批A段", "提前批A段", "军事公安空军提前"),
                "本科提前批 A 段含军事、公安、空军、海军、武警等院校，1 个第一志愿 + 2 个平行第二志愿，按顺序志愿规则投档。"));

        // A 段后 B 段前高校专项：按 2026 实施规定有 20 平行 / 1 顺序两种说法，先按 1 顺序口径稳上线
        add(rules, new BatchRule(
                "SC_GAOXIAO_SPECIAL_PRE_B", "本科提前批高校专项", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.SEQUENTIAL_COLLEGE, 1, 6, true,
                "院校顺序志愿（待 6 月细则确认是否改 20 平行）", false,
                List.of("高校专项", "本科提前高校专项", "提前批高校专项"),
                "本科提前批 A 段后 B 段前高校专项计划，2026 官方表述存在 1 顺序 vs 20 平行两种口径，"
                        + "暂按 1 顺序兜底，待 6 月实施细则正式公布后切换。"));

        // B 段：30 平行院校专业组（公费师范生等）
        add(rules, new BatchRule(
                "SC_TIQIAN_B", "本科提前批 B 段", "普通类",
                CandidateCategory.EARLY, RecommendMode.PARALLEL_GROUP, 30, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("本科提前批B段", "提前批B段", "国家公费师范"),
                "本科提前批 B 段含国家公费师范生、优师专项、免费医学定向、地方优师等，30 个平行院校专业组。"));

        // ===== 2. 本科批 =====
        // A 段国家专项：20 平行院校专业组
        add(rules, new BatchRule(
                "SC_BENKE_A_NATIONAL", "本科批 A 段国家专项", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("本科批A段国家专项", "本科A段国家专项"),
                "本科批 A 段国家专项计划，面向集中连片特困地区、国家级贫困县考生，20 个平行院校专业组。"));

        // A 段地方专项：20 平行院校专业组
        add(rules, new BatchRule(
                "SC_BENKE_A_LOCAL", "本科批 A 段地方专项", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("本科批A段地方专项", "本科A段地方专项"),
                "本科批 A 段地方专项计划，面向四川省民族自治地方、边远地区，20 个平行院校专业组。"));

        // A 段后 B 段前高校专项：按 2026 升级口径 20 平行（与提前批高校专项区分）
        add(rules, new BatchRule(
                "SC_BENKE_GAOXIAO_SPECIAL", "本科批 A 段后高校专项", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("本科批高校专项", "本科A段后高校专项", "高校专项20平行"),
                "本科批 A 段后 B 段前高校专项计划，2026 由 2 顺序升级为 20 平行院校专业组，调整到国家专项前投档。"));

        // 高水平运动队：1 院校顺序志愿
        add(rules, new BatchRule(
                "SC_BENKE_SPORTS_TEAM", "本科批高水平运动队", "普通类",
                CandidateCategory.OTHER, RecommendMode.SEQUENTIAL_COLLEGE, 1, 6, false,
                "院校顺序志愿（1 个）", false,
                List.of("高水平运动队", "本科批高水平运动队"),
                "本科批 A 段后 B 段前高水平运动队 1 个院校顺序志愿，需通过教育部高水平运动队认定与体育专项测试。"));

        // B 段：45 平行院校专业组（主流程）
        add(rules, new BatchRule(
                "SC_BENKE_B", "本科批 B 段", "普通类",
                CandidateCategory.ORDINARY, RecommendMode.PARALLEL_GROUP, 45, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("本科批B段", "本科普通批", "普通本科批B段", "BenkeB"),
                "本科批 B 段为四川主流程，45 个平行院校专业组志愿，每组 6 专业 + 是否服从专业调剂。"));

        // B 段后区域教育均衡发展专项：20 平行院校专业组
        add(rules, new BatchRule(
                "SC_BENKE_REGION_BALANCE", "本科批 B 段后区域教育均衡发展专项", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("区域教育均衡", "区域教育均衡发展专项", "本科批后区域均衡"),
                "本科批 B 段后区域教育均衡发展专项，面向四川教育薄弱区县考生，20 个平行院校专业组。"));

        // 省属高校少数民族预科：20 平行院校专业组
        add(rules, new BatchRule(
                "SC_BENKE_MINORITY_PRE", "本科批省属高校少数民族预科", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("少数民族预科", "省属高校少数民族预科", "民族预科"),
                "本科批省属高校少数民族预科 20 个平行院校专业组，需具备少数民族身份和户籍资格。"));

        // ===== 3. 高职（专科）批 =====
        // 高职专科批：45 平行院校专业组（按贵州对齐，实际数量以 6 月细则为准）
        add(rules, new BatchRule(
                "SC_ZHUANKE_B", "高职（专科）批", "普通类",
                CandidateCategory.ORDINARY, RecommendMode.PARALLEL_GROUP, 45, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("高职专科批", "专科批", "高职批"),
                "高职（专科）批 45 个平行院校专业组志愿，实际数量以 2026 实施细则为准。"));

        // 高职专科提前批：1 第一 + 2 平行第二
        add(rules, new BatchRule(
                "SC_ZHUANKE_EARLY", "高职（专科）提前批", "普通类",
                CandidateCategory.EARLY, RecommendMode.SEQUENTIAL_COLLEGE, 3, 6, false,
                "院校顺序志愿（1 第一 + 2 平行第二）", false,
                List.of("高职专科提前批", "专科提前批"),
                "高职（专科）提前批含定向培养军士、公安专科等，1 个第一志愿 + 2 个平行第二志愿。"));

        // ===== 4. 艺术类（综合分平行 45 + 艺术类本科提前批顺序） =====
        // 艺术类本科提前批：顺序志愿
        add(rules, new BatchRule(
                "SC_ART_TIQIAN", "艺术类本科提前批", "艺术类",
                CandidateCategory.ART, RecommendMode.SEQUENTIAL_COLLEGE, 1, 6, false,
                "院校顺序志愿", false,
                List.of("艺术类本科提前批", "艺术本科提前批"),
                "艺术类本科提前批含独立设置艺术院校、参照独立设置艺术院校及部分高校艺术类专业，按顺序志愿投档；需校考或专项审核。"));

        add(rules, new BatchRule(
                "SC_ART_BENKE", "艺术类本科批", "艺术类",
                CandidateCategory.ART, RecommendMode.PARALLEL_GROUP, 45, 6, false,
                "院校专业组（综合分平行志愿）", true,
                List.of("艺术类本科批", "艺术本科批"),
                "艺术类本科批 45 个平行院校专业组，按综合成绩（文化 + 省级专业统考）位次投档，综合分公式按统考类别分两档。"));

        add(rules, new BatchRule(
                "SC_ART_ZHUANKE", "艺术类高职（专科）批", "艺术类",
                CandidateCategory.ART, RecommendMode.PARALLEL_GROUP, 45, 6, false,
                "院校专业组（综合分平行志愿）", true,
                List.of("艺术高职专科批", "艺术专科批"),
                "艺术类高职（专科）批 45 个平行院校专业组，按综合成绩位次投档。"));

        // ===== 5. 体育类（综合分平行 45） =====
        add(rules, new BatchRule(
                "SC_SPORTS_BENKE", "体育类本科批", "体育类",
                CandidateCategory.SPORTS, RecommendMode.PARALLEL_GROUP, 45, 6, false,
                "院校专业组（综合分平行志愿）", true,
                List.of("体育类本科批", "体育本科批"),
                "体育类本科批 45 个平行院校专业组，按体育统考成绩位次投档（双线达标后按统考分排序）。"));

        add(rules, new BatchRule(
                "SC_SPORTS_ZHUANKE", "体育类高职（专科）批", "体育类",
                CandidateCategory.SPORTS, RecommendMode.PARALLEL_GROUP, 45, 6, false,
                "院校专业组（综合分平行志愿）", true,
                List.of("体育高职专科批", "体育专科批"),
                "体育类高职（专科）批 45 个平行院校专业组，按体育统考成绩位次投档。"));

        return Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    private static void add(Map<String, BatchRule> rules, BatchRule rule) {
        rules.put(rule.batchCode(), rule);
    }

    private static Map<String, String> buildAliases(java.util.Collection<BatchRule> rules) {
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

    /** 主流程批次清单（PARALLEL_GROUP + targetCount > 0 + mainRankEngine）。 */
    public static List<String> mainRankBatchCodes() {
        List<String> codes = new ArrayList<>();
        for (BatchRule rule : RULES.values()) {
            if (rule.mainRankEngine() && rule.recommendMode() == RecommendMode.PARALLEL_GROUP) {
                codes.add(rule.batchCode());
            }
        }
        return List.copyOf(codes);
    }

    /** 当 BatchListing 走"按批次模糊匹配 admission_group_line.batch"时使用的关键词列表。 */
    public static List<String> batchKeywords(String batchCode) {
        BatchRule rule = find(batchCode).orElse(null);
        if (rule == null) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        keywords.add(rule.batchName());
        keywords.addAll(rule.aliases());
        return List.copyOf(keywords);
    }
}
