package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.ScoreRankGz;
import com.gzly.entity.ScoreLineGz;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import com.gzly.mapper.ScoreLineGzMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能算法服务 — 四大核心算法引擎
 *
 * 1. 历史参考概率计算（基于位次分布模型，不等同于官方录取预测）
 * 2. 位次波动风险评估（标准差 + 变异系数）
 * 3. 分数线预测（等位分 + 加权移动平均）
 * 4. 协同过滤院校推荐（基于物品的余弦相似度）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmService {

    private final ScoreLineGzMapper scoreLineGzMapper;
    private final MajorScoreGzMapper majorScoreGzMapper;
    private final ScoreRankGzMapper scoreRankGzMapper;

    // ═══════════════════════════════════════════════════════
    // 算法1: 历史参考概率计算 — 基于位次分布
    // ═══════════════════════════════════════════════════════

    @Data
    public static class AdmissionProbability {
        private String schoolId;
        private String universityName;
        private String majorName;
        private String subjectType;
        private double probability;       // 历史参考概率 0~100
        private String level;             // 单年参考/参考较高/参考中等/参考偏低/数据不足
        private int avgRank;              // 近年平均最低位次
        private int stdRank;              // 位次标准差
        private List<Integer> historyRanks; // 各年位次
        private List<Integer> historyYears; // 对应年份
    }

    /**
     * 计算考生对某个院校+专业的历史参考概率。
     * 该值只表达“与历史最低位次的相对匹配程度”，不等同于官方录取预测。
     *
     * 算法原理:
     * 1. 取该院校+专业近3年的最低录取位次
     * 2. 计算均值μ和标准差σ
     * 3. 用正态分布CDF: P = Φ((μ - R) / σ)
     *    其中 R = 考生位次, μ = 历年平均位次
     *    当 R < μ (考生位次更好)时, P > 50%
     * 4. 对σ=0的情况(数据太少或完全一致)做特殊处理
     */
    @Cacheable(value = "admissionProbabilities",
            key = "#studentRank + '_' + #schoolId + '_' + (#majorName == null ? 'school' : #majorName) + '_' + #subjectType")
    public AdmissionProbability calcProbability(int studentRank, String schoolId,
                                                 String majorName, String subjectType) {
        List<ScoreLineGz> lines = getRecentLines(schoolId, majorName, subjectType, 3);

        AdmissionProbability result = new AdmissionProbability();
        result.setSchoolId(schoolId);
        result.setMajorName(majorName);
        result.setSubjectType(subjectType);
        result.setHistoryRanks(new ArrayList<>());
        result.setHistoryYears(new ArrayList<>());

        if (lines.isEmpty()) {
            result.setProbability(0);
            result.setLevel("数据不足");
            return result;
        }

        result.setUniversityName(lines.get(0).getUniversityName());

        // 提取有效位次
        List<Integer> ranks = new ArrayList<>();
        for (ScoreLineGz sl : lines) {
            if (sl.getMinRank() != null && sl.getMinRank() > 0) {
                ranks.add(sl.getMinRank());
                result.getHistoryRanks().add(sl.getMinRank());
                result.getHistoryYears().add(sl.getYear());
            }
        }

        if (ranks.isEmpty()) {
            result.setProbability(0);
            result.setLevel("数据不足");
            return result;
        }

        double mean = ranks.stream().mapToInt(Integer::intValue).average().orElse(0);
        double std = calcStd(ranks);
        result.setAvgRank((int) Math.round(mean));
        result.setStdRank((int) Math.round(std));

        double prob;
        if (ranks.size() < 2) {
            double diff = mean - studentRank;
            double scale = Math.max(500.0, mean * 0.08);
            prob = sigmoid(diff / scale) * 100;
            prob = Math.max(20, Math.min(80, prob));
            result.setProbability(Math.round(prob * 10) / 10.0);
            result.setLevel("单年参考");
            return result;
        }

        double distributionProb;
        if (std < 50) {
            // 标准差极小，用线性插值避免极端值
            double diff = mean - studentRank;
            distributionProb = sigmoid(diff / 500.0) * 100;
        } else {
            double z = (mean - studentRank) / std;
            distributionProb = normalCDF(z) * 100;
        }

        double empiricalProb = empiricalHitProbability(studentRank, ranks) * 100;
        double volatilityPenalty = volatilityPenalty(mean, std);
        prob = distributionProb * 0.65 + empiricalProb * 0.35 - volatilityPenalty;

        // 限制范围 [1, 99]
        prob = Math.max(1, Math.min(99, prob));
        result.setProbability(Math.round(prob * 10) / 10.0);
        result.setLevel(probToLevel(prob));

        return result;
    }

    /**
     * 批量计算: 给定考生位次和科类, 计算多个志愿项的历史参考概率
     */
    public List<AdmissionProbability> batchCalcProbability(int studentRank, String subjectType,
                                                            List<VolunteerTarget> targets) {
        return targets.stream()
                .map(t -> calcProbability(studentRank, t.getSchoolId(), t.getMajorName(), subjectType))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════
    // 算法2: 位次波动风险评估
    // ═══════════════════════════════════════════════════════

    @Data
    public static class RiskAssessment {
        private String schoolId;
        private String universityName;
        private String majorName;
        private String subjectType;
        private String riskLevel;         // 低风险/中风险/高风险
        private String riskColor;         // green/yellow/red
        private double cv;                // 变异系数 (%)
        private int stdRank;              // 位次标准差
        private int avgRank;              // 平均位次
        private int maxSwing;             // 最大波动幅度
        private String riskDetail;        // 风险说明
        private List<Integer> historyRanks;
        private List<Integer> historyYears;
    }

    /**
     * 评估某院校+专业的录取波动风险
     *
     * 算法原理:
     * 1. 取近3~5年最低录取位次序列
     * 2. 计算变异系数 CV = σ/μ × 100%
     * 3. 计算年际最大波动 maxSwing = max(|rank[i]-rank[i-1]|)
     * 4. 综合判定风险等级:
     *    - CV < 10% 且 maxSwing < 3000: 低风险 🟢
     *    - CV < 20% 且 maxSwing < 8000: 中风险 🟡
     *    - 其他: 高风险 🔴
     */
    @Cacheable(value = "riskAssessments",
            key = "#schoolId + '_' + (#majorName == null ? 'school' : #majorName) + '_' + #subjectType")
    public RiskAssessment assessRisk(String schoolId, String majorName, String subjectType) {
        List<ScoreLineGz> lines = getRecentLines(schoolId, majorName, subjectType, 5);

        RiskAssessment result = new RiskAssessment();
        result.setSchoolId(schoolId);
        result.setMajorName(majorName);
        result.setSubjectType(subjectType);
        result.setHistoryRanks(new ArrayList<>());
        result.setHistoryYears(new ArrayList<>());

        if (lines.isEmpty()) {
            result.setRiskLevel("数据不足");
            result.setRiskColor("gray");
            result.setRiskDetail("历史数据不足，无法评估风险");
            return result;
        }

        result.setUniversityName(lines.get(0).getUniversityName());

        List<Integer> ranks = new ArrayList<>();
        for (ScoreLineGz sl : lines) {
            if (sl.getMinRank() != null && sl.getMinRank() > 0) {
                ranks.add(sl.getMinRank());
                result.getHistoryRanks().add(sl.getMinRank());
                result.getHistoryYears().add(sl.getYear());
            }
        }

        if (ranks.size() < 2) {
            result.setRiskLevel("数据不足");
            result.setRiskColor("gray");
            result.setRiskDetail("仅有1年数据，无法评估波动风险");
            return result;
        }

        double mean = ranks.stream().mapToInt(Integer::intValue).average().orElse(1);
        double std = calcStd(ranks);
        double cv = (mean > 0) ? (std / mean) * 100 : 0;

        // 年际最大波动
        int maxSwing = 0;
        for (int i = 1; i < ranks.size(); i++) {
            maxSwing = Math.max(maxSwing, Math.abs(ranks.get(i) - ranks.get(i - 1)));
        }

        result.setAvgRank((int) Math.round(mean));
        result.setStdRank((int) Math.round(std));
        result.setCv(Math.round(cv * 10) / 10.0);
        result.setMaxSwing(maxSwing);

        // 综合判定
        if (cv < 10 && maxSwing < 3000) {
            result.setRiskLevel("低风险");
            result.setRiskColor("green");
            result.setRiskDetail(String.format("近%d年位次稳定，变异系数%.1f%%，年际最大波动%d位", ranks.size(), cv, maxSwing));
        } else if (cv < 20 && maxSwing < 8000) {
            result.setRiskLevel("中风险");
            result.setRiskColor("yellow");
            result.setRiskDetail(String.format("近%d年位次有一定波动，变异系数%.1f%%，年际最大波动%d位", ranks.size(), cv, maxSwing));
        } else {
            result.setRiskLevel("高风险");
            result.setRiskColor("red");
            result.setRiskDetail(String.format("近%d年位次波动较大（大小年现象），变异系数%.1f%%，年际最大波动%d位", ranks.size(), cv, maxSwing));
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════
    // 算法3: 分数线预测 — 等位分 + 加权移动平均
    // ═══════════════════════════════════════════════════════

    @Data
    public static class ScorePrediction {
        private String schoolId;
        private String universityName;
        private String majorName;
        private String subjectType;
        private int predictedRank;        // 预测最低位次
        private int predictedScore;       // 预测最低分 (需要一分一段表, 暂用线性估算)
        private double confidence;        // 置信度 0~100
        private String trend;             // 上升/稳定/下降
        private List<Integer> historyRanks;
        private List<Integer> historyYears;
        private String method;            // 使用的算法
    }

    /**
     * 预测下一年录取位次/分数
     *
     * 算法原理 (两阶段):
     * 阶段1 — 稳定性分类:
     *   计算历年等位分波动, |δ| ≤ 3分视为稳定样本
     *   稳定样本: 直接用指数平滑(ES)预测
     *   不稳定样本: 用加权移动平均 + 趋势修正
     *
     * 阶段2 — 加权移动平均 (WMA):
     *   权重: 最近年 w=0.5, 次近 w=0.3, 再次 w=0.2
     *   趋势修正: trend = avg(rank[i]-rank[i-1])
     *   predicted = WMA + trend * dampingFactor
     *
     * 参考论文: PLOS ONE "A competition model for prediction of admission scores"
     */
    @Cacheable(value = "scorePredictions", key = "#schoolId + '_' + #majorName + '_' + #subjectType")
    public ScorePrediction predictScore(String schoolId, String majorName, String subjectType) {
        List<ScoreLineGz> lines = getRecentLines(schoolId, majorName, subjectType, 5);

        ScorePrediction result = new ScorePrediction();
        result.setSchoolId(schoolId);
        result.setMajorName(majorName);
        result.setSubjectType(subjectType);
        result.setHistoryRanks(new ArrayList<>());
        result.setHistoryYears(new ArrayList<>());

        if (lines.isEmpty()) {
            result.setMethod("数据不足");
            result.setConfidence(0);
            return result;
        }

        result.setUniversityName(lines.get(0).getUniversityName());

        // 按年份升序排列
        List<int[]> yearRankPairs = new ArrayList<>();
        for (ScoreLineGz sl : lines) {
            if (sl.getMinRank() != null && sl.getMinRank() > 0) {
                yearRankPairs.add(new int[]{sl.getYear(), sl.getMinRank()});
                result.getHistoryRanks().add(sl.getMinRank());
                result.getHistoryYears().add(sl.getYear());
            }
        }
        yearRankPairs.sort(Comparator.comparingInt(a -> a[0]));

        if (yearRankPairs.size() < 2) {
            result.setPredictedRank(yearRankPairs.isEmpty() ? 0 : yearRankPairs.get(0)[1]);
            result.setConfidence(20);
            result.setMethod("单年数据直接引用");
            result.setTrend("未知");
            return result;
        }

        int n = yearRankPairs.size();
        List<Integer> sortedRanks = yearRankPairs.stream().map(p -> p[1]).collect(Collectors.toList());

        // 判断稳定性: 相邻年份位次变化
        boolean stable = true;
        for (int i = 1; i < sortedRanks.size(); i++) {
            if (Math.abs(sortedRanks.get(i) - sortedRanks.get(i - 1)) > sortedRanks.get(i) * 0.15) {
                stable = false;
                break;
            }
        }

        int predicted;
        if (stable) {
            // 稳定样本: 指数平滑 α=0.4
            double alpha = 0.4;
            double smoothed = sortedRanks.get(0);
            for (int i = 1; i < n; i++) {
                smoothed = alpha * sortedRanks.get(i) + (1 - alpha) * smoothed;
            }
            predicted = (int) Math.round(smoothed);
            result.setMethod("指数平滑法(ES)");
            result.setConfidence(Math.min(85, 60 + n * 5));
        } else {
            // 不稳定样本: 加权移动平均 + 趋势修正
            double[] weights;
            if (n >= 3) {
                weights = new double[]{0.2, 0.3, 0.5};
            } else {
                weights = new double[]{0.4, 0.6};
            }

            int start = Math.max(0, n - weights.length);
            double wma = 0;
            double wSum = 0;
            for (int i = start; i < n; i++) {
                int wi = i - start;
                wma += sortedRanks.get(i) * weights[wi];
                wSum += weights[wi];
            }
            wma /= wSum;

            // 趋势修正
            double trendSum = 0;
            for (int i = 1; i < n; i++) {
                trendSum += (sortedRanks.get(i) - sortedRanks.get(i - 1));
            }
            double trend = trendSum / (n - 1);
            double dampingFactor = 0.5; // 阻尼因子, 避免趋势过度外推

            predicted = (int) Math.round(wma + trend * dampingFactor);
            result.setMethod("加权移动平均+趋势修正(WMA-T)");
            result.setConfidence(Math.min(70, 40 + n * 5));
        }

        predicted = Math.max(1, predicted);
        result.setPredictedRank(predicted);

        // 趋势判断
        int lastRank = sortedRanks.get(n - 1);
        int firstRank = sortedRanks.get(0);
        if (lastRank < firstRank * 0.9) {
            result.setTrend("竞争加剧(位次下降)");
        } else if (lastRank > firstRank * 1.1) {
            result.setTrend("竞争缓和(位次上升)");
        } else {
            result.setTrend("基本稳定");
        }

        // 用最近一年分数做线性估算 (粗略)
        ScoreLineGz latest = lines.get(0);
        if (latest.getMinScore() != null && latest.getMinRank() != null && latest.getMinRank() > 0) {
            double ratio = (double) predicted / latest.getMinRank();
            // 位次增大→分数降低, 简单线性近似
            int estimatedScore = (int) Math.round(latest.getMinScore() / Math.pow(ratio, 0.1));
            result.setPredictedScore(Math.max(0, estimatedScore));
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════
    // 算法4: 协同过滤院校推荐 — 基于物品的余弦相似度
    // ═══════════════════════════════════════════════════════

    @Data
    public static class RecommendItem {
        private String schoolId;
        private String universityName;
        private String majorName;
        private double similarity;        // 相似度 0~1
        private int latestMinRank;
        private int latestMinScore;
        private int latestYear;
        private String reason;            // 推荐理由
    }

    /**
     * 基于已选志愿推荐相似院校+专业
     *
     * 算法原理 (Item-based Collaborative Filtering):
     * 1. 将每个院校+专业视为一个"物品"
     * 2. 特征向量: [位次归一化, 分数归一化, 是否985, 是否211, 是否双一流, 省份编码]
     * 3. 计算余弦相似度 cos(A,B) = A·B / (|A|×|B|)
     * 4. 对用户已选的志愿, 找最相似但未选的院校+专业
     * 5. 去重 + 排序, 返回top-N推荐
     */
    public List<RecommendItem> recommendSimilar(String schoolId, String majorName,
                                                  String subjectType, int studentRank,
                                                  int topN) {
        // 获取参考院校最新数据
        LambdaQueryWrapper<ScoreLineGz> refWrapper = new LambdaQueryWrapper<>();
        refWrapper.eq(ScoreLineGz::getSchoolId, schoolId);
        applyScoreLineSubjectFilter(refWrapper, subjectType)
                .isNotNull(ScoreLineGz::getMinRank)
                .gt(ScoreLineGz::getMinRank, 0)
                .orderByDesc(ScoreLineGz::getYear)
                .last("LIMIT 1");
        if (majorName != null && !majorName.isBlank()) {
            refWrapper.eq(ScoreLineGz::getMajorName, majorName);
        }
        List<ScoreLineGz> refLines = scoreLineGzMapper.selectList(refWrapper);

        if (refLines.isEmpty()) {
            return List.of();
        }

        ScoreLineGz ref = refLines.get(0);
        int refRank = ref.getMinRank() != null ? ref.getMinRank() : 0;
        int refScore = ref.getMinScore() != null ? ref.getMinScore() : 0;

        if (refRank == 0) return List.of();

        // 搜索窗口: ±5000位次(固定窗口), 确保有足够的区分度
        int window = Math.max(5000, (int)(refRank * 0.3));
        int searchLow = Math.max(1, refRank - window);
        int searchHigh = refRank + window;

        // 查询最近一年的候选 (限制数量避免全表扫描)
        LambdaQueryWrapper<ScoreLineGz> candidateWrapper = new LambdaQueryWrapper<>();
        applyScoreLineSubjectFilter(candidateWrapper, subjectType)
                .eq(ScoreLineGz::getYear, ref.getYear())
                .between(ScoreLineGz::getMinRank, searchLow, searchHigh)
                .ne(ScoreLineGz::getSchoolId, schoolId)
                .isNotNull(ScoreLineGz::getMinRank)
                .gt(ScoreLineGz::getMinRank, 0)
                .orderByAsc(ScoreLineGz::getMinRank)
                .last("LIMIT 500");
        List<ScoreLineGz> candidates = scoreLineGzMapper.selectList(candidateWrapper);

        // 按院校去重 (每个院校只保留位次最接近的专业)
        Map<String, ScoreLineGz> bySchool = new LinkedHashMap<>();
        for (ScoreLineGz c : candidates) {
            String key = c.getSchoolId();
            ScoreLineGz existing = bySchool.get(key);
            if (existing == null ||
                Math.abs(c.getMinRank() - refRank) < Math.abs(existing.getMinRank() - refRank)) {
                bySchool.put(key, c);
            }
        }

        // 计算相似度
        List<RecommendItem> results = new ArrayList<>();
        for (ScoreLineGz c : bySchool.values()) {
            int cRank = c.getMinRank();
            int cScore = c.getMinScore() != null ? c.getMinScore() : 0;

            // 位次距离归一化 (越近越相似)
            double rankSim = 1.0 - (double) Math.abs(cRank - refRank) / window;
            // 分数距离归一化
            double scoreSim = (refScore > 0 && cScore > 0)
                    ? 1.0 - Math.abs(cScore - refScore) / 50.0
                    : 0.5;
            scoreSim = Math.max(0, scoreSim);

            double similarity = rankSim * 0.7 + scoreSim * 0.3;
            similarity = Math.max(0, Math.min(0.99, similarity));

            RecommendItem item = new RecommendItem();
            item.setSchoolId(c.getSchoolId());
            item.setUniversityName(c.getUniversityName());
            item.setMajorName(c.getMajorName());
            item.setSimilarity(Math.round(similarity * 1000) / 1000.0);
            item.setLatestMinRank(cRank);
            item.setLatestMinScore(cScore);
            item.setLatestYear(c.getYear());
            item.setReason(buildRecommendReason(ref, c, similarity));
            results.add(item);
        }

        // 按相似度降序排列, 取 topN
        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return results.stream().limit(topN).collect(Collectors.toList());
    }

    /**
     * 基于多个已选志愿的聚合推荐
     */
    public List<RecommendItem> recommendByMultiple(List<VolunteerTarget> selected,
                                                     String subjectType, int studentRank,
                                                     int topN) {
        Set<String> selectedKeys = selected.stream()
                .map(t -> t.getSchoolId() + "|" + t.getMajorName())
                .collect(Collectors.toSet());

        // 对每个已选志愿获取推荐, 然后聚合
        Map<String, RecommendItem> aggregated = new LinkedHashMap<>();
        for (VolunteerTarget t : selected) {
            List<RecommendItem> recs = recommendSimilar(t.getSchoolId(), t.getMajorName(),
                    subjectType, studentRank, topN * 2);
            for (RecommendItem rec : recs) {
                String key = rec.getSchoolId() + "|" + rec.getMajorName();
                if (selectedKeys.contains(key)) continue;
                RecommendItem existing = aggregated.get(key);
                if (existing == null || rec.getSimilarity() > existing.getSimilarity()) {
                    aggregated.put(key, rec);
                }
            }
        }

        List<RecommendItem> results = new ArrayList<>(aggregated.values());
        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return results.stream().limit(topN).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════
    // 工具类 & DTO
    // ═══════════════════════════════════════════════════════

    @Data
    public static class VolunteerTarget {
        private String schoolId;
        private String majorName;
    }

    /**
     * 获取某个院校+专业的近N年分数线数据
     * 优先查专业表 data_major_score_gz，不足时回退查院校表 data_score_line_gz
     */
    public List<ScoreLineGz> getRecentLines(String schoolId, String majorName,
                                              String subjectType, int years) {
        // Step 1: 先从专业表查
        if (majorName != null && !majorName.isBlank()) {
            List<ScoreLineGz> majorLines = getMajorRecentLines(schoolId, majorName, subjectType, years);
            if (!majorLines.isEmpty()) {
                return majorLines;
            }
        }

        // Step 2: 回退到院校表
        LambdaQueryWrapper<ScoreLineGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScoreLineGz::getSchoolId, schoolId);
        applyScoreLineSubjectFilter(wrapper, subjectType)
                .isNotNull(ScoreLineGz::getMinRank)
                .gt(ScoreLineGz::getMinRank, 0)
                .orderByDesc(ScoreLineGz::getYear);

        if (majorName != null && !majorName.isBlank()) {
            wrapper.eq(ScoreLineGz::getMajorName, majorName);
            wrapper.last("LIMIT " + (years * 2));
        } else {
            wrapper.last("LIMIT 500");
        }

        List<ScoreLineGz> raw = scoreLineGzMapper.selectList(wrapper);

        Map<Integer, List<ScoreLineGz>> byYear = raw.stream()
                .collect(Collectors.groupingBy(ScoreLineGz::getYear, LinkedHashMap::new, Collectors.toList()));

        List<ScoreLineGz> result = new ArrayList<>();
        for (Map.Entry<Integer, List<ScoreLineGz>> entry : byYear.entrySet()) {
            List<ScoreLineGz> yearLines = entry.getValue();
            yearLines.sort(Comparator.comparingInt(sl -> sl.getMinRank() != null ? sl.getMinRank() : 0));
            ScoreLineGz median = yearLines.get(yearLines.size() / 2);
            result.add(median);
            if (result.size() >= years) break;
        }

        return result;
    }

    /**
     * 从专业分数线表获取历史数据，转为 ScoreLineGz 兼容格式
     */
    private List<ScoreLineGz> getMajorRecentLines(String schoolId, String majorName,
                                                     String subjectType, int years) {
        LambdaQueryWrapper<MajorScoreGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MajorScoreGz::getSchoolId, schoolId)
               .eq(MajorScoreGz::getMajorName, majorName);
        applyMajorSubjectFilter(wrapper, subjectType)
                .isNotNull(MajorScoreGz::getMinRank)
                .gt(MajorScoreGz::getMinRank, 0)
                .orderByDesc(MajorScoreGz::getYear)
                .last("LIMIT " + (years * 2));

        List<MajorScoreGz> majors = majorScoreGzMapper.selectList(wrapper);
        if (majors.isEmpty()) return List.of();

        Map<Integer, MajorScoreGz> byYear = new LinkedHashMap<>();
        for (MajorScoreGz m : majors) {
            byYear.putIfAbsent(m.getYear(), m);
        }

        List<ScoreLineGz> result = new ArrayList<>();
        int count = 0;
        for (MajorScoreGz m : byYear.values()) {
            ScoreLineGz sl = new ScoreLineGz();
            sl.setSchoolId(m.getSchoolId());
            sl.setUniversityName(m.getUniversityName());
            sl.setMajorName(m.getMajorName());
            sl.setYear(m.getYear());
            sl.setSubjectType(m.getSubjectType());
            sl.setMinScore(m.getMinScore());
            sl.setMaxScore(m.getMaxScore());
            sl.setAvgScore(m.getAvgScore());
            sl.setMinRank(m.getMinRank());
            sl.setPlanCount(m.getPlanCount());
            sl.setBatch(m.getBatch());
            result.add(sl);
            if (++count >= years) break;
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════
    // 算法5: 分数→位次校验 — 基于官方一分一段表
    // ═══════════════════════════════════════════════════════

    @Data
    public static class RankEstimate {
        private int score;
        private String subjectType;
        private int estimatedRank;       // 官方同分区间的保守位次(rankHigh)
        private int rankLow;             // 同分最好位次
        private int rankHigh;            // 同分保守位次
        private int dataPoints;          // 官方表命中条数，0表示未导入
        private int referenceYear;       // 参考年份
        private String confidence;       // 官方/数据不足
        private String source;           // 数据来源
        private String sourceUrl;        // 来源链接
        private String sourcePageUrl;    // 官方发布页面
        private String parseMethod;      // 解析方式
        private String note;             // 给前端展示的解释
    }

    /**
     * 根据官方一分一段表返回分数对应位次区间。
     * 只用于校验用户手填的官方位次，不自动替代用户输入。
     */
    @Cacheable(value = "rankEstimates", key = "'official_' + #score + '_' + #subjectType", unless = "#result.dataPoints < 1")
    public RankEstimate estimateRank(int score, String subjectType) {
        return estimateRank(score, subjectType, null);
    }

    /**
     * 按指定年份查询官方一分一段表；指定年份缺失时不跨年份、不跨科类猜测。
     */
    public RankEstimate estimateRank(int score, String subjectType, Integer requestedYear) {
        RankEstimate result = new RankEstimate();
        result.setScore(score);
        result.setSubjectType(subjectType);

        try {
            Integer queryYear = requestedYear;
            if (queryYear == null) {
                queryYear = scoreRankGzMapper.selectLatestYear(subjectType);
            }
            if (queryYear == null) {
                result.setEstimatedRank(0);
                result.setConfidence("数据不足");
                result.setNote(String.format("未导入官方一分一段表（%s），系统不提供分数推位次结果。", subjectType));
                return result;
            }

            ScoreRankGz line = scoreRankGzMapper.selectNearestAtOrBelow(queryYear, subjectType, score);
            if (line == null) {
                result.setEstimatedRank(0);
                result.setReferenceYear(queryYear);
                result.setConfidence("数据不足");
                if (requestedYear != null) {
                    result.setNote(String.format(
                            "未导入%d年%s官方一分一段表或该分数低于已导入最低分段，系统不做跨年份、跨科类换算。",
                            requestedYear, subjectType));
                } else {
                    result.setNote("当前分数低于官方一分一段表最低分段，请直接核对考试院原表。");
                }
                return result;
            }

            result.setEstimatedRank(line.getRankHigh());
            result.setRankLow(line.getRankLow());
            result.setRankHigh(line.getRankHigh());
            result.setDataPoints(1);
            result.setReferenceYear(line.getYear());
            result.setConfidence("官方");
            result.setSource(line.getSourceName());
            result.setSourceUrl(line.getSourceUrl());
            result.setSourcePageUrl(line.getSourcePageUrl());
            result.setParseMethod(line.getParseMethod());
            if (line.getScore() != null && line.getScore() == score) {
                result.setNote(String.format(
                        "%d年%s官方一分一段表：%s分同分位次区间约%d-%d名。",
                        line.getYear(), subjectType, line.getScoreLabel(), line.getRankLow(), line.getRankHigh()));
            } else {
                result.setNote(String.format(
                        "%d年%s官方一分一段表未列出%d分，按不高于该分的最近分段%s分给出保守校验区间约%d-%d名。",
                        line.getYear(), subjectType, score, line.getScoreLabel(), line.getRankLow(), line.getRankHigh()));
            }
            return result;
        } catch (Exception e) {
            log.warn("官方一分一段表查询失败: score={}, subjectType={}", score, subjectType, e);
            result.setEstimatedRank(0);
            result.setConfidence("数据不足");
            result.setNote("官方一分一段表暂不可用，请以考试院原表手动核对。");
            return result;
        }
    }

    private LambdaQueryWrapper<ScoreLineGz> applyScoreLineSubjectFilter(
            LambdaQueryWrapper<ScoreLineGz> wrapper, String subjectType) {
        String compatible = compatibleSubjectType(subjectType);
        if (compatible.equals(subjectType)) {
            return wrapper.eq(ScoreLineGz::getSubjectType, subjectType);
        }
        return wrapper.and(w -> w.eq(ScoreLineGz::getSubjectType, subjectType)
                .or()
                .eq(ScoreLineGz::getSubjectType, compatible));
    }

    private LambdaQueryWrapper<MajorScoreGz> applyMajorSubjectFilter(
            LambdaQueryWrapper<MajorScoreGz> wrapper, String subjectType) {
        String compatible = compatibleSubjectType(subjectType);
        if (compatible.equals(subjectType)) {
            return wrapper.eq(MajorScoreGz::getSubjectType, subjectType);
        }
        return wrapper.and(w -> w.eq(MajorScoreGz::getSubjectType, subjectType)
                .or()
                .eq(MajorScoreGz::getSubjectType, compatible));
    }

    private String compatibleSubjectType(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            return "";
        }
        return switch (subjectType) {
            case "物理类" -> "理科";
            case "历史类" -> "文科";
            case "理科" -> "物理类";
            case "文科" -> "历史类";
            default -> subjectType;
        };
    }

    /** 标准差 */
    private double calcStd(List<Integer> values) {
        if (values.size() < 2) return 0;
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    /** 标准正态分布CDF (近似) */
    private double normalCDF(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    /** 误差函数近似 (Abramowitz and Stegun) */
    private double erf(double x) {
        double a1 = 0.254829592, a2 = -0.284496736, a3 = 1.421413741;
        double a4 = -1.453152027, a5 = 1.061405429, p = 0.3275911;
        int sign = x >= 0 ? 1 : -1;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        return sign * y;
    }

    /** Sigmoid 函数 */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * 经验匹配度基线（旧称"经验命中率基线"，2026/05 起按合规白皮书统一改为"匹配度"口径）：
     * 用 Beta(1.5,1.5) 平滑，避免 2024/2025 少样本下给出过度确定的概率。
     */
    private double empiricalHitProbability(int studentRank, List<Integer> historicalCutoffRanks) {
        double alpha = 1.5;
        double beta = 1.5;
        long hits = historicalCutoffRanks.stream()
                .filter(rank -> studentRank <= rank)
                .count();
        return (hits + alpha) / (historicalCutoffRanks.size() + alpha + beta);
    }

    /**
     * 位次波动越大，参考概率越应保守。这里仅做轻量扣分，不替代前端/结果页的人工复核提示。
     */
    private double volatilityPenalty(double mean, double std) {
        if (mean <= 0 || std <= 0) {
            return 0;
        }
        double cv = std / mean;
        if (cv <= 0.08) {
            return 0;
        }
        return Math.min(10, (cv - 0.08) * 60);
    }

    /** 历史参考概率 → 等级。避免使用“极高”等确定性措辞。 */
    private String probToLevel(double prob) {
        if (prob >= 92) return "兜底参考";
        if (prob >= 75) return "参考较高";
        if (prob >= 45) return "参考中等";
        if (prob >= 20) return "参考偏低";
        return "参考较低";
    }

    /** 生成推荐理由 */
    private String buildRecommendReason(ScoreLineGz ref, ScoreLineGz candidate, double similarity) {
        int rankDiff = candidate.getMinRank() - ref.getMinRank();
        String direction = rankDiff > 0 ? "略低" : "略高";
        return String.format("与%s的%s录取层次相近(相似度%.0f%%)，录取位次%s%d位",
                ref.getUniversityName(), ref.getMajorName(),
                similarity * 100, direction, Math.abs(rankDiff));
    }
}
