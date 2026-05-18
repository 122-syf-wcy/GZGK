package com.gzly.service;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 四川 2026 艺术 / 体育综合分公式计算器。
 *
 * <p>口径来源：四川省教育考试院 2026-05 公告 + 四川省 2026 年普通高校招生实施规定（2026-04-29）。</p>
 *
 * <h2>艺术类综合分公式</h2>
 *
 * <h3>类别 1：文化 50% + 统考 50%（共 7 个统考类别）</h3>
 * <ul>
 *   <li>美术与设计类</li>
 *   <li>戏剧影视编导类</li>
 *   <li>戏剧影视表演类</li>
 *   <li>戏剧影视导演类</li>
 *   <li>服装表演类</li>
 *   <li>播音与主持类</li>
 *   <li>（航空服务艺术见类别 2，按教育考试院 2026 公告分组）</li>
 * </ul>
 *
 * <p>公式：<code>综合 = 高考文化成绩 × 50% + 省级专业统考成绩 × (750/300) × 50%</code></p>
 *
 * <h3>类别 2：文化 30% + 统考 70%（共 5 个统考类别）</h3>
 * <ul>
 *   <li>音乐表演类</li>
 *   <li>音乐教育类</li>
 *   <li>舞蹈类</li>
 *   <li>书法类</li>
 *   <li>航空服务艺术类</li>
 * </ul>
 *
 * <p>公式：<code>综合 = 高考文化成绩 × 30% + 省级专业统考成绩 × (750/300) × 70%</code></p>
 *
 * <h2>体育类综合分公式</h2>
 *
 * <p>体育平行志愿：按体育专业统考成绩排序（文化、专业双达线后）。</p>
 *
 * <p>综合分排序口径：<code>综合 = 高考文化成绩 × 30% + 体育统考成绩 × (750/100) × 70%</code></p>
 *
 * <h2>使用方式</h2>
 *
 * <pre>
 * SichuanCompositeScoreCalculator calc = new SichuanCompositeScoreCalculator();
 *
 * // 艺术类：文化 480，美术统考 280，综合分 ≈ 480×0.5 + 280×2.5×0.5 = 240 + 350 = 590
 * double art = calc.calculateArt(480, 280, "美术与设计类").orElseThrow().getScore();
 *
 * // 体育类：文化 450，体育统考 88，综合分 ≈ 450×0.3 + 88×7.5×0.7 = 135 + 462 = 597
 * double sport = calc.calculateSports(450, 88).getScore();
 * </pre>
 */
@Service
public class SichuanCompositeScoreCalculator {

    /** 文化分上限（卷面 750）。 */
    public static final int CULTURE_MAX = 750;
    /** 艺术统考分上限（300 分制）。 */
    public static final int ART_RAW_MAX = 300;
    /** 体育统考分上限（100 分制）。 */
    public static final int SPORTS_RAW_MAX = 100;
    /** 艺术类统考折算系数：750 / 300 = 2.5。 */
    public static final double ART_RAW_TO_750 = (double) CULTURE_MAX / ART_RAW_MAX;
    /** 体育类统考折算系数：750 / 100 = 7.5。 */
    public static final double SPORTS_RAW_TO_750 = (double) CULTURE_MAX / SPORTS_RAW_MAX;

    /**
     * 计算艺术类综合分。
     *
     * @param cultureScore   高考文化成绩（含照顾加分），0-750
     * @param professional   省级专业统考成绩，0-300
     * @param category       统考类别（美术与设计类 / 音乐表演类 等），用于路由文化-统考权重组合
     * @return 综合分 + 公式说明 + 权重明细；类别无法识别时返回 Optional.empty()
     */
    public Optional<CompositeScoreResult> calculateArt(int cultureScore, int professional, String category) {
        return resolveArtCategory(category).map(group -> calculate(
                cultureScore, professional, ART_RAW_MAX, ART_RAW_TO_750, group, "艺术类"));
    }

    /**
     * 计算体育类综合分（按平行志愿口径：文化 30% + 体育统考 70%）。
     */
    public CompositeScoreResult calculateSports(int cultureScore, int sportsProfessional) {
        return calculate(cultureScore, sportsProfessional, SPORTS_RAW_MAX, SPORTS_RAW_TO_750,
                ArtSportsGroup.SPORTS_30_70, "体育类");
    }

    /**
     * 统一的综合分计算入口；按 candidateType + category（艺术统考类别）路由。
     *
     * @param candidateType 普通类 / 艺术类 / 体育类
     * @param artCategory   艺术统考类别（美术与设计类 等）；非艺术时可为空
     */
    public Optional<CompositeScoreResult> calculate(String candidateType, int cultureScore,
                                                    int professionalScore, String artCategory) {
        String type = candidateType == null ? "" : candidateType.trim();
        if ("艺术类".equals(type)) {
            return calculateArt(cultureScore, professionalScore, artCategory);
        }
        if ("体育类".equals(type)) {
            return Optional.of(calculateSports(cultureScore, professionalScore));
        }
        return Optional.empty();
    }

    /**
     * 把艺术统考类别归一化到 50/50 vs 30/70 两档。
     * 输入支持中英文混合、带"类"后缀、空格、繁体括号。识别失败返回 empty。
     */
    public Optional<ArtSportsGroup> resolveArtCategory(String category) {
        String key = normalize(category);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        // 50/50：美术 / 设计 / 戏剧影视编导 / 戏剧影视表演 / 戏剧影视导演 / 服装表演 / 播音与主持
        if (containsAny(key, "美术", "设计")) return Optional.of(ArtSportsGroup.ART_50_50);
        if (containsAny(key, "戏剧影视编导", "编导")) return Optional.of(ArtSportsGroup.ART_50_50);
        if (containsAny(key, "戏剧影视表演", "戏剧影视导演")) return Optional.of(ArtSportsGroup.ART_50_50);
        if (containsAny(key, "服装表演")) return Optional.of(ArtSportsGroup.ART_50_50);
        if (containsAny(key, "播音与主持", "播音主持", "播音")) return Optional.of(ArtSportsGroup.ART_50_50);
        // 30/70：音乐表演 / 音乐教育 / 舞蹈 / 书法 / 航空服务艺术
        if (containsAny(key, "音乐表演", "音乐教育", "音乐")) return Optional.of(ArtSportsGroup.ART_30_70);
        if (containsAny(key, "舞蹈")) return Optional.of(ArtSportsGroup.ART_30_70);
        if (containsAny(key, "书法")) return Optional.of(ArtSportsGroup.ART_30_70);
        if (containsAny(key, "航空服务艺术", "航空服务")) return Optional.of(ArtSportsGroup.ART_30_70);
        return Optional.empty();
    }

    /**
     * 列出所有支持的艺术统考类别，用于前端下拉。
     */
    public java.util.List<String> listArtCategories() {
        return java.util.List.of(
                "美术与设计类",
                "戏剧影视编导类",
                "戏剧影视表演类",
                "戏剧影视导演类",
                "服装表演类",
                "播音与主持类",
                "音乐表演类",
                "音乐教育类",
                "舞蹈类",
                "书法类",
                "航空服务艺术类"
        );
    }

    private CompositeScoreResult calculate(int culture, int professional, int professionalMax,
                                           double professionalScale, ArtSportsGroup group, String candidateType) {
        int clampedCulture = Math.max(0, Math.min(CULTURE_MAX, culture));
        int clampedPro = Math.max(0, Math.min(professionalMax, professional));
        double cultureWeighted = clampedCulture * group.cultureRatio;
        double proWeighted = clampedPro * professionalScale * group.professionalRatio;
        double score = round(cultureWeighted + proWeighted);
        CompositeScoreResult result = new CompositeScoreResult();
        result.setCandidateType(candidateType);
        result.setCategory(group.label);
        result.setCultureRatio(group.cultureRatio);
        result.setProfessionalRatio(group.professionalRatio);
        result.setCultureScore(clampedCulture);
        result.setProfessionalScore(clampedPro);
        result.setProfessionalScale(round(professionalScale * 100) / 100.0);
        result.setCultureWeighted(round(cultureWeighted));
        result.setProfessionalWeighted(round(proWeighted));
        result.setScore(score);
        result.setFormula(String.format(Locale.ROOT,
                "综合 = 文化 %d × %.0f%% + 统考 %d × (750/%d) × %.0f%% = %.0f + %.0f = %.2f",
                clampedCulture, group.cultureRatio * 100,
                clampedPro, professionalMax,
                group.professionalRatio * 100,
                cultureWeighted, proWeighted, score));
        return result;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim()
                .replace(" ", "")
                .replace("　", "")
                .replace("（", "(")
                .replace("）", ")");
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isEmpty()) return false;
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && text.contains(k)) return true;
        }
        return false;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 艺体统考-文化权重组合。
     */
    public enum ArtSportsGroup {
        /** 美术 / 设计 / 戏剧编导 / 表演 / 导演 / 服装表演 / 播音 → 50%+50% */
        ART_50_50("美术/设计/戏剧/服装/播音类", 0.5, 0.5),
        /** 音乐 / 舞蹈 / 书法 / 航空服务艺术 → 30%+70% */
        ART_30_70("音乐/舞蹈/书法/航空艺术类", 0.3, 0.7),
        /** 体育类（平行志愿综合分排序） → 30%+70% */
        SPORTS_30_70("体育类", 0.3, 0.7);

        public final String label;
        public final double cultureRatio;
        public final double professionalRatio;

        ArtSportsGroup(String label, double cultureRatio, double professionalRatio) {
            this.label = label;
            this.cultureRatio = cultureRatio;
            this.professionalRatio = professionalRatio;
        }
    }

    @Data
    public static class CompositeScoreResult {
        private String candidateType;
        private String category;
        private double cultureRatio;
        private double professionalRatio;
        private int cultureScore;
        private int professionalScore;
        private double professionalScale;
        private double cultureWeighted;
        private double professionalWeighted;
        private double score;
        private String formula;
    }
}
