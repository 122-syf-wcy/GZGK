package com.gzly.algorithm;

import com.gzly.service.VolunteerService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 志愿排序引擎。
 *
 * <p>finalScore 公式（对齐需求文档第 12 节）：</p>
 * <pre>
 * finalScore =
 *     chanceScore         * chanceCoef            (default 0.35)
 *   + majorMatchScore     * majorWeight
 *   + schoolMatchScore    * schoolWeight
 *   + cityMatchScore      * cityWeight
 *   + employmentScore     * employmentWeight
 *   + confidenceScore     * confidenceCoef        (default 0.10)
 *   - riskPenalty         * riskCoef              (default 0.10)
 *   - tuitionPenalty      * 0.10
 *   - volatilityPenalty   * 0.10
 * </pre>
 *
 * <p>整体顺序固定为 冲→稳→保→垫，不随 strategyMode 变化；strategyMode 仅影响梯度内部
 * 各分量乘子（保守型加重 chance/confidence/risk，冲刺型由权重工厂在 major/school/city
 * 上加权，均衡型保持默认）。所有 raw 分量都标准化到 0-100 区间，避免量级失衡。</p>
 */
@Component
public class VolunteerSortEngine {

    private static final Map<String, Integer> GRADIENT_ORDER = Map.of("冲", 0, "稳", 1, "保", 2, "垫", 3);

    /** 保守型/冲刺型/均衡型 chance / confidence / risk 系数，覆盖默认 0.35 / 0.10 / 0.10。 */
    private static final double CHANCE_COEF_DEFAULT = 0.35D;
    private static final double CONFIDENCE_COEF_DEFAULT = 0.10D;
    private static final double RISK_COEF_DEFAULT = 0.10D;

    private static final double CHANCE_COEF_CONSERVATIVE = 0.45D;
    private static final double CONFIDENCE_COEF_CONSERVATIVE = 0.18D;
    private static final double RISK_COEF_CONSERVATIVE = 0.18D;

    public List<VolunteerService.VolunteerItem> sort(List<VolunteerService.VolunteerItem> items) {
        return sort(items, PreferenceWeights.defaults());
    }

    public List<VolunteerService.VolunteerItem> sort(List<VolunteerService.VolunteerItem> items,
                                                     PreferenceWeights weights) {
        if (items == null) return List.of();
        PreferenceWeights w = weights == null ? PreferenceWeights.defaults() : weights.normalized();
        List<VolunteerService.VolunteerItem> sorted = items.stream()
                .sorted(Comparator
                        .comparingInt((VolunteerService.VolunteerItem item) -> GRADIENT_ORDER.getOrDefault(item.getGradient(), 9))
                        .thenComparing(Comparator.comparingDouble((VolunteerService.VolunteerItem item) -> finalScore(item, w)).reversed()))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setIndex(i + 1);
        }
        return sorted;
    }

    public double finalScore(VolunteerService.VolunteerItem item) {
        return finalScore(item, PreferenceWeights.defaults());
    }

    /**
     * 计算 finalScore（副作用：写回 item.recommendationScore）。
     */
    public double finalScore(VolunteerService.VolunteerItem item, PreferenceWeights weights) {
        if (item == null) return 0;
        PreferenceWeights w = weights == null ? PreferenceWeights.defaults() : weights;

        boolean conservative = "保守型".equals(w.getStrategyMode());
        double chanceCoef = conservative ? CHANCE_COEF_CONSERVATIVE : CHANCE_COEF_DEFAULT;
        double confidenceCoef = conservative ? CONFIDENCE_COEF_CONSERVATIVE : CONFIDENCE_COEF_DEFAULT;
        double riskCoef = conservative ? RISK_COEF_CONSERVATIVE : RISK_COEF_DEFAULT;

        double chance        = clamp01_100(item.getChanceScore())              * chanceCoef;
        double major         = majorMatchRawScore(item)                         * w.getMajorPriority();
        double school        = schoolMatchRawScore(item)                        * w.getSchoolPriority();
        double city          = cityMatchRawScore(item)                          * w.getCityPriority();
        double employment    = employmentRawScore(item)                         * w.getEmploymentPriority();
        double confidence    = clamp01_100((int) Math.round(item.getDataConfidence())) * confidenceCoef;

        double riskPenalty       = riskRawPenalty(item.getRiskLevel())          * riskCoef;
        double tuitionPenalty    = tuitionRawPenalty(item)                      * 0.10D;
        double volatilityPenalty = volatilityRawPenalty(item.getPlanTrend())    * 0.10D;

        double score = chance + major + school + city + employment + confidence
                - riskPenalty - tuitionPenalty - volatilityPenalty;
        item.setRecommendationScore((int) Math.round(score));
        return score;
    }

    private double majorMatchRawScore(VolunteerService.VolunteerItem item) {
        if (containsPreferredSignal(item.getMatchTag(), "专业")) return 100D;
        return clamp01_100(item.getMatchScore());
    }

    private double schoolMatchRawScore(VolunteerService.VolunteerItem item) {
        if (hasEliteSignal(item)) return 100D;
        if (item.getTags() != null && item.getTags().stream().anyMatch(t -> t != null && (t.contains("省重点") || t.contains("一本")))) {
            return 70D;
        }
        return 40D;
    }

    private double cityMatchRawScore(VolunteerService.VolunteerItem item) {
        return containsPreferredSignal(item.getMatchTag(), "地区") ? 100D : 0D;
    }

    private double employmentRawScore(VolunteerService.VolunteerItem item) {
        return isPracticalMajor(item.getMajorName()) ? 80D : 40D;
    }

    private double riskRawPenalty(String riskLevel) {
        if ("较高".equals(riskLevel) || "高".equals(riskLevel)) return 60D;
        if ("中等".equals(riskLevel)) return 25D;
        return 0D;
    }

    private double tuitionRawPenalty(VolunteerService.VolunteerItem item) {
        boolean costly = contains(item.getMajorName(), "中外") || contains(item.getSchoolNature(), "民办");
        return costly ? 30D : 0D;
    }

    private double volatilityRawPenalty(String planTrend) {
        return "缩招".equals(planTrend) ? 30D : 0D;
    }

    private double clamp01_100(int value) {
        return Math.max(0D, Math.min(100D, value));
    }

    private boolean containsPreferredSignal(String text, String key) {
        return text != null && text.contains(key);
    }

    private boolean hasEliteSignal(VolunteerService.VolunteerItem item) {
        return item.getTags() != null
                && item.getTags().stream().anyMatch(t -> t != null && (t.contains("985") || t.contains("211") || t.contains("双一流")));
    }

    private boolean isPracticalMajor(String major) {
        return contains(major, "计算机") || contains(major, "软件") || contains(major, "电子")
                || contains(major, "电气") || contains(major, "自动化") || contains(major, "医学")
                || contains(major, "师范") || contains(major, "法学");
    }

    private boolean contains(String text, String key) {
        return text != null && key != null && text.contains(key);
    }

    /**
     * 用户偏好权重。majorPriority + schoolPriority + cityPriority + employmentPriority 通常归一化到 1.0，
     * 但调用方未必精确传 1.0，{@link #normalized()} 会做兜底归一化。
     *
     * <p>strategyMode 不影响整体梯度顺序（始终冲→稳→保→垫），仅在 finalScore 计算时影响
     * chance/confidence/risk 系数；冲刺型偏好通过 fromStrategyAndProfile 在 major/school/city
     * 上加权达成。</p>
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PreferenceWeights {
        /** 专业偏好权重，默认 0.35。 */
        private double majorPriority = 0.35D;
        /** 学校层次权重，默认 0.25。 */
        private double schoolPriority = 0.25D;
        /** 城市偏好权重，默认 0.15。 */
        private double cityPriority = 0.15D;
        /** 就业偏好权重，默认 0.25。 */
        private double employmentPriority = 0.25D;
        /** 策略模式：保守型/均衡型/冲刺型；保守型放大 chance/confidence/risk 系数。 */
        private String strategyMode = "均衡型";

        public PreferenceWeights(double major, double school, double city, double employment) {
            this.majorPriority = major;
            this.schoolPriority = school;
            this.cityPriority = city;
            this.employmentPriority = employment;
        }

        public static PreferenceWeights defaults() {
            return new PreferenceWeights(0.35D, 0.25D, 0.15D, 0.25D);
        }

        public static PreferenceWeights of(Double major, Double school, Double city, Double employment) {
            PreferenceWeights w = defaults();
            if (major != null && major >= 0)        w.setMajorPriority(major);
            if (school != null && school >= 0)      w.setSchoolPriority(school);
            if (city != null && city >= 0)          w.setCityPriority(city);
            if (employment != null && employment >= 0) w.setEmploymentPriority(employment);
            return w.normalized();
        }

        /**
         * 根据策略模式 + 用户决策优先级 + 用户传入的偏好权重构建排序权重。
         *
         * @param strategyMode      "保守型" / "均衡型" / "冲刺型"
         * @param decisionPriority  "学校优先" / "专业优先"，缺失视为均衡
         * @param careerGoal        "就业优先" / "升学优先" / "城市机会优先"
         * @param majorPriority     用户传入专业偏好权重，缺失用 0.35
         * @param schoolPriority    用户传入学校偏好权重，缺失用 0.25
         * @param cityPriority      用户传入城市偏好权重，缺失用 0.15
         * @param employmentPriority 用户传入就业偏好权重，缺失用 0.25
         */
        public static PreferenceWeights fromStrategyAndProfile(String strategyMode,
                                                               String decisionPriority,
                                                               String careerGoal,
                                                               Double majorPriority,
                                                               Double schoolPriority,
                                                               Double cityPriority,
                                                               Double employmentPriority) {
            double major = majorPriority == null || majorPriority < 0 ? 0.35D : majorPriority;
            double school = schoolPriority == null || schoolPriority < 0 ? 0.25D : schoolPriority;
            double city = cityPriority == null || cityPriority < 0 ? 0.15D : cityPriority;
            double employment = employmentPriority == null || employmentPriority < 0 ? 0.25D : employmentPriority;

            // decisionPriority 微调：学校优先放大 school 权重，专业优先放大 major 权重
            if ("学校优先".equals(decisionPriority)) {
                school += 0.08D;
                major = Math.max(0.0D, major - 0.08D);
            } else if ("专业优先".equals(decisionPriority)) {
                major += 0.08D;
                school = Math.max(0.0D, school - 0.08D);
            }

            // careerGoal 微调：就业优先放大 employment；升学优先放大 school；城市机会优先放大 city
            if ("就业优先".equals(careerGoal)) {
                employment += 0.05D;
            } else if ("升学优先".equals(careerGoal)) {
                school += 0.05D;
            } else if ("城市机会优先".equals(careerGoal)) {
                city += 0.05D;
            }

            // strategyMode 微调（不改整体顺序，仅调梯度内权重）
            if ("冲刺型".equals(strategyMode)) {
                // 冲档加重 major/school/city，减重 employment
                major += 0.10D;
                school += 0.05D;
                city += 0.05D;
                employment = Math.max(0.0D, employment - 0.20D);
            }

            PreferenceWeights w = new PreferenceWeights(major, school, city, employment).normalized();
            w.setStrategyMode(strategyMode == null || strategyMode.isBlank() ? "均衡型" : strategyMode);
            return w;
        }

        /**
         * 把四个非负权重按总和归一化到 1.0；总和为 0 时回到默认值。保留 strategyMode 字段。
         */
        public PreferenceWeights normalized() {
            double sum = Math.max(0, majorPriority) + Math.max(0, schoolPriority)
                    + Math.max(0, cityPriority) + Math.max(0, employmentPriority);
            if (sum <= 0) {
                PreferenceWeights d = defaults();
                d.setStrategyMode(strategyMode);
                return d;
            }
            PreferenceWeights w = new PreferenceWeights(
                    majorPriority / sum,
                    schoolPriority / sum,
                    cityPriority / sum,
                    employmentPriority / sum);
            w.setStrategyMode(strategyMode);
            return w;
        }
    }
}
