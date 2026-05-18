package com.gzly.service;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 安徽 2025-2026 艺术 / 体育综合分公式计算器。
 *
 * <p>口径来源（必须核到原文一字不差）：</p>
 * <ul>
 *   <li>《安徽省高等学校招生委员会 安徽省教育厅关于做好 2025 年普通高校艺术类专业招生考试工作的通知》
 *       （皖招委〔2024〕11 号）</li>
 *   <li>《安徽省 2025 年普通高校招生工作实施办法》（皖招委 2025-05-12 发布）</li>
 *   <li>安徽省 2025 年体育类本科文化课录取控制分数线公告（物理类 300 / 历史类 310，按本科控线 65% 划定）</li>
 * </ul>
 *
 * <h2>艺术类综合分公式</h2>
 *
 * <h3>综合分1：文化 50% + 统考 50%（共 5 个统考类别）</h3>
 * <ul>
 *   <li>音乐表演类 / 音乐教育类（统称音乐类）</li>
 *   <li>舞蹈类</li>
 *   <li>表（导）演类（戏剧影视表演 / 戏剧影视导演 / 服装表演）</li>
 *   <li>美术与设计类</li>
 *   <li>书法类</li>
 * </ul>
 *
 * <p>公式：<code>综合分1 = 文化课成绩 × 50% + 专业统考成绩 × 2.5 × 50%</code>
 *  （统考满分 300，2.5 = 750/300 折 750 制）</p>
 *
 * <h3>综合分2：文化 70% + 统考 30%（共 1 个统考类别）</h3>
 * <ul><li>播音与主持类</li></ul>
 *
 * <p>公式：<code>综合分2 = 文化课成绩 × 70% + 专业统考成绩 × 2.5 × 30%</code></p>
 *
 * <h2>体育类综合分公式</h2>
 *
 * <p>体育平行志愿（本科 / 高职专科）：<br>
 * <code>综合 = 1.2 × 专业总分 + 0.8 × [60 + 40 × (文化分 - 本科文化分数线) ÷ (750 - 本科文化分数线)]</code></p>
 *
 * <ul>
 *   <li>专业总分：体育统考成绩，0-100 分制</li>
 *   <li>文化分：高考文化课成绩（含政策加分）</li>
 *   <li>本科文化分数线：当年体育类本科文化课录取控制分数线（按普通类本科控线 65% 划定）
 *       <ul>
 *         <li>2025 物理类：<strong>300</strong></li>
 *         <li>2025 历史类：<strong>310</strong></li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>使用方式</h2>
 *
 * <pre>
 * AnhuiCompositeScoreCalculator calc = new AnhuiCompositeScoreCalculator();
 *
 * // 艺术类（综合分1）：文化 480、美术统考 280
 * //   综合 = 480×0.5 + 280×2.5×0.5 = 240 + 350 = 590
 * double art = calc.calculateArt(480, 280, "美术与设计类").orElseThrow().getScore();
 *
 * // 艺术类（综合分2 播音）：文化 520、播音统考 250
 * //   综合 = 520×0.7 + 250×2.5×0.3 = 364 + 187.5 = 551.5
 * double broadcast = calc.calculateArt(520, 250, "播音与主持类").orElseThrow().getScore();
 *
 * // 体育类：文化 420、体育统考 85、物理类（控线 300）
 * //   综合 = 1.2×85 + 0.8×[60 + 40×(420-300)/(750-300)] = 102 + 0.8×[60 + 10.667] = 102 + 56.533 ≈ 158.53
 * double sports = calc.calculateSports(420, 85, "物理").getScore();
 * </pre>
 */
@Service
public class AnhuiCompositeScoreCalculator {

    /** 文化分上限（卷面 750）。 */
    public static final int CULTURE_MAX = 750;
    /** 艺术统考分上限（300 分制）。 */
    public static final int ART_RAW_MAX = 300;
    /** 体育统考分上限（100 分制）。 */
    public static final int SPORTS_RAW_MAX = 100;
    /** 艺术折算系数：750 / 300 = 2.5。 */
    public static final double ART_RAW_TO_750 = (double) CULTURE_MAX / ART_RAW_MAX;
    /** 体育综合分系数：1.2 × 专业 + 0.8 × [60 + 40 × 文化占比]。 */
    public static final double SPORTS_PROFESSIONAL_COEF = 1.2;
    public static final double SPORTS_CULTURE_COEF = 0.8;
    public static final double SPORTS_BASE = 60.0;
    public static final double SPORTS_CULTURE_RANGE = 40.0;
    /** 2025 安徽体育类本科文化课控线（物理类）。 */
    public static final int SPORTS_BENKE_LINE_PHYSICS_DEFAULT = 300;
    /** 2025 安徽体育类本科文化课控线（历史类）。 */
    public static final int SPORTS_BENKE_LINE_HISTORY_DEFAULT = 310;

    /**
     * 计算艺术类综合分。
     *
     * @param cultureScore 文化课成绩（含政策加分），0-750
     * @param professional 专业统考成绩，0-300
     * @param category     统考类别（音乐类 / 美术与设计类 / 播音与主持类 等），用于路由 50/50 vs 70/30
     * @return 综合分 + 公式说明 + 权重明细；类别无法识别时返回 Optional.empty()
     */
    public Optional<CompositeScoreResult> calculateArt(int cultureScore, int professional, String category) {
        return resolveArtCategory(category).map(group -> calculateArtInternal(
                cultureScore, professional, group, category));
    }

    /**
     * 计算体育类综合分（本科平行志愿口径，1.2 × 专业 + 0.8 × [60 + 40 × 文化占比]）。
     *
     * @param cultureScore        文化课成绩（含政策加分），0-750
     * @param sportsProfessional  体育统考成绩，0-100
     * @param firstSubject        首选科目 "物理" / "历史"，用于路由控线（不区分时按物理默认 300）
     */
    public CompositeScoreResult calculateSports(int cultureScore, int sportsProfessional, String firstSubject) {
        int line = "历史".equals(firstSubject == null ? "" : firstSubject.trim())
                ? SPORTS_BENKE_LINE_HISTORY_DEFAULT
                : SPORTS_BENKE_LINE_PHYSICS_DEFAULT;
        return calculateSportsInternal(cultureScore, sportsProfessional, line, firstSubject);
    }

    /**
     * 统一综合分计算入口（按 candidateType + category + firstSubject 路由）。
     *
     * @param candidateType 普通类 / 艺术类 / 体育类
     * @param artCategory   艺术统考类别（音乐类 / 美术与设计类 等），非艺术可空
     * @param firstSubject  体育类需要：物理 / 历史，用于体育文化控线
     */
    public Optional<CompositeScoreResult> calculate(String candidateType, int cultureScore,
                                                    int professionalScore, String artCategory,
                                                    String firstSubject) {
        String type = candidateType == null ? "" : candidateType.trim();
        if ("艺术类".equals(type)) {
            return calculateArt(cultureScore, professionalScore, artCategory);
        }
        if ("体育类".equals(type)) {
            return Optional.of(calculateSports(cultureScore, professionalScore, firstSubject));
        }
        return Optional.empty();
    }

    /**
     * 艺术统考类别 → 50/50 vs 70/30 路由。
     * 输入支持中文混合、空格、繁体括号、"类"后缀。识别失败返回 empty。
     */
    public Optional<ArtGroup> resolveArtCategory(String category) {
        String key = normalize(category);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        // 70/30：播音与主持
        if (containsAny(key, "播音与主持", "播音主持", "播音")) {
            return Optional.of(ArtGroup.ART_70_30);
        }
        // 50/50：音乐 / 舞蹈 / 表（导）演 / 美术与设计 / 书法
        if (containsAny(key, "音乐表演", "音乐教育", "音乐")) return Optional.of(ArtGroup.ART_50_50);
        if (containsAny(key, "舞蹈")) return Optional.of(ArtGroup.ART_50_50);
        if (containsAny(key, "表(导)演", "表导演", "戏剧影视表演", "戏剧影视导演",
                "服装表演", "表演", "导演")) {
            return Optional.of(ArtGroup.ART_50_50);
        }
        if (containsAny(key, "美术", "设计")) return Optional.of(ArtGroup.ART_50_50);
        if (containsAny(key, "书法")) return Optional.of(ArtGroup.ART_50_50);
        return Optional.empty();
    }

    /** 所有支持的安徽艺术统考类别，给前端下拉。 */
    public List<String> listArtCategories() {
        return List.of(
                "音乐表演类",
                "音乐教育类",
                "舞蹈类",
                "表（导）演类",
                "美术与设计类",
                "书法类",
                "播音与主持类"
        );
    }

    private CompositeScoreResult calculateArtInternal(int culture, int professional, ArtGroup group,
                                                     String originalCategory) {
        int clampedCulture = Math.max(0, Math.min(CULTURE_MAX, culture));
        int clampedPro = Math.max(0, Math.min(ART_RAW_MAX, professional));
        double cultureWeighted = clampedCulture * group.cultureRatio;
        double proWeighted = clampedPro * ART_RAW_TO_750 * group.professionalRatio;
        double score = round(cultureWeighted + proWeighted);
        CompositeScoreResult result = new CompositeScoreResult();
        result.setCandidateType("艺术类");
        result.setCategory(group.label);
        result.setSelectedCategory(originalCategory == null ? "" : originalCategory.trim());
        result.setCultureRatio(group.cultureRatio);
        result.setProfessionalRatio(group.professionalRatio);
        result.setCultureScore(clampedCulture);
        result.setProfessionalScore(clampedPro);
        result.setProfessionalScale(round(ART_RAW_TO_750 * 100) / 100.0);
        result.setCultureWeighted(round(cultureWeighted));
        result.setProfessionalWeighted(round(proWeighted));
        result.setScore(score);
        result.setFormula(String.format(Locale.ROOT,
                "综合 = 文化 %d × %.0f%% + 统考 %d × (750/%d) × %.0f%% = %.0f + %.0f = %.2f",
                clampedCulture, group.cultureRatio * 100,
                clampedPro, ART_RAW_MAX,
                group.professionalRatio * 100,
                cultureWeighted, proWeighted, score));
        return result;
    }

    private CompositeScoreResult calculateSportsInternal(int culture, int sportsProfessional,
                                                         int sportsBenkeLine, String firstSubject) {
        int clampedCulture = Math.max(0, Math.min(CULTURE_MAX, culture));
        int clampedPro = Math.max(0, Math.min(SPORTS_RAW_MAX, sportsProfessional));
        int benkeLine = Math.max(0, Math.min(CULTURE_MAX - 1, sportsBenkeLine));
        double cultureRatioInRange = (clampedCulture - benkeLine) / (double) (CULTURE_MAX - benkeLine);
        double cultureSubtotal = SPORTS_BASE + SPORTS_CULTURE_RANGE * cultureRatioInRange;
        double proWeighted = SPORTS_PROFESSIONAL_COEF * clampedPro;
        double cultureWeighted = SPORTS_CULTURE_COEF * cultureSubtotal;
        double score = round(proWeighted + cultureWeighted);
        CompositeScoreResult result = new CompositeScoreResult();
        result.setCandidateType("体育类");
        result.setCategory("体育类（本科平行志愿）");
        result.setSelectedCategory(firstSubject == null ? "" : firstSubject.trim());
        result.setCultureRatio(SPORTS_CULTURE_COEF);
        result.setProfessionalRatio(SPORTS_PROFESSIONAL_COEF);
        result.setCultureScore(clampedCulture);
        result.setProfessionalScore(clampedPro);
        result.setProfessionalScale(SPORTS_PROFESSIONAL_COEF);
        result.setCultureBenkeLine(benkeLine);
        result.setCultureWeighted(round(cultureWeighted));
        result.setProfessionalWeighted(round(proWeighted));
        result.setScore(score);
        result.setFormula(String.format(Locale.ROOT,
                "综合 = 1.2 × 专业 %d + 0.8 × [60 + 40 × (文化 %d - 本科文化控线 %d) / (750 - %d)] = %.2f + 0.8 × %.2f = %.2f",
                clampedPro, clampedCulture, benkeLine, benkeLine,
                proWeighted, cultureSubtotal, score));
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

    /** 艺术统考-文化权重组合（安徽 2025-2026）。 */
    public enum ArtGroup {
        /** 音乐 / 舞蹈 / 表（导）演 / 美术与设计 / 书法 → 50/50。 */
        ART_50_50("音乐/舞蹈/表导演/美术设计/书法类（综合分1）", 0.5, 0.5),
        /** 播音与主持 → 70/30（文化为主）。 */
        ART_70_30("播音与主持类（综合分2）", 0.7, 0.3);

        public final String label;
        public final double cultureRatio;
        public final double professionalRatio;

        ArtGroup(String label, double cultureRatio, double professionalRatio) {
            this.label = label;
            this.cultureRatio = cultureRatio;
            this.professionalRatio = professionalRatio;
        }
    }

    @Data
    public static class CompositeScoreResult {
        private String candidateType;
        private String category;
        /** 用户选择的统考类别（艺术）或首选科目（体育），便于前端原样回显。 */
        private String selectedCategory;
        private double cultureRatio;
        private double professionalRatio;
        private int cultureScore;
        private int professionalScore;
        private double professionalScale;
        /** 体育类专有：本次计算使用的本科文化控线（物理 300 / 历史 310 默认）。 */
        private Integer cultureBenkeLine;
        private double cultureWeighted;
        private double professionalWeighted;
        private double score;
        private String formula;
    }
}
