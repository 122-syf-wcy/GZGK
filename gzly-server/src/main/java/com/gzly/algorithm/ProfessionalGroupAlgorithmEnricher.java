package com.gzly.algorithm;

import com.gzly.service.VolunteerService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 院校专业组链路的共享算法接入层（阶段 2 并线，docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md）。
 *
 * <p>此前专业组省份（川/鄂/皖/桂/琼/滇/豫）没有任何概率模型：chanceScore 恒为 0、
 * riskLevel 写死"需复核"、排序只按历史调档位次、recommendationScore 是数据质量分。
 * 本类把贵州链路已验证的四层共享算法接给专业组条目：</p>
 * <ol>
 *   <li>机会指数：FeatureBuildEngine 位次特征 + FallbackRulePredictionEngine 规则预测
 *       （直接消费条目上已加载的 historyRecords，不产生额外查询）；</li>
 *   <li>意向匹配：preferredMajors 对组内专业/组名、preferredRegions 对省份/城市/校名
 *       （精确包含语义，与贵州链路一致；专业大类模糊映射暂为贵州独有，阶段 3 再共享）；</li>
 *   <li>排序：VolunteerSortEngine 按策略权重产出 finalScore 并写回 recommendationScore/index；</li>
 *   <li>诊断 + 整表安全度：VolunteerDiagnosisEngine 13 维诊断；保/垫尾部经
 *       ProbabilityCalibration 校准后跑 PortfolioMonteCarloSimulator 整表命中率。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ProfessionalGroupAlgorithmEnricher {

    /** 与 VolunteerService.PORTFOLIO_SAFETY_THRESHOLD 同口径。 */
    private static final double PORTFOLIO_SAFETY_THRESHOLD = 98.0;
    private static final int SAFE_TAIL_LIMIT = 18;

    private final FeatureBuildEngine featureBuildEngine;
    private final FallbackRulePredictionEngine fallbackRulePredictionEngine;
    private final VolunteerSortEngine volunteerSortEngine;
    private final VolunteerDiagnosisEngine volunteerDiagnosisEngine;
    private final com.gzly.service.RankNormalizationService rankNormalizationService;
    private final com.gzly.config.FallbackPredictionProperties fallbackProps;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 兼容旧签名：3+1+2 省份按首选科目推导轨道。 */
    public Outcome enrich(List<VolunteerService.VolunteerItem> items,
                          VolunteerService.GenerateRequest req,
                          int studentRank,
                          int policyTargetCount) {
        return enrich(items, req, studentRank, policyTargetCount,
                com.gzly.algorithm.core.ThreeOneTwoSubjectMatcher.toTrackLabel(
                        req == null ? null : req.getFirstSubject()));
    }

    /**
     * 对已选出的专业组条目执行共享算法层，返回重排后的列表与方案级产物。
     * 入参 items 需已填充 historyRecords / historyMinRank / gradient；
     * subjectType 为调用方解析好的科类轨道（3+1+2 物理类/历史类，3+3 综合），
     * 用于等效位次换算的考生总数查询。
     */
    public Outcome enrich(List<VolunteerService.VolunteerItem> items,
                          VolunteerService.GenerateRequest req,
                          int studentRank,
                          int policyTargetCount,
                          String subjectType) {
        List<VolunteerService.VolunteerItem> safeItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        String provinceCode = req == null ? null : req.getProvinceCode();
        // 「省 × 批次」参数覆盖（policy_rule_config.chance_params_json）：河南 σ 上调等按批次生效
        com.gzly.config.FallbackPredictionProperties.Overrides overrides =
                com.gzly.config.FallbackPredictionProperties.Overrides.parse(
                        req == null ? null : req.getPolicyChanceParamsJson(), objectMapper);

        int reclassified = 0;
        for (VolunteerService.VolunteerItem item : safeItems) {
            applyRulePrediction(item, studentRank, provinceCode, subjectType, overrides);
            if (reclassifyGradientByProbability(item)) {
                reclassified++;
            }
        }
        applyPreferenceMatch(safeItems, req);

        VolunteerSortEngine.PreferenceWeights weights = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                text(req == null ? null : req.getStrategyMode(), "均衡型"),
                req == null ? null : req.getDecisionPriority(),
                req == null ? null : req.getCareerGoal(),
                null, null, null, null);
        List<VolunteerService.VolunteerItem> sorted = volunteerSortEngine.sort(safeItems, weights);

        VolunteerDiagnosisEngine.DiagnosisContext ctx = new VolunteerDiagnosisEngine.DiagnosisContext();
        ctx.setDislikedMajors(req == null || req.getDislikedMajors() == null ? List.of() : req.getDislikedMajors());
        ctx.setRiskPreference(weights.getStrategyMode());
        Map<String, Object> diagnosis = volunteerDiagnosisEngine.diagnose(sorted, policyTargetCount, ctx);

        PortfolioSafety safety = assessPortfolioSafety(sorted);

        Outcome outcome = new Outcome();
        outcome.setItems(sorted);
        outcome.setDiagnosis(diagnosis);
        outcome.setPortfolioSafety(safety);
        outcome.setGradientReclassifiedCount(reclassified);
        return outcome;
    }

    // ══════════════════ 1. 机会指数 ══════════════════

    /**
     * 与贵州链路 applyRulePrediction 同构：等效位次归一 → 位次特征 → 规则预测 → 写回条目。
     * 差异仅在数据来源——专业组条目的历史序列来自已加载的 historyRecords（组级调档线），
     * referenceRank 取最近一年调档位次（即 historyMinRank）。
     */
    private void applyRulePrediction(VolunteerService.VolunteerItem item, int studentRank,
                                     String provinceCode, String subjectType,
                                     com.gzly.config.FallbackPredictionProperties.Overrides overrides) {
        List<VolunteerService.HistoryRecord> records = item.getHistoryRecords() == null
                ? List.of() : item.getHistoryRecords();
        int targetYear = records.stream()
                .map(VolunteerService.HistoryRecord::getYear)
                .filter(y -> y != null && y > 0)
                .max(Integer::compareTo)
                .orElse(0);

        List<Integer> minRanks = new ArrayList<>();
        List<Integer> planCounts = new ArrayList<>();
        for (VolunteerService.HistoryRecord record : records) {
            if (record == null) continue;
            if (record.getMinRank() != null && record.getMinRank() > 0) {
                minRanks.add(normalizeRank(record.getMinRank(), record.getYear(), targetYear, provinceCode, subjectType));
            }
            if (record.getPlanCount() != null && record.getPlanCount() > 0) {
                planCounts.add(record.getPlanCount());
            }
        }
        if (minRanks.isEmpty() && item.getHistoryMinRank() > 0) {
            minRanks.add(item.getHistoryMinRank());
        }

        FeatureBuildEngine.RankFeature feature = featureBuildEngine.buildRankFeatures(minRanks, planCounts);
        int referenceRank = item.getHistoryMinRank() > 0
                ? item.getHistoryMinRank()
                : (feature.getLatestMinRank() != null ? feature.getLatestMinRank() : 0);

        FallbackRulePredictionEngine.Prediction p = fallbackRulePredictionEngine.predictWithOverrides(
                overrides,
                Math.max(1, studentRank),
                referenceRank,
                feature.getRankVolatility3y(),
                feature.getPlanChangeRate(),
                feature.getDataConfidence(),
                item.getHotTrendScore());

        item.setChanceScore(p.getChanceScore());
        item.setChanceLevel(p.getChanceLevel());
        item.setRiskLevel(p.getRiskLevel());
        item.setConfidenceLevel(p.getConfidenceLevel());
        item.setDataConfidence(p.getDataConfidence());
        item.setPredictedMinRank(p.getPredictedMinRank());
        item.setRankDiff(p.getRankDiff());
        item.setRankVolatility3y(feature.getRankVolatility3y());
        item.setPlanChangeRate(feature.getPlanChangeRate());
        // 风险色与 ML 链路口径一致：>=75 green / >=50 yellow / 其余 red
        item.setRiskColor(p.getChanceScore() >= 75 ? "green" : p.getChanceScore() >= 50 ? "yellow" : "red");

        // 展示层校准概率：等渗校准后叠加退档折减——专业组进档后仍有组内分配环节，
        // P(录取) = P(投档) × (1 − P(退档|已投档))。折减先验按投档比例配置（10.3），
        // 支持 chance_params_json 按省覆盖（1:1 投档省可下调）。
        double calibrated = ProbabilityCalibration.fromChanceScore(p.getChanceScore());
        double withdrawPrior = Math.max(0D, Math.min(0.5D, fallbackProps.effectiveGroupWithdrawPrior(overrides)));
        item.setCalibratedProbability(Math.round(calibrated * (1 - withdrawPrior) * 1000d) / 10d);
        String withdrawNote = String.format(Locale.ROOT,
                "院校专业组进档后仍需组内专业分配，已按退档先验%.0f%%折减录取参考概率；服从组内调剂可显著降低退档风险。",
                withdrawPrior * 100);
        item.setRiskReason(item.getRiskReason() == null || item.getRiskReason().isBlank()
                ? withdrawNote
                : item.getRiskReason() + " " + withdrawNote);
    }

    /**
     * 梯度归属改由校准概率决定（10.4）：位次比例区间只承担 DB 预筛，
     * 最终档位按校准后投档概率带划定，保证"保"档条目的概率口径与展示一致。
     * 概率带：垫 ≥0.95、保 [0.85,0.95)、稳 [0.55,0.85)、其余为冲。
     *
     * @return 档位是否发生变化（用于统计与预设数量的偏离度，调用方写入 metrics 与说明文案）
     */
    private boolean reclassifyGradientByProbability(VolunteerService.VolunteerItem item) {
        if (item.getChanceScore() <= 0) {
            return false;
        }
        double p = ProbabilityCalibration.fromChanceScore(item.getChanceScore());
        String gradient;
        if (p >= 0.95) {
            gradient = "垫";
        } else if (p >= 0.85) {
            gradient = "保";
        } else if (p >= 0.55) {
            gradient = "稳";
        } else {
            gradient = "冲";
        }
        boolean moved = !gradient.equals(item.getGradient());
        item.setGradient(gradient);
        return moved;
    }

    /** 跨年位次归一；考生总数缺失时原样返回（不造数）。 */
    private int normalizeRank(int rank, Integer fromYear, int targetYear, String provinceCode, String subjectType) {
        if (rankNormalizationService == null || fromYear == null || fromYear <= 0 || targetYear <= 0) {
            return rank;
        }
        return rankNormalizationService.normalize(rank, fromYear, targetYear, provinceCode, subjectType);
    }

    // ══════════════════ 2. 意向匹配 ══════════════════

    private void applyPreferenceMatch(List<VolunteerService.VolunteerItem> items,
                                      VolunteerService.GenerateRequest req) {
        List<String> prefMajors = normalize(req == null ? null : req.getPreferredMajors());
        List<String> prefRegions = normalize(req == null ? null : req.getPreferredRegions());
        if (prefMajors.isEmpty() && prefRegions.isEmpty()) {
            return;
        }
        for (VolunteerService.VolunteerItem item : items) {
            boolean majorMatch = false;
            boolean regionMatch = false;
            int score = 0;

            if (!prefMajors.isEmpty()) {
                String haystack = (text(item.getGroupName(), "") + " " + text(item.getMajorName(), "") + " "
                        + String.join(" ", item.getGroupMajors() == null ? List.of() : item.getGroupMajors()))
                        .toLowerCase(Locale.ROOT);
                for (String pref : prefMajors) {
                    if (haystack.contains(pref.toLowerCase(Locale.ROOT))) {
                        majorMatch = true;
                        score += 60;
                        break;
                    }
                }
            }
            if (!prefRegions.isEmpty()) {
                String province = text(item.getProvince(), "");
                String city = text(item.getCity(), "");
                String school = text(item.getUniversityName(), "");
                for (String pref : prefRegions) {
                    if (province.contains(pref) || city.contains(pref) || school.contains(pref)) {
                        regionMatch = true;
                        score += 40;
                        break;
                    }
                }
            }

            if (majorMatch && regionMatch) {
                item.setMatchTag("双匹配");
            } else if (majorMatch) {
                item.setMatchTag("专业匹配");
            } else if (regionMatch) {
                item.setMatchTag("地区匹配");
            } else {
                item.setMatchTag("");
            }
            item.setMatchScore(Math.min(score, 100));
        }
    }

    // ══════════════════ 4. 整表安全度 ══════════════════

    /** 与 VolunteerService.assessPortfolioSafety 同口径的专业组版本。 */
    private PortfolioSafety assessPortfolioSafety(List<VolunteerService.VolunteerItem> items) {
        if (items == null || items.isEmpty()) {
            return new PortfolioSafety(0, "数据不足", "志愿表为空，无法评估整表安全度。", 0);
        }
        List<VolunteerService.VolunteerItem> safeTail = items.stream()
                .filter(item -> "保".equals(item.getGradient()) || "垫".equals(item.getGradient()))
                .filter(item -> !item.isSpecialTypeFlag())
                .filter(item -> item.getDataConfidenceScore() >= 45 || "高可信".equals(item.getConfidenceLabel()))
                .sorted((a, b) -> Integer.compare(b.getChanceScore(), a.getChanceScore()))
                .limit(SAFE_TAIL_LIMIT)
                .toList();
        if (safeTail.isEmpty()) {
            return new PortfolioSafety(0, "需加厚保/兜底",
                    "保/兜底区缺少可用于整表兜底的高可信条目，建议补充计划数清楚、来源可核验的专业组。", 0);
        }
        List<Double> calibratedProbs = new ArrayList<>(safeTail.size());
        for (VolunteerService.VolunteerItem item : safeTail) {
            double p = ProbabilityCalibration.fromChanceScore(item.getChanceScore());
            if ("red".equals(item.getRiskColor())) {
                p = Math.max(0.02, p - 0.05);
            } else if ("green".equals(item.getRiskColor())) {
                p = Math.min(0.97, p + 0.02);
            }
            if (item.getDataConfidenceScore() > 0 && item.getDataConfidenceScore() < 60) {
                p = Math.max(0.02, p - 0.03);
            }
            calibratedProbs.add(p);
        }
        double hitRate = PortfolioMonteCarloSimulator.listHitRate(calibratedProbs);
        double probability = Math.max(0, Math.min(99.9, Math.round(hitRate * 1000d) / 10d));
        String level;
        if (probability >= PORTFOLIO_SAFETY_THRESHOLD) {
            level = "安全垫充足";
        } else if (probability >= 95) {
            level = "安全垫可用";
        } else {
            level = "需加厚保/兜底";
        }
        String note = String.format(Locale.ROOT,
                "按保/兜底区前%d个高可信专业组的校准概率做 %d 次蒙特卡洛仿真，整表至少投出一条的参考频率约%.1f%%；该值仅用于检查列表是否过于激进，不代表录取承诺。",
                safeTail.size(), PortfolioMonteCarloSimulator.DEFAULT_ITERATIONS, probability);
        return new PortfolioSafety(probability, level, note, safeTail.size());
    }

    private List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList();
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record PortfolioSafety(double probability, String level, String note, int safeTailCount) {
    }

    @Data
    public static class Outcome {
        private List<VolunteerService.VolunteerItem> items = List.of();
        private Map<String, Object> diagnosis = Map.of();
        private PortfolioSafety portfolioSafety;
        /** 概率定档相对位次预筛档位发生变化的条数（预设数量偏离的度量）。 */
        private int gradientReclassifiedCount;
    }
}
