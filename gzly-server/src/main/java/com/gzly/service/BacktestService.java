package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.algorithm.FeatureBuildEngine;
import com.gzly.common.exception.BizException;
import com.gzly.config.FallbackPredictionProperties;
import com.gzly.entity.AlgoBacktestReport;
import com.gzly.entity.MajorScoreGz;
import com.gzly.mapper.AlgoBacktestReportMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 推荐算法离线回测：
 * 用「数据截止年（cutoffYear = evaluationYear - 1）以前」的专业级录取数据做推荐口径，
 * 用 evaluationYear 的真实录取位次验证，产出命中率、梯度达线率与机会指数校准报告。
 *
 * 只读回测，不影响主生成链路；梯度区间口径复制自
 * {@code VolunteerService.defaultRatioRanges / defaultGradientInputs / resolveGradientRanges}
 * 的预设分支，主链路预设调整时必须同步这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestService {

    private static final List<String> GRADIENT_ORDER = List.of("冲", "稳", "保", "垫");
    private static final int MAX_SAMPLE_CANDIDATES = 200;
    private static final int HISTORY_WINDOW_YEARS = 3;

    /** 单次回测全表扫描需数秒，管理端连点会堆积线程；同一时刻只允许一个回测在跑。 */
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(false);

    private final com.gzly.mapper.MajorScoreGzMapper majorScoreGzMapper;
    private final com.gzly.mapper.DataAdmissionGroupLineMapper groupLineMapper;
    private final AlgoBacktestReportMapper reportMapper;
    private final FallbackRulePredictionEngine fallbackRulePredictionEngine;
    private final FeatureBuildEngine featureBuildEngine;
    private final FallbackPredictionProperties fallbackProps;
    private final ObjectMapper objectMapper;
    private final RankNormalizationService rankNormalizationService;
    private final ProvincePolicyService provincePolicyService;

    // ══════════════════ 请求 / 响应模型 ══════════════════

    @Data
    public static class BacktestRequest {
        /** 省份代码，缺省 GZ；专业组省份（SC/HB/AH/GX/HI/YN/HA）用组级调档线回测。 */
        private String provinceCode = ProvincePolicyService.GZ;
        /** 真实结果年份，例如 2025。 */
        private Integer evaluationYear;
        /** 物理类 / 历史类（海南为 综合）。 */
        private String subjectType;
        /** 保守型 / 均衡型 / 冲刺型，默认均衡型。 */
        private String strategyMode = "均衡型";
        /** 可选：指定模拟考生位次列表；缺省按 sampleStep 均匀采样。 */
        private List<Integer> candidateRanks;
        /** 缺省采样步长（位次间隔）。 */
        private Integer sampleStep = 5000;
        /** 可选：基础概率模型覆盖（sigmoid / log-rank），用于两种模型的 A/B 对比；缺省用全局配置。 */
        private String chanceModel;
        /** 人工备注。 */
        private String note;
    }

    @Data
    public static class GradientStat {
        private String gradient;
        private int pairs;
        private int hits;
        private double hitRate;
        private double avgChanceScore;
    }

    @Data
    public static class CalibrationBucket {
        /** 机会指数分桶下界（含），如 60 表示 [60,70)。 */
        private int bucketLow;
        private int pairs;
        private int hits;
        /** 桶内平均预测概率 0-1。 */
        private double avgPredicted;
        /** 桶内实际达线频率 0-1。 */
        private double actualHitRate;
    }

    @Data
    public static class BacktestReportView {
        private Long id;
        private String provinceCode;
        private int evaluationYear;
        private int cutoffYear;
        private String subjectType;
        private String strategyMode;
        private int sampleCandidates;
        private int evaluatedPairs;
        private double coverageRatio;
        private double overallHitRate;
        private double brierScore;
        private List<GradientStat> gradientStats;
        private List<CalibrationBucket> calibration;
        private Map<String, Object> paramsSnapshot;
        private String note;
    }

    // ══════════════════ 主流程 ══════════════════

    public BacktestReportView run(BacktestRequest req) {
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有一个回测任务在执行中，请等待其完成后再触发");
        }
        try {
            return doRun(req);
        } finally {
            running.set(false);
        }
    }

    private BacktestReportView doRun(BacktestRequest req) {
        String provinceCode = provincePolicyService.normalizeProvinceCode(
                req == null ? null : req.getProvinceCode());
        validate(req, provinceCode);
        int evaluationYear = req.getEvaluationYear();
        int cutoffYear = evaluationYear - 1;
        String subjectType = req.getSubjectType();
        String strategyMode = normalizeStrategy(req.getStrategyMode());
        boolean groupProvince = provincePolicyService.isProfessionalGroupProvince(provinceCode);

        Map<String, Integer> truthMap = groupProvince
                ? loadGroupTruth(provinceCode, evaluationYear, subjectType)
                : loadTruth(evaluationYear, subjectType);
        if (truthMap.isEmpty()) {
            throw new BizException(String.format("%s %d 年 %s 没有可用的真实录取位次数据，无法回测",
                    provinceCode, evaluationYear, subjectType));
        }
        Map<String, ReferenceStats> referenceMap = groupProvince
                ? loadGroupReference(provinceCode, cutoffYear, subjectType)
                : loadReference(cutoffYear, subjectType);
        if (referenceMap.isEmpty()) {
            throw new BizException(String.format("%s 截止 %d 年没有可用的历史参考数据，无法回测", provinceCode, cutoffYear));
        }

        List<Integer> candidateRanks = resolveCandidateRanks(req, truthMap);

        Map<String, GradientAccumulator> gradientAcc = new LinkedHashMap<>();
        GRADIENT_ORDER.forEach(g -> gradientAcc.put(g, new GradientAccumulator()));
        TreeMap<Integer, CalibrationAccumulator> calibrationAcc = new TreeMap<>();
        long inRangePairs = 0;
        long evaluatedPairs = 0;
        long totalHits = 0;
        double brierSum = 0;

        for (int candidateRank : candidateRanks) {
            Map<String, int[]> ranges = resolveGradientRanges(candidateRank, strategyMode);
            for (Map.Entry<String, ReferenceStats> entry : referenceMap.entrySet()) {
                ReferenceStats ref = entry.getValue();
                String gradient = classifyGradient(ref.getReferenceRank(), ranges);
                if (gradient == null) {
                    continue;
                }
                inRangePairs++;
                Integer actualMinRank = truthMap.get(entry.getKey());
                if (actualMinRank == null) {
                    continue;
                }
                evaluatedPairs++;
                boolean hit = candidateRank <= actualMinRank;
                if (hit) {
                    totalHits++;
                }

                FallbackRulePredictionEngine.Prediction prediction = fallbackRulePredictionEngine.predictWithModel(
                        resolveChanceModel(req),
                        candidateRank,
                        ref.getReferenceRank(),
                        ref.getVolatility(),
                        ref.getPlanChangeRate(),
                        ref.getDataConfidence(),
                        0);

                GradientAccumulator acc = gradientAcc.get(gradient);
                acc.pairs++;
                acc.chanceScoreSum += prediction.getChanceScore();
                if (hit) {
                    acc.hits++;
                }

                int bucketLow = Math.min(90, prediction.getChanceScore() / 10 * 10);
                CalibrationAccumulator bucket = calibrationAcc.computeIfAbsent(bucketLow, k -> new CalibrationAccumulator());
                bucket.pairs++;
                bucket.predictedSum += prediction.getInternalChanceScore();
                if (hit) {
                    bucket.hits++;
                }
                double error = prediction.getInternalChanceScore() - (hit ? 1D : 0D);
                brierSum += error * error;
            }
        }

        if (evaluatedPairs == 0) {
            throw new BizException("回测没有产生可评估的考生-候选对，请检查年份、科类与采样参数");
        }

        BacktestReportView view = buildView(req, cutoffYear, strategyMode, candidateRanks.size(),
                (int) evaluatedPairs, inRangePairs, totalHits, brierSum, gradientAcc, calibrationAcc,
                provinceCode, groupProvince);
        persist(view);
        return view;
    }

    public List<AlgoBacktestReport> listReports(int limit) {
        int size = Math.max(1, Math.min(50, limit));
        LambdaQueryWrapper<AlgoBacktestReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AlgoBacktestReport::getCreatedAt).last("LIMIT " + size);
        return reportMapper.selectList(wrapper);
    }

    // ══════════════════ 数据加载 ══════════════════

    /**
     * 真实结果：evaluationYear 的专业级最低位次。
     * 同一 school+major 有多条（不同批次）时取最大 minRank（对考生最宽松的一条），口径记录在 paramsSnapshot。
     */
    private Map<String, Integer> loadTruth(int evaluationYear, String subjectType) {
        List<MajorScoreGz> rows = selectMajorScores(evaluationYear, evaluationYear, subjectType);
        Map<String, Integer> truth = new LinkedHashMap<>();
        for (MajorScoreGz row : rows) {
            String key = keyOf(row);
            Integer existing = truth.get(key);
            if (existing == null || row.getMinRank() > existing) {
                truth.put(key, row.getMinRank());
            }
        }
        return truth;
    }

    /** 基础模型：请求覆盖优先，缺省用全局配置（与主链路一致）。 */
    private String resolveChanceModel(BacktestRequest req) {
        String override = req == null || req.getChanceModel() == null ? "" : req.getChanceModel().trim();
        return override.isBlank() ? fallbackProps.getModel() : override;
    }

    /**
     * 历史参考：cutoffYear 往前 3 年窗口内的专业级数据；
     * 参考位次取最近可用年份（与主链路「参考年 = 最近可用」一致），
     * 各年位次先经等效位次换算归一到 cutoffYear 口径（一分一段缺失年份自动不换算），
     * 波动 / 计划变化 / 置信度复用 FeatureBuildEngine 的同款口径。
     */
    private Map<String, ReferenceStats> loadReference(int cutoffYear, String subjectType) {
        List<MajorScoreGz> rows = selectMajorScores(cutoffYear - HISTORY_WINDOW_YEARS + 1, cutoffYear, subjectType);
        Map<String, List<MajorScoreGz>> grouped = new LinkedHashMap<>();
        for (MajorScoreGz row : rows) {
            grouped.computeIfAbsent(keyOf(row), k -> new ArrayList<>()).add(row);
        }

        Map<String, ReferenceStats> reference = new LinkedHashMap<>();
        for (Map.Entry<String, List<MajorScoreGz>> entry : grouped.entrySet()) {
            List<MajorScoreGz> history = entry.getValue();
            history.sort(Comparator.comparing(MajorScoreGz::getYear, Comparator.reverseOrder()));
            List<Integer> ranks = history.stream()
                    .map(row -> row.getMinRank() == null || row.getMinRank() <= 0 || row.getYear() == null
                            ? row.getMinRank()
                            : rankNormalizationService.normalize(row.getMinRank(), row.getYear(), cutoffYear,
                                    ProvincePolicyService.GZ, subjectType))
                    .toList();
            List<Integer> plans = history.stream().map(MajorScoreGz::getPlanCount).toList();
            FeatureBuildEngine.RankFeature feature = featureBuildEngine.buildRankFeatures(ranks, plans, null, null);
            if (feature.getLatestMinRank() == null || feature.getLatestMinRank() <= 0) {
                continue;
            }
            ReferenceStats stats = new ReferenceStats();
            stats.setReferenceRank(feature.getLatestMinRank());
            stats.setVolatility(feature.getRankVolatility3y());
            stats.setPlanChangeRate(feature.getPlanChangeRate());
            stats.setDataConfidence(feature.getDataConfidence());
            reference.put(entry.getKey(), stats);
        }
        return reference;
    }

    // ══════════════════ 专业组省份数据加载（组级调档线口径） ══════════════════

    /** 专业组真实结果：evaluationYear 的组级最低调档位次；同 key 多批次取最宽松（最大位次）。 */
    private Map<String, Integer> loadGroupTruth(String provinceCode, int evaluationYear, String subjectType) {
        List<com.gzly.entity.DataAdmissionGroupLine> rows = selectGroupLines(provinceCode, evaluationYear, evaluationYear, subjectType);
        Map<String, Integer> truth = new LinkedHashMap<>();
        for (com.gzly.entity.DataAdmissionGroupLine row : rows) {
            String key = groupKeyOf(row);
            Integer existing = truth.get(key);
            if (existing == null || row.getMinRank() > existing) {
                truth.put(key, row.getMinRank());
            }
        }
        return truth;
    }

    /** 专业组历史参考：与贵州口径一致（最近可用年 + 等效位次归一 + FeatureBuildEngine 特征）。 */
    private Map<String, ReferenceStats> loadGroupReference(String provinceCode, int cutoffYear, String subjectType) {
        List<com.gzly.entity.DataAdmissionGroupLine> rows =
                selectGroupLines(provinceCode, cutoffYear - HISTORY_WINDOW_YEARS + 1, cutoffYear, subjectType);
        Map<String, List<com.gzly.entity.DataAdmissionGroupLine>> grouped = new LinkedHashMap<>();
        for (com.gzly.entity.DataAdmissionGroupLine row : rows) {
            grouped.computeIfAbsent(groupKeyOf(row), k -> new ArrayList<>()).add(row);
        }
        Map<String, ReferenceStats> reference = new LinkedHashMap<>();
        for (Map.Entry<String, List<com.gzly.entity.DataAdmissionGroupLine>> entry : grouped.entrySet()) {
            List<com.gzly.entity.DataAdmissionGroupLine> history = entry.getValue();
            history.sort(Comparator.comparing(com.gzly.entity.DataAdmissionGroupLine::getYear, Comparator.reverseOrder()));
            List<Integer> ranks = history.stream()
                    .map(row -> row.getMinRank() == null || row.getMinRank() <= 0 || row.getYear() == null
                            ? row.getMinRank()
                            : rankNormalizationService.normalize(row.getMinRank(), row.getYear(), cutoffYear,
                                    provinceCode, subjectType))
                    .toList();
            List<Integer> plans = history.stream().map(com.gzly.entity.DataAdmissionGroupLine::getPlanCount).toList();
            FeatureBuildEngine.RankFeature feature = featureBuildEngine.buildRankFeatures(ranks, plans, null, null);
            if (feature.getLatestMinRank() == null || feature.getLatestMinRank() <= 0) {
                continue;
            }
            ReferenceStats stats = new ReferenceStats();
            stats.setReferenceRank(feature.getLatestMinRank());
            stats.setVolatility(feature.getRankVolatility3y());
            stats.setPlanChangeRate(feature.getPlanChangeRate());
            stats.setDataConfidence(feature.getDataConfidence());
            reference.put(entry.getKey(), stats);
        }
        return reference;
    }

    private List<com.gzly.entity.DataAdmissionGroupLine> selectGroupLines(String provinceCode, int yearFrom, int yearTo,
                                                                          String subjectType) {
        LambdaQueryWrapper<com.gzly.entity.DataAdmissionGroupLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(com.gzly.entity.DataAdmissionGroupLine::getSchoolId,
                        com.gzly.entity.DataAdmissionGroupLine::getGroupCode,
                        com.gzly.entity.DataAdmissionGroupLine::getYear,
                        com.gzly.entity.DataAdmissionGroupLine::getMinRank,
                        com.gzly.entity.DataAdmissionGroupLine::getPlanCount)
                .eq(com.gzly.entity.DataAdmissionGroupLine::getProvinceCode, provinceCode)
                .eq(com.gzly.entity.DataAdmissionGroupLine::getSubjectType, subjectType)
                .between(com.gzly.entity.DataAdmissionGroupLine::getYear, yearFrom, yearTo)
                .isNotNull(com.gzly.entity.DataAdmissionGroupLine::getMinRank)
                .gt(com.gzly.entity.DataAdmissionGroupLine::getMinRank, 0);
        return groupLineMapper.selectList(wrapper);
    }

    private String groupKeyOf(com.gzly.entity.DataAdmissionGroupLine row) {
        return row.getSchoolId() + "|" + row.getGroupCode();
    }

    private List<MajorScoreGz> selectMajorScores(int yearFrom, int yearTo, String subjectType) {
        // 与主链路特征窗口同口径：贵州参考数据不早于新高考首年（2024），
        // 保证回测评估的正是线上实际使用的数据窗口。
        int eraYear = provincePolicyService.getPolicy(ProvincePolicyService.GZ).getNewGaokaoFirstYear();
        int effectiveYearFrom = Math.max(yearFrom, eraYear);
        String legacyType = "物理类".equals(subjectType) ? "理科" : "文科";
        LambdaQueryWrapper<MajorScoreGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(MajorScoreGz::getSchoolId, MajorScoreGz::getMajorName, MajorScoreGz::getYear,
                        MajorScoreGz::getMinRank, MajorScoreGz::getPlanCount)
                .and(w -> w.eq(MajorScoreGz::getSubjectType, subjectType)
                        .or().eq(MajorScoreGz::getSubjectType, legacyType))
                .between(MajorScoreGz::getYear, effectiveYearFrom, yearTo)
                .isNotNull(MajorScoreGz::getMinRank)
                .gt(MajorScoreGz::getMinRank, 0);
        return majorScoreGzMapper.selectList(wrapper);
    }

    private String keyOf(MajorScoreGz row) {
        return row.getSchoolId() + "|" + row.getMajorName();
    }

    // ══════════════════ 梯度口径（与主链路预设保持一致） ══════════════════

    /**
     * 预设梯度位次区间。复制自 VolunteerService 的
     * defaultRatioRanges + defaultGradientInputs + resolveGradientRanges 预设分支：
     * 位次 <= 20000 用纯比例区间；否则取绝对偏移与比例区间交集，无交集回退比例区间。
     */
    static Map<String, int[]> resolveGradientRanges(int candidateRank, String strategyMode) {
        double[][] ratios = ratioPreset(strategyMode);
        int[][] offsets = offsetPreset(strategyMode);
        Map<String, int[]> ranges = new LinkedHashMap<>();
        for (int i = 0; i < GRADIENT_ORDER.size(); i++) {
            int ratioLow = Math.max(1, (int) Math.round(candidateRank * ratios[i][0]));
            int ratioHigh = Math.max(ratioLow, (int) Math.round(candidateRank * ratios[i][1]));
            if (candidateRank <= 20_000) {
                ranges.put(GRADIENT_ORDER.get(i), new int[]{ratioLow, ratioHigh});
                continue;
            }
            int absoluteLow = Math.max(1, candidateRank + offsets[i][0]);
            int absoluteHigh = Math.max(absoluteLow, candidateRank + offsets[i][1]);
            int low = Math.max(absoluteLow, ratioLow);
            int high = Math.min(absoluteHigh, ratioHigh);
            if (high < low) {
                low = ratioLow;
                high = ratioHigh;
            }
            ranges.put(GRADIENT_ORDER.get(i), new int[]{low, high});
        }
        return ranges;
    }

    private static double[][] ratioPreset(String strategyMode) {
        // 数值单源：GradientAllocationEngine.ratioPreset（不再需要与主链路人工同步）。
        return com.gzly.algorithm.GradientAllocationEngine.ratioPreset(strategyMode);
    }

    private static int[][] offsetPreset(String strategyMode) {
        // 数值单源：GradientAllocationEngine.offsetPreset。
        return com.gzly.algorithm.GradientAllocationEngine.offsetPreset(strategyMode);
    }

    /** 参考位次落入哪个梯度区间；区间按冲→垫顺序判定，都不落则返回 null。 */
    static String classifyGradient(int referenceRank, Map<String, int[]> ranges) {
        for (Map.Entry<String, int[]> entry : ranges.entrySet()) {
            if (referenceRank >= entry.getValue()[0] && referenceRank <= entry.getValue()[1]) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ══════════════════ 采样与校验 ══════════════════

    private void validate(BacktestRequest req, String provinceCode) {
        if (req == null || req.getEvaluationYear() == null) {
            throw new BizException("evaluationYear 必填，例如 2025");
        }
        if (req.getEvaluationYear() < 2022 || req.getEvaluationYear() > 2100) {
            throw new BizException("evaluationYear 超出可回测范围");
        }
        List<String> allowed = provincePolicyService.getPolicy(provinceCode).getSubjectTypes();
        if (req.getSubjectType() == null || !allowed.contains(req.getSubjectType())) {
            throw new BizException("subjectType 必须为 " + String.join(" 或 ", allowed));
        }
        if (req.getCandidateRanks() != null && req.getCandidateRanks().size() > MAX_SAMPLE_CANDIDATES) {
            throw new BizException("candidateRanks 数量不能超过 " + MAX_SAMPLE_CANDIDATES);
        }
    }

    private String normalizeStrategy(String strategyMode) {
        String value = strategyMode == null ? "" : strategyMode.trim();
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "保守型", "conservative" -> "保守型";
            case "冲刺型", "aggressive" -> "冲刺型";
            default -> "均衡型";
        };
    }

    private List<Integer> resolveCandidateRanks(BacktestRequest req, Map<String, Integer> truthMap) {
        if (req.getCandidateRanks() != null && !req.getCandidateRanks().isEmpty()) {
            List<Integer> ranks = req.getCandidateRanks().stream()
                    .filter(r -> r != null && r > 0)
                    .distinct()
                    .sorted()
                    .toList();
            if (ranks.isEmpty()) {
                throw new BizException("candidateRanks 中没有合法位次");
            }
            return ranks;
        }
        int step = req.getSampleStep() == null || req.getSampleStep() < 1000 ? 5000 : req.getSampleStep();
        int maxTruthRank = truthMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int upper = Math.min(200_000, Math.max(step, maxTruthRank));
        List<Integer> ranks = new ArrayList<>();
        for (int rank = step; rank <= upper && ranks.size() < MAX_SAMPLE_CANDIDATES; rank += step) {
            ranks.add(rank);
        }
        if (ranks.isEmpty()) {
            throw new BizException("采样步长过大，未生成任何模拟考生位次");
        }
        return ranks;
    }

    // ══════════════════ 汇总与持久化 ══════════════════

    private BacktestReportView buildView(BacktestRequest req, int cutoffYear, String strategyMode,
                                         int sampleCandidates, int evaluatedPairs, long inRangePairs,
                                         long totalHits, double brierSum,
                                         Map<String, GradientAccumulator> gradientAcc,
                                         TreeMap<Integer, CalibrationAccumulator> calibrationAcc,
                                         String provinceCode, boolean groupProvince) {
        BacktestReportView view = new BacktestReportView();
        view.setProvinceCode(provinceCode);
        view.setEvaluationYear(req.getEvaluationYear());
        view.setCutoffYear(cutoffYear);
        view.setSubjectType(req.getSubjectType());
        view.setStrategyMode(strategyMode);
        view.setSampleCandidates(sampleCandidates);
        view.setEvaluatedPairs(evaluatedPairs);
        view.setCoverageRatio(round4(inRangePairs == 0 ? 0 : (double) evaluatedPairs / inRangePairs));
        view.setOverallHitRate(round4((double) totalHits / evaluatedPairs));
        view.setBrierScore(round4(brierSum / evaluatedPairs));
        view.setNote(req.getNote());

        List<GradientStat> gradientStats = new ArrayList<>();
        for (String gradient : GRADIENT_ORDER) {
            GradientAccumulator acc = gradientAcc.get(gradient);
            GradientStat stat = new GradientStat();
            stat.setGradient(gradient);
            stat.setPairs(acc.pairs);
            stat.setHits(acc.hits);
            stat.setHitRate(round4(acc.pairs == 0 ? 0 : (double) acc.hits / acc.pairs));
            stat.setAvgChanceScore(round4(acc.pairs == 0 ? 0 : acc.chanceScoreSum / acc.pairs));
            gradientStats.add(stat);
        }
        view.setGradientStats(gradientStats);

        List<CalibrationBucket> calibration = new ArrayList<>();
        calibrationAcc.forEach((bucketLow, acc) -> {
            CalibrationBucket bucket = new CalibrationBucket();
            bucket.setBucketLow(bucketLow);
            bucket.setPairs(acc.pairs);
            bucket.setHits(acc.hits);
            bucket.setAvgPredicted(round4(acc.pairs == 0 ? 0 : acc.predictedSum / acc.pairs));
            bucket.setActualHitRate(round4(acc.pairs == 0 ? 0 : (double) acc.hits / acc.pairs));
            calibration.add(bucket);
        });
        view.setCalibration(calibration);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("provinceCode", provinceCode);
        snapshot.put("fallbackProps", fallbackProps);
        snapshot.put("chanceModel", resolveChanceModel(req));
        snapshot.put("rankNormalization", "equivalent-rank via score-rank population ratio (no-op when population missing)");
        snapshot.put("gradientRangeSource", "GradientAllocationEngine preset (ratio + offset, rank<=20000 pure ratio)");
        snapshot.put("referenceRankSource", "latest available year within " + HISTORY_WINDOW_YEARS + "y window <= cutoffYear");
        snapshot.put("truthAggregation", groupProvince
                ? "max minRank across batches per school+group"
                : "max minRank across batches per school+major");
        snapshot.put("hitDefinition", "candidateRank <= actual minRank of evaluationYear");
        snapshot.put("hitDefinitionCaveat",
                "该命中口径系统性偏乐观：忽略组内专业分配/调剂/单科体检限制，真值取跨批次最宽松位次；"
                        + "绝对值不可作为录取率对外使用，仅适合模型/参数间的相对 A/B 对比，"
                        + "绝对校准需真实录取结果回流后重估。");
        snapshot.put("dataLevel", groupProvince
                ? "group-level (data_admission_group_line)"
                : "major-level (data_major_score_gz)");
        view.setParamsSnapshot(snapshot);
        return view;
    }

    private void persist(BacktestReportView view) {
        AlgoBacktestReport entity = new AlgoBacktestReport();
        entity.setProvinceCode(view.getProvinceCode());
        entity.setEvaluationYear(view.getEvaluationYear());
        entity.setCutoffYear(view.getCutoffYear());
        entity.setSubjectType(view.getSubjectType());
        entity.setStrategyMode(view.getStrategyMode());
        entity.setSampleCandidates(view.getSampleCandidates());
        entity.setEvaluatedPairs(view.getEvaluatedPairs());
        entity.setCoverageRatio(view.getCoverageRatio());
        entity.setOverallHitRate(view.getOverallHitRate());
        entity.setBrierScore(view.getBrierScore());
        entity.setGradientStatsJson(toJson(view.getGradientStats()));
        entity.setCalibrationJson(toJson(view.getCalibration()));
        entity.setParamsSnapshotJson(toJson(view.getParamsSnapshot()));
        entity.setNote(view.getNote());
        entity.setCreatedAt(LocalDateTime.now());
        try {
            reportMapper.insert(entity);
            view.setId(entity.getId());
        } catch (Exception e) {
            // 表未初始化等落库失败不影响返回报告本身，管理员可先看结果再补表。
            log.warn("回测报告落库失败: {}", e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("回测报告 JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    @Data
    static class ReferenceStats {
        private int referenceRank;
        private double volatility;
        private double planChangeRate;
        private double dataConfidence;
    }

    private static class GradientAccumulator {
        int pairs;
        int hits;
        double chanceScoreSum;
    }

    private static class CalibrationAccumulator {
        int pairs;
        int hits;
        double predictedSum;
    }
}
