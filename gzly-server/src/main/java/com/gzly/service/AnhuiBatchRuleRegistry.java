package com.gzly.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 安徽 2026 新高考 14 个批次完整规则定义。
 *
 * <p>口径来源（核到原文一字不差）：</p>
 * <ul>
 *   <li>《安徽省 2025 年普通高校招生工作实施办法》第 24-38 条（2025-05-12 省考试院 / 省教育厅联合发布）</li>
 *   <li>《关于做好 2025 年普通高校艺术类专业招生考试工作的通知》（皖招委〔2024〕11 号）</li>
 *   <li>安徽省教育招生考试院 www.ahzsks.cn 政策栏目（2026 实施办法待 6 月下旬正式发文，本文件按 2025 同款建模，2026 出文后冲重）</li>
 * </ul>
 *
 * <p>与 {@link SichuanBatchRuleRegistry}（四川 18 批次）、{@link BatchRuleRegistry}（贵州 18 批次）三者完全独立。
 * 批次代码统一使用 {@code AH_*} 前缀，规则上下文不会跨省互相污染。</p>
 *
 * <p>志愿单位：所有 14 批次均为「院校专业组」，组内 6 个专业 + 是否服从专业调剂选项
 * （免费医学定向 / 农技推广人才定向 不设专业服从；其它批次均有）。
 * 排序规则：平行志愿按「综合分（含政策加分）→ 语+数和 → 语/数最高单科 → 外语 → 首选 → 再选最高」六级同分排序；
 * 顺序志愿按「根据志愿、从高分到低分、按比例投档」。
 * 调档比例：平行志愿 105%（省属 100%）；非平行志愿（顺序）120% 以内。</p>
 */
public final class AnhuiBatchRuleRegistry {

    /** 安徽主流程默认批次（普通本科批）。 */
    public static final String DEFAULT_BATCH_CODE = "AH_BENKE";

    /** 安徽特有的「本科提前批军公师范优师定向」子类型清单（前端 subType 下拉，考生只能选 1 类）。 */
    public static final List<String> TIQIAN_BENKE_SUBTYPES = List.of(
            "军事", "公安", "公费师范", "优师专项", "免费医学定向", "农技推广人才定向");

    public enum CandidateCategory {
        /** 普通类主流程（本科 / 高职专科）。 */
        ORDINARY,
        /** 提前批（军事 / 公安 / 公费师范 / 优师 / 定向 等）。 */
        EARLY,
        /** 艺术类（校考 / 统考 本科 + 高职专科）。 */
        ART,
        /** 体育类（本科 + 高职专科）。 */
        SPORTS,
        /** 专项计划（国家 / 地方 / 高校 三项）。 */
        SPECIAL_PROGRAM,
        /** 其他（高水平运动队 / 西藏定向 / 边防军人子女预科 等已并入 AH_BENKE 内部提示，本枚举位置预留）。 */
        OTHER
    }

    public enum RecommendMode {
        /** 院校专业组平行志愿（普通本科 / 高职专科 / 提前批主流 / 专项主流 / 艺术统考 / 体育）。 */
        PARALLEL_GROUP,
        /** 院校顺序志愿（提前批司法应急/其他、高校专项、艺术校考）。 */
        SEQUENTIAL_COLLEGE,
        /** 仅查询规则与资格条件（数据不全或政策待发时降级用）。 */
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
            String supportNote,
            List<String> subTypes
    ) {
        public BatchRule {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            subTypes = subTypes == null ? List.of() : List.copyOf(subTypes);
        }
    }

    private static final Map<String, BatchRule> RULES = buildRules();
    private static final Map<String, String> ALIASES = buildAliases(RULES.values());

    public static final String OFFICIAL_SOURCE_TITLE = "安徽省 2025 年普通高校招生工作实施办法";
    public static final String OFFICIAL_SOURCE_URL = "https://www.ahzsks.cn/";
    public static final String OFFICIAL_SOURCE_TEXT =
            "安徽 2026 新高考（3+1+2）：志愿单位为「院校专业组」，组内 6 个专业 + 是否服从专业调剂。"
                    + "普通类共 4 个批次：本科提前批（军事/公安/公费师范/优师/免费医学/农技推广 等 6 类合并为 20 平行院校专业组；"
                    + "司法应急消防/其他类 1 顺序）、本科批 45 平行、高职专科提前批（定向培养军士/免费医学/农技推广 20 平行；司法/其他 1 顺序）、"
                    + "高职专科批 45 平行。专项 3 类：国家专项 20 平行、地方专项 20 平行、高校专项 1 顺序。"
                    + "艺术类 3 个批次：校考本科 1 顺序、统考本科 20 平行（A/B 段细分 + 音乐特殊单投）、统考高职专科 20 平行。"
                    + "体育类 2 个批次：本科 20 平行（文化线按本科控线 65%）、高职专科 20 平行。"
                    + "高水平运动队填本科批第 1 志愿；西藏定向 / 边防军人子女预科与本科批同期投档。"
                    + "投档比例：平行志愿 105%（省属 100%），非平行 120% 以内；平行志愿同分排序：综合分→语+数→语/数单科→外语→首选→再选最高。";

    public static final String OFFICIAL_TIE_BREAKER_TEXT =
            "安徽 2026 平行志愿同分排序：先按综合分（含政策加分），同分再依次比较：语+数之和 → 语/数单科最高 → 外语 → 首选科目 → 再选科目最高单科。";

    private AnhuiBatchRuleRegistry() {
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
                () -> new IllegalArgumentException("Unsupported Anhui batch code: " + batchCode));
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
        if (containsAny(normalized, List.of("艺术", "美术", "音乐", "舞蹈", "播音", "戏剧", "影视", "书法", "表导演"))) {
            return "艺术类";
        }
        if (containsAny(normalized, List.of("体育", "运动训练", "健美操", "啦啦操"))) {
            return "体育类";
        }
        return value;
    }

    public static boolean candidateTypeMatches(String ruleCandidateType, String requestedCandidateType) {
        return normalizeCandidateType(ruleCandidateType).equals(normalizeCandidateType(requestedCandidateType));
    }

    /**
     * 14 批次规则定义。批次顺序按官方投档顺序排（提前批 → 专项 → 本科批 → 高职提前 → 高职批 → 艺术 → 体育）。
     */
    private static Map<String, BatchRule> buildRules() {
        Map<String, BatchRule> rules = new LinkedHashMap<>();

        // ===== 1. 普通本科提前批（平行 / 顺序 两套）=====
        // 1.1 军 + 公 + 公费师范 + 优师 + 免医 + 农技推广 6 子类合并 20 平行（考生只能选 1 子类）
        add(rules, new BatchRule(
                "AH_TIQIAN_BENKE_PARALLEL", "普通本科提前批（军事/公安/公费师范/优师/免费医学/农技推广）", "普通类",
                CandidateCategory.EARLY, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of(
                        "普通本科提前批", "本科提前批", "提前本科批", "提前批本科",
                        "本科提前批军事", "本科提前批公安", "本科提前批公费师范",
                        "本科提前批优师", "本科提前批免费医学定向", "本科提前批农技推广",
                        "普通本科提前批军事公安"
                ),
                "安徽 2026 本科提前批军事/公安/公费师范/优师/免费医学/农技推广 6 个子类合并为 20 个平行院校专业组志愿，"
                        + "考生只能在 6 个子类中选 1 类；每组 6 个专业 + 专业服从志愿（免费医学定向、农技推广人才定向不设专业服从）。",
                TIQIAN_BENKE_SUBTYPES));

        // 1.2 司法应急消防 / 其他类（综合评价、定向培养乡村教师）—— 1 顺序
        add(rules, new BatchRule(
                "AH_TIQIAN_BENKE_SEQUENTIAL", "普通本科提前批（司法应急消防/其他类）", "普通类",
                CandidateCategory.EARLY, RecommendMode.SEQUENTIAL_COLLEGE, 1, 6, true,
                "院校顺序志愿", false,
                List.of(
                        "本科提前批司法", "本科提前批应急消防", "本科提前批其他类",
                        "本科提前批综合评价", "本科提前批定向培养乡村教师"
                ),
                "安徽 2026 本科提前批司法（含司法国家专项）、应急消防、其他类（综合评价、定向培养乡村教师）"
                        + "为 1 个院校专业组顺序志愿，组内 6 个专业 + 专业服从志愿，按从高分到低分、按比例投档；"
                        + "考生本科提前批志愿只能在 6 个平行子类与本顺序批中选 1 类。",
                List.of()));

        // ===== 2. 专项计划（本科提前批后单独 3 个批次）=====
        add(rules, new BatchRule(
                "AH_NATIONAL_SPECIAL", "国家专项计划", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("国家专项", "国家专项计划", "农村和脱贫地区国家专项"),
                "安徽 2026 国家专项计划 20 个平行院校专业组志愿，每组 6 专业 + 专业服从；"
                        + "需先通过户籍 / 学籍 / 综合素质 / 农村脱贫地区资格审查后方可填报。",
                List.of()));

        add(rules, new BatchRule(
                "AH_LOCAL_SPECIAL", "地方专项计划", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("地方专项", "地方专项计划", "省属高校地方专项"),
                "安徽 2026 地方专项计划 20 个平行院校专业组志愿，每组 6 专业 + 专业服从；"
                        + "面向安徽省内农村及脱贫地区考生，需通过省考试院专项资格审核。",
                List.of()));

        add(rules, new BatchRule(
                "AH_UNIVERSITY_SPECIAL", "高校专项计划", "普通类",
                CandidateCategory.SPECIAL_PROGRAM, RecommendMode.SEQUENTIAL_COLLEGE, 1, 6, true,
                "院校顺序志愿", false,
                List.of("高校专项", "高校专项计划"),
                "安徽 2026 高校专项计划 1 个院校专业组顺序志愿，每组 6 个专业 + 专业服从志愿；"
                        + "需通过高校自主审核与省考试院公示后方可填报。"
                        + "往年被专项计划录取后放弃入学资格或退学的考生，不再具有专项计划报考资格。",
                List.of()));

        // ===== 3. 本科批主流程 =====
        add(rules, new BatchRule(
                "AH_BENKE", "普通本科批", "普通类",
                CandidateCategory.ORDINARY, RecommendMode.PARALLEL_GROUP, 45, 6, true,
                "院校专业组（平行志愿）", true,
                List.of(
                        "本科批", "普通本科批", "普通本科批次", "本科批次",
                        "AnhuiBenke", "AH_BENKE", "AH_BENKE_PUTONG"
                ),
                "安徽 2026 普通本科批为 45 个平行院校专业组志愿，每组 6 个专业 + 专业服从志愿；"
                        + "高水平运动队的考生须将院校专业组志愿填在第 1 志愿位置；"
                        + "非西藏生源定向西藏就业计划、边防军人子女预科班与普通本科批其他院校专业组志愿同时进行平行志愿投档。",
                List.of()));

        // ===== 4. 高职专科提前批（平行 + 顺序 两套）=====
        add(rules, new BatchRule(
                "AH_TIQIAN_ZHUANKE_PARALLEL", "普通高职（专科）提前批（定向军士/免费医学/农技推广）", "普通类",
                CandidateCategory.EARLY, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（平行志愿）", true,
                List.of(
                        "高职提前批", "专科提前批", "高职专科提前批",
                        "定向培养军士", "高职提前批定向培养军士",
                        "高职提前批免费医学定向", "高职提前批农技推广"
                ),
                "安徽 2026 高职（专科）提前批定向培养军士 / 免费医学定向 / 农技推广人才定向"
                        + "为 20 个平行院校专业组志愿，每组 6 专业（定向培养军士设专业服从；免费医学定向、"
                        + "农技推广人才定向不设专业服从）；考生只能在本提前批 4 类中选 1 类。",
                List.of("定向培养军士", "免费医学定向", "农技推广人才定向")));

        add(rules, new BatchRule(
                "AH_TIQIAN_ZHUANKE_SEQUENTIAL", "普通高职（专科）提前批（司法/其他）", "普通类",
                CandidateCategory.EARLY, RecommendMode.SEQUENTIAL_COLLEGE, 1, 6, true,
                "院校顺序志愿", false,
                List.of("高职提前批司法", "高职专科提前批司法", "高职提前批其他类"),
                "安徽 2026 高职（专科）提前批司法 / 其他类为 1 个院校专业组顺序志愿，"
                        + "组内 6 个专业 + 专业服从志愿。",
                List.of()));

        // ===== 5. 高职专科批主流程 =====
        add(rules, new BatchRule(
                "AH_ZHUANKE", "普通高职（专科）批", "普通类",
                CandidateCategory.ORDINARY, RecommendMode.PARALLEL_GROUP, 45, 6, true,
                "院校专业组（平行志愿）", true,
                List.of("高职专科批", "专科批", "高职批", "普通高职专科批"),
                "安徽 2026 普通高职（专科）批为 45 个平行院校专业组志愿，每组 6 个专业 + 专业服从志愿。",
                List.of()));

        // ===== 6. 艺术类 3 个批次 =====
        add(rules, new BatchRule(
                "AH_ART_XIAOKAO_BENKE", "艺术类校考本科批", "艺术类",
                CandidateCategory.ART, RecommendMode.SEQUENTIAL_COLLEGE, 1, 6, true,
                "院校顺序志愿", false,
                List.of("艺术校考本科批", "艺术校考本科", "艺术类第一批次"),
                "安徽 2026 艺术类校考本科批为 1 个院校专业组顺序志愿，"
                        + "含 6 个专业 + 专业服从志愿；少数经批准开展校考的艺术类专业和戏曲类省际联考专业列入本批次。",
                List.of()));

        add(rules, new BatchRule(
                "AH_ART_TONGKAO_BENKE", "艺术类统考本科批", "艺术类",
                CandidateCategory.ART, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（综合分平行志愿）", true,
                List.of(
                        "艺术统考本科批", "艺术类统考本科", "艺术类第二批次",
                        "艺术类本科批", "艺术本科批", "艺术类本科批A段", "艺术类本科批B段"
                ),
                "安徽 2026 艺术类统考本科批为 20 个平行院校专业组志愿，每组 6 专业 + 专业服从；"
                        + "播音与主持类、表（导）演类、美术与设计类分 A、B 段录取（条件考生可兼报）；"
                        + "音乐类对乐器主副项 / 声器乐有特殊要求的可单独投档（1 专业组 1 专业志愿）。"
                        + "按「综合分优先，遵循志愿」投档，投档比例 100%。",
                List.of()));

        add(rules, new BatchRule(
                "AH_ART_TONGKAO_ZHUANKE", "艺术类统考高职（专科）批", "艺术类",
                CandidateCategory.ART, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（综合分平行志愿）", true,
                List.of("艺术统考高职专科批", "艺术统考专科批", "艺术类高职专科批", "艺术专科批"),
                "安徽 2026 艺术类统考高职（专科）批为 20 个平行院校专业组志愿，每组 6 专业 + 专业服从；"
                        + "考生在每个批次只能选择报考一个艺术类别的志愿。按「综合分优先，遵循志愿」投档，投档比例 100%。",
                List.of()));

        // ===== 7. 体育类 2 个批次 =====
        add(rules, new BatchRule(
                "AH_SPORTS_BENKE", "体育类本科批", "体育类",
                CandidateCategory.SPORTS, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（综合分平行志愿）", true,
                List.of("体育本科批", "体育类本科批", "体育类本科"),
                "安徽 2026 体育类本科批为 20 个平行院校专业组志愿，每组 6 专业 + 专业服从；"
                        + "文化课录取控制分数线分历史 / 物理科目组合，按普通类本科录取控制分数线的 65% 划定；"
                        + "按综合分优先、遵循志愿投档，投档比例 100%；考生可以兼考传统体育 / 健美操 / 啦啦操，但只可选报一类。",
                List.of()));

        add(rules, new BatchRule(
                "AH_SPORTS_ZHUANKE", "体育类高职（专科）批", "体育类",
                CandidateCategory.SPORTS, RecommendMode.PARALLEL_GROUP, 20, 6, true,
                "院校专业组（综合分平行志愿）", true,
                List.of("体育专科批", "体育类专科批", "体育类高职专科批"),
                "安徽 2026 体育类高职（专科）批为 20 个平行院校专业组志愿，每组 6 专业 + 专业服从；"
                        + "文化课录取控制分数线按普通高职（专科）批次录取控制分数线执行；"
                        + "按综合分优先、遵循志愿投档，投档比例 100%。",
                List.of()));

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

    /** 主流程批次清单（PARALLEL_GROUP + mainRankEngine + ORDINARY 主科）。 */
    public static List<String> mainRankBatchCodes() {
        List<String> codes = new ArrayList<>();
        for (BatchRule rule : RULES.values()) {
            if (rule.mainRankEngine()
                    && rule.recommendMode() == RecommendMode.PARALLEL_GROUP
                    && rule.category() == CandidateCategory.ORDINARY) {
                codes.add(rule.batchCode());
            }
        }
        return List.copyOf(codes);
    }

    /** 当 BatchListing 走「按批次模糊匹配 admission_group_line.batch」时使用的关键词列表。 */
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
