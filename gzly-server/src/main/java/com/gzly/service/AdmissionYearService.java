package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdmissionYearService {

    public static final int FALLBACK_ACTIVE_ADMISSION_YEAR = 2026;
    public static final String PUBLIC_YEAR_LOCKED_MESSAGE = "当前填报入口仅支持最新高考年份，历史年份仅用于回测和模型校准。";
    public static final String PHASE_PRE_OFFICIAL_DATA = "PRE_OFFICIAL_DATA";
    public static final String PHASE_OFFICIAL_DATA_PARTIAL = "OFFICIAL_DATA_PARTIAL";
    public static final String PHASE_OFFICIAL_DATA_IMPORTED = "OFFICIAL_DATA_IMPORTED";
    public static final String PHASE_MODEL_RETRAINED = "MODEL_RETRAINED";
    public static final String PRE_OFFICIAL_DATA_WARNING = "2026 官方数据尚未发布，当前仅基于 2024/2025 历史数据提供趋势分析和预估参考。";
    public static final String PRE_OFFICIAL_DATA_ESTIMATE_WARNING = "当前为 2026 志愿预估参考，2026 官方招生计划和一分一段表尚未发布，结果基于 2024/2025 历史数据、院校专业录取趋势和计划变化进行模拟测算。待 2026 官方数据导入后，请重新生成正式志愿方案。";
    public static final String OFFICIAL_DATA_PARTIAL_WARNING = "2026 官方数据正在分批导入和质检，当前仍仅支持政策、趋势和数据缺口说明；完整推荐会在关键数据齐备后开放。";
    public static final String OFFICIAL_DATA_IMPORTED_TRIAL_WARNING = "2026 官方数据已导入并通过关键质检，当前处于试推荐阶段；模型重训和规则校验完成后再开放完整推荐。";

    @Value("${gzly.admission.active-year:2026}")
    private Integer activeAdmissionYear;

    @Value("${gzly.admission.latest-official-data-year:2025}")
    private Integer latestOfficialDataYear;

    @Value("${gzly.admission.history-years:2025,2024}")
    private String historyYears;

    @Value("${gzly.admission.training-years:2024,2025}")
    private String trainingYears;

    @Value("${gzly.admission.target-year:0}")
    private Integer targetYear;

    @Value("${gzly.admission.future-import-year:2026}")
    private Integer futureImportYear;

    @Value("${gzly.admission.recommendation-phase:PRE_OFFICIAL_DATA}")
    private String recommendationPhase;

    @Value("${gzly.admission.readiness.policy-ready:false}")
    private boolean policyReady;

    @Value("${gzly.admission.readiness.score-segment-ready:false}")
    private boolean scoreSegmentReady;

    @Value("${gzly.admission.readiness.admission-plan-ready:false}")
    private boolean admissionPlanReady;

    @Value("${gzly.admission.readiness.major-requirement-ready:false}")
    private boolean majorRequirementReady;

    @Value("${gzly.admission.readiness.major-meta-ready:false}")
    private boolean majorMetaReady;

    @Value("${gzly.admission.readiness.ml-training-ready:false}")
    private boolean mlTrainingReady;

    @Value("${gzly.admission.readiness.historical-training-ready:true}")
    private boolean historicalTrainingReady;

    public int getActiveAdmissionYear() {
        return activeAdmissionYear == null || activeAdmissionYear <= 0
                ? FALLBACK_ACTIVE_ADMISSION_YEAR
                : activeAdmissionYear;
    }

    public int getLatestOfficialDataYear() {
        return latestOfficialDataYear == null || latestOfficialDataYear <= 0
                ? getActiveAdmissionYear() - 1
                : latestOfficialDataYear;
    }

    public int getTargetYear() {
        return targetYear == null || targetYear <= 0 ? getActiveAdmissionYear() : targetYear;
    }

    public int getFutureImportYear() {
        return futureImportYear == null || futureImportYear <= 0 ? getActiveAdmissionYear() : futureImportYear;
    }

    public String getRecommendationPhase() {
        return normalizeRecommendationPhase(recommendationPhase);
    }

    public int normalizePublicYear(Integer requestedYear) {
        if (requestedYear == null || requestedYear <= 0) {
            return getActiveAdmissionYear();
        }
        return requireActiveYearForPublicApi(requestedYear);
    }

    public int requireActiveYearForPublicApi(Integer requestedYear) {
        int activeYear = getActiveAdmissionYear();
        if (requestedYear == null || requestedYear <= 0 || requestedYear == activeYear) {
            return activeYear;
        }
        throw new BizException(400, PUBLIC_YEAR_LOCKED_MESSAGE);
    }

    public List<Integer> resolveHistoryYears(int targetYear) {
        return resolveConfiguredYears(historyYears, targetYear);
    }

    public List<Integer> resolveTrainingYears() {
        List<Integer> years = resolveConfiguredYears(trainingYears, getTargetYear());
        if (years.isEmpty()) {
            years = resolveHistoryYears(getTargetYear());
        }
        return years;
    }

    public BatchSupportService.DataReadiness defaultDataReadiness(int year) {
        BatchSupportService.DataReadiness readiness = new BatchSupportService.DataReadiness();
        boolean targetYear = year == getTargetYear();
        boolean historicalOfficial = year > 0 && year <= getLatestOfficialDataYear();
        readiness.setPolicyReady(targetYear ? policyReady : historicalOfficial);
        readiness.setScoreSegmentReady(targetYear ? scoreSegmentReady : historicalOfficial);
        readiness.setAdmissionPlanReady(targetYear ? admissionPlanReady : historicalOfficial);
        readiness.setMajorRequirementReady(targetYear ? majorRequirementReady : historicalOfficial);
        readiness.setMajorMetaReady(targetYear ? majorMetaReady : historicalOfficial);
        readiness.setMlTrainingReady(targetYear ? mlTrainingReady : historicalOfficial);
        readiness.setHistoricalTrainingReady(historicalTrainingReady);
        readiness.setRecommendationPhase(targetYear ? getRecommendationPhase() : PHASE_MODEL_RETRAINED);
        return readiness;
    }

    public boolean isPreOfficialDataPhase(String phase) {
        return PHASE_PRE_OFFICIAL_DATA.equals(normalizeRecommendationPhase(phase));
    }

    public boolean isOfficialDataPartialPhase(String phase) {
        return PHASE_OFFICIAL_DATA_PARTIAL.equals(normalizeRecommendationPhase(phase));
    }

    public boolean isModelRetrainedPhase(String phase) {
        return PHASE_MODEL_RETRAINED.equals(normalizeRecommendationPhase(phase));
    }

    public boolean isOfficialDataReady(BatchSupportService.DataReadiness readiness) {
        if (readiness == null) {
            return false;
        }
        String phase = normalizeRecommendationPhase(readiness.getRecommendationPhase());
        return readiness.isPolicyReady()
                && readiness.isScoreSegmentReady()
                && readiness.isAdmissionPlanReady()
                && readiness.isMajorRequirementReady()
                && readiness.isMajorMetaReady()
                && (PHASE_OFFICIAL_DATA_IMPORTED.equals(phase) || PHASE_MODEL_RETRAINED.equals(phase));
    }

    private List<Integer> resolveConfiguredYears(String rawYears, int targetYear) {
        Set<Integer> years = new LinkedHashSet<>();
        if (rawYears != null && !rawYears.isBlank()) {
            for (String part : rawYears.split(",")) {
                try {
                    int year = Integer.parseInt(part.trim());
                    if (year > 0 && year < targetYear) {
                        years.add(year);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (years.isEmpty()) {
            years.add(targetYear - 1);
            years.add(targetYear - 2);
        }
        return new ArrayList<>(years);
    }

    public String normalizeRecommendationPhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return PHASE_PRE_OFFICIAL_DATA;
        }
        String value = phase.trim().toUpperCase();
        return switch (value) {
            case PHASE_OFFICIAL_DATA_PARTIAL, PHASE_OFFICIAL_DATA_IMPORTED, PHASE_MODEL_RETRAINED -> value;
            default -> PHASE_PRE_OFFICIAL_DATA;
        };
    }

    public void setActiveAdmissionYear(Integer activeAdmissionYear) {
        this.activeAdmissionYear = activeAdmissionYear;
    }

    public void setLatestOfficialDataYear(Integer latestOfficialDataYear) {
        this.latestOfficialDataYear = latestOfficialDataYear;
    }

    public void setHistoryYears(String historyYears) {
        this.historyYears = historyYears;
    }

    public void setTrainingYears(String trainingYears) {
        this.trainingYears = trainingYears;
    }

    public void setTargetYear(Integer targetYear) {
        this.targetYear = targetYear;
    }

    public void setFutureImportYear(Integer futureImportYear) {
        this.futureImportYear = futureImportYear;
    }

    public void setRecommendationPhase(String recommendationPhase) {
        this.recommendationPhase = recommendationPhase;
    }

    public void setPolicyReady(boolean policyReady) {
        this.policyReady = policyReady;
    }

    public void setScoreSegmentReady(boolean scoreSegmentReady) {
        this.scoreSegmentReady = scoreSegmentReady;
    }

    public void setAdmissionPlanReady(boolean admissionPlanReady) {
        this.admissionPlanReady = admissionPlanReady;
    }

    public void setMajorRequirementReady(boolean majorRequirementReady) {
        this.majorRequirementReady = majorRequirementReady;
    }

    public void setMajorMetaReady(boolean majorMetaReady) {
        this.majorMetaReady = majorMetaReady;
    }

    public void setMlTrainingReady(boolean mlTrainingReady) {
        this.mlTrainingReady = mlTrainingReady;
    }

    public void setHistoricalTrainingReady(boolean historicalTrainingReady) {
        this.historicalTrainingReady = historicalTrainingReady;
    }
}
