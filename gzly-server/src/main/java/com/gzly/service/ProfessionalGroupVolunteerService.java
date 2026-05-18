package com.gzly.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.common.ComplianceConstants;
import com.gzly.common.exception.BizException;
import com.gzly.entity.DataAdmissionGroupLine;
import com.gzly.entity.DataAdmissionGroupPlan;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.University;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.DataAdmissionGroupPlanMapper;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.PlanHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 多省院校专业组 45 志愿生成。
 * <p>
 * 只使用通用多省表中带官方/学校官网来源的候选；候选不足时直接返回数据不足，不用低可信数据补满。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalGroupVolunteerService {

    private static final int TARGET_TOTAL = 45;
    private static final Map<String, Integer> TARGET_COUNTS = Map.of(
            "冲", 8,
            "稳", 19,
            "保", 13,
            "垫", 5
    );
    private static final List<String> GRADIENT_ORDER = List.of("冲", "稳", "保", "垫");

    private final DataAdmissionGroupLineMapper groupLineMapper;
    private final DataAdmissionGroupPlanMapper groupPlanMapper;
    private final DataScoreRankMapper dataScoreRankMapper;
    private final PlanHistoryMapper planHistoryMapper;
    private final ScoreLineService scoreLineService;
    private final ProvincePolicyService provincePolicyService;
    private final ProvinceRankService provinceRankService;
    private final ObjectMapper objectMapper;
    private final VolunteerMetricsRecorder metricsRecorder;
    private final SafetyCodeService safetyCodeService;

    @Transactional(rollbackFor = Exception.class)
    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req, Long userId, String clientIp) {
        validate(req);
        long startedAt = System.currentTimeMillis();
        metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_TOTAL);
        try {
            ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(req.getProvinceCode());
            if (!ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45.equals(policy.getVolunteerUnitType())) {
                throw new BizException("该省份不适用院校专业组45志愿生成策略");
            }
            String provinceCode = policy.getProvinceCode();
            String subjectType = mapSubjectType(req.getFirstSubject());
            RankResolution rankResolution = resolveRankResolution(policy, req, subjectType);
            req.setProvinceRank(rankResolution.effectiveRank());
            // v7.48 (2026-05-18)：放宽 score_rank 强校验。
            // - 原硬校验「rankYear < 2025 抛错」对 AH 等尚未导入一分一段的省份过严，
            //   阻断了用户「自填位次 + 院校专业组线已就位」的正常生成路径。
            // - resolveRankResolution 已内置「用户未填位次且 score_rank 缺失」的明确报错，
            //   本处不再重复硬校验；score_rank 仅作为「未填位次时回退估算」的来源。
            Integer rankYear = dataScoreRankMapper.selectLatestYear(provinceCode, subjectType);
            if ((rankYear == null || rankYear < 2025) && req.getProvinceRank() <= 0) {
                throw new BizException(String.format(
                        "%s官方一分一段表尚未导入或未通过核验：请先手动填写考试院确认的全省位次，或导入2025年%s物理类/历史类官方一分一段后再开放估算。",
                        policy.getProvinceName(), policy.getProvinceName()));
            }
            Integer year = groupLineMapper.selectLatestYear(provinceCode, subjectType);
            if (year == null) {
                throw new BizException(String.format("%s%s数据尚未导入：请先导入2025年%s院校专业组计划与调档线后再开放生成。",
                        policy.getProvinceName(), policy.getTargetBatch(), policy.getProvinceName()));
            }

            VolunteerService.GradientRangeSummary rangeSummary = buildRangeSummary(policy, rankResolution.effectiveRank());
            List<VolunteerService.VolunteerItem> items = new ArrayList<>();
            int specialExcluded = 0;
            for (String gradient : GRADIENT_ORDER) {
                VolunteerService.GradientRangeDetail range = rangeSummary.getRanges().get(gradient);
                PickResult picked = pickGradient(policy, req, subjectType, year, gradient, range);
                items.addAll(picked.items());
                specialExcluded += picked.specialExcludedCount();
                range.setActualCount(picked.items().size());
            }

            boolean partialData = items.size() < TARGET_TOTAL;
            // v7.44 (2026-05-17)：候选数据不足 45 时，不再硬抛错。
            // PRE_OFFICIAL_DATA 阶段 / 数据补齐期间允许返回 27~44 条 TRIAL_RECOMMEND 草稿，
            // 同时在 dataQualityWarning / advisorAdvice 中显式标注"数据缺口"。
            // 业务约束：仅当 items.isEmpty() 时仍抛错（彻底无可用数据）；其它情况返回部分草稿 + 警告。
            if (items.isEmpty()) {
                metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_INCOMPLETE);
                throw new BizException(String.format(
                        "%s%s公开可核验院校专业组数据未检索到任何条目。请先按 docs/2026_sc_pre_launch_audit.md 流程补齐 2025 院校专业组计划和调档线后再生成。",
                        policy.getProvinceName(), policy.getTargetBatch()));
            }
            if (partialData) {
                metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_INCOMPLETE);
                log.warn("[ProfessionalGroupVolunteer] 部分数据生成：province={}, batch={}, items={}/{}",
                        policy.getProvinceCode(), policy.getTargetBatch(), items.size(), TARGET_TOTAL);
            }

            items.sort(Comparator
                    .comparingInt((VolunteerService.VolunteerItem item) -> gradientOrder(item.getGradient()))
                    .thenComparingInt(VolunteerService.VolunteerItem::getHistoryMinRank)
                    .thenComparing(item -> safeText(item.getUniversityName()))
                    .thenComparing(item -> safeText(item.getGroupCode())));
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setIndex(i + 1);
            }

            List<VolunteerService.ManualReviewItem> manualReviewItems = buildManualReviewList(items);
            VolunteerService.PlanMetrics metrics = buildMetrics(items, manualReviewItems, specialExcluded);
            long cost = System.currentTimeMillis() - startedAt;
            metrics.setGenerationCostMs(cost);
            metrics.setGeneratedAtMs(System.currentTimeMillis());

            PlanHistory history = new PlanHistory();
            history.setUserId(userId != null ? userId : 0L);
            history.setClientIp(clientIp);
            history.setProvinceCode(policy.getProvinceCode());
            history.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
            history.setTargetBatch(policy.getTargetBatch());
            history.setAgreedDisclaimer(Boolean.TRUE.equals(req.getAgreedDisclaimer()) ? 1 : 0);
            history.setDisclaimerVersion(req.getDisclaimerVersion());
            history.setDisclaimerConfirmedAt(LocalDateTime.now());
            history.setTotalScore(req.getTotalScore());
            history.setProvinceRank(rankResolution.effectiveRank());
            history.setFirstSubject(req.getFirstSubject());
            history.setStrategyMode(defaultText(req.getStrategyMode(), "均衡型"));
            history.setDecisionPriority(defaultText(req.getDecisionPriority(), "专业优先"));
            history.setCareerGoal(defaultText(req.getCareerGoal(), "就业优先"));
            history.setTuitionBudget(defaultText(req.getTuitionBudget(), "均衡预算"));
            history.setAcceptPrivate(Boolean.FALSE.equals(req.getAcceptPrivate()) ? 0 : 1);
            history.setAcceptSinoForeign(Boolean.TRUE.equals(req.getAcceptSinoForeign()) ? 1 : 0);
            history.setItemCount(items.size());
            history.setCreatedAt(LocalDateTime.now());
            history.setDataQualityWarning(buildDataQualityWarning(items, specialExcluded));
            SafetyCodeService.SafetyCodeIssue safetyCodeIssue = safetyCodeService.issue(req.getSafetyCode());
            history.setSafetyCodeHash(safetyCodeIssue.safetyCodeHash());
            history.setSafetyCodeCreatedAt(LocalDateTime.now());
            history.setSafetyCodeVersion(1);
            try {
                history.setResubjects(objectMapper.writeValueAsString(req.getResubjects()));
                history.setPreferredMajors(objectMapper.writeValueAsString(req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors()));
                history.setPreferredRegions(objectMapper.writeValueAsString(req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions()));
                history.setPlanJson(objectMapper.writeValueAsString(items));
                history.setManualReviewJson(objectMapper.writeValueAsString(manualReviewItems));
                history.setMetricsJson(objectMapper.writeValueAsString(metrics));
                history.setRequestSnapshotJson(objectMapper.writeValueAsString(buildRequestSnapshot(req, rangeSummary, rankResolution.summary())));
            } catch (JsonProcessingException e) {
                throw new BizException("序列化院校专业组方案失败");
            }
            planHistoryMapper.insert(history);

            VolunteerService.PlanResult result = new VolunteerService.PlanResult();
            result.setId(history.getId());
            result.setProvinceCode(policy.getProvinceCode());
            result.setProvinceName(policy.getProvinceName());
            result.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
            result.setVolunteerUnitLabel("院校专业组");
            result.setTargetBatch(policy.getTargetBatch());
            result.setTargetCount(TARGET_TOTAL);
            result.setTotalScore(req.getTotalScore());
            result.setProvinceRank(rankResolution.effectiveRank());
            result.setFirstSubject(req.getFirstSubject());
            result.setResubjects(req.getResubjects());
            result.setPreferredMajors(req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors());
            result.setPreferredRegions(req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions());
            result.setStrategyMode(history.getStrategyMode());
            result.setDecisionPriority(history.getDecisionPriority());
            result.setCareerGoal(history.getCareerGoal());
            result.setTuitionBudget(history.getTuitionBudget());
            result.setAcceptPrivate(history.getAcceptPrivate() == 1);
            result.setAcceptSinoForeign(history.getAcceptSinoForeign() == 1);
            result.setSafetyCode(safetyCodeIssue.safetyCode());
            result.setAccessKey(safetyCodeIssue.safetyCode());
            result.setItems(items);
            result.setCreatedAt(history.getCreatedAt().toString());
            result.setDataQualityWarning(history.getDataQualityWarning());
            result.setManualReviewItems(manualReviewItems);
            result.setMetrics(metrics);
            result.setReferenceProbabilityNotice(buildReferenceNotice(policy));
            result.setGradientRangeSummary(rangeSummary);
            result.setRankEstimate(rankResolution.summary());
            result.setAdvisorAdvice(buildAdvisorAdvice(policy, result));

            metricsRecorder.recordCost(cost);
            metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_SUCCESS);
            if (!manualReviewItems.isEmpty()) {
                metricsRecorder.incr(VolunteerMetricsRecorder.MANUAL_REVIEW_TRIGGERED);
            }
            return result;
        } catch (RuntimeException e) {
            metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_FAILURE);
            throw e;
        }
    }

    private PickResult pickGradient(ProvincePolicyService.ProvincePolicy policy, VolunteerService.GenerateRequest req, String subjectType, int year,
                                    String gradient, VolunteerService.GradientRangeDetail range) {
        List<DataAdmissionGroupLine> candidates = groupLineMapper.selectCandidates(
                policy.getProvinceCode(),
                year,
                subjectType,
                range.getRankLow(),
                range.getRankHigh(),
                batchKeyword(policy.getTargetBatch()),
                TARGET_COUNTS.getOrDefault(gradient, 0) * 8 + 30);
        Map<String, VolunteerService.VolunteerItem> deduped = new LinkedHashMap<>();
        int specialExcluded = 0;
        for (DataAdmissionGroupLine line : candidates) {
            String specialReason = specialTypeReason(line);
            if (!specialReason.isBlank()) {
                specialExcluded++;
                continue;
            }
            if (!matchResubject(line.getResubjectRequirement(), req.getResubjects())) {
                continue;
            }
            String key = safeText(line.getSchoolId()) + "|" + safeText(line.getGroupCode());
            deduped.putIfAbsent(key, toVolunteerItem(policy, line, gradient, req.getProvinceRank(), range));
        }
        List<VolunteerService.VolunteerItem> items = deduped.values().stream()
                .sorted(Comparator.comparingInt(VolunteerService.VolunteerItem::getHistoryMinRank))
                .limit(TARGET_COUNTS.getOrDefault(gradient, 0))
                .toList();
        return new PickResult(items, specialExcluded);
    }

    private VolunteerService.VolunteerItem toVolunteerItem(ProvincePolicyService.ProvincePolicy policy,
                                                           DataAdmissionGroupLine line, String gradient,
                                                           int studentRank,
                                                           VolunteerService.GradientRangeDetail range) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setProvinceCode(policy.getProvinceCode());
        item.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        item.setVolunteerUnitLabel("院校专业组");
        item.setUniversityName(line.getUniversityName());
        item.setSchoolId(line.getSchoolId());
        item.setGroupCode(line.getGroupCode());
        item.setGroupName(defaultText(line.getGroupName(), line.getGroupCode()));
        item.setMajorName(defaultText(line.getGroupName(), defaultText(line.getGroupCode(), "院校专业组")));
        item.setObeyAdjustment(Boolean.TRUE);
        item.setGradient(gradient);
        item.setHistoryMinScore(line.getMinScore() == null ? 0 : line.getMinScore());
        item.setHistoryMinRank(line.getMinRank() == null ? 0 : line.getMinRank());
        item.setReferenceYear(line.getYear() == null ? 0 : line.getYear());
        item.setResubjectRequirement(defaultText(line.getResubjectRequirement(), "需复核"));
        item.setSubjectRequirementSource("official_group");
        item.setRequirementSourceUrl(line.getSourceUrl());
        item.setRequirementSourceYear(line.getYear());
        item.setRequirementSourceName(defaultText(line.getSourceName(), policy.getOfficialSourceName() + "/高校招生官网"));
        item.setDataSourceType("院校专业组");
        item.setConfidenceLabel(resolveConfidence(line));
        item.setWithinConfiguredRange(true);
        item.setRangeNote(String.format("参考调档位次位于本次%s档区间：第%,d ~ %,d位", gradient, range.getRankLow(), range.getRankHigh()));
        item.setRankGap(item.getHistoryMinRank() - studentRank);
        item.setRankGapRatio(studentRank > 0 ? Math.round(item.getRankGap() * 1000.0 / studentRank) / 10.0 : 0);
        item.setLatestPlanCount(line.getPlanCount());
        item.setPlanTrend(line.getPlanCount() == null || line.getPlanCount() <= 0 ? "计划数暂缺" : "单年计划");
        item.setPlanRiskNote(line.getPlanCount() == null || line.getPlanCount() <= 0
                ? "院校专业组招生计划数暂缺，需核对当年官方专业目录。"
                : String.format("当前已导入专业组计划%d人，仍需结合组内专业计划复核。", line.getPlanCount()));
        applyGroupPlanPrecision(item, line);
        item.setReferenceFitLevel(resolveReferenceFitLevel(gradient));
        item.setDataConfidenceScore(calcConfidenceScore(line));
        item.setPrecisionScore(calcPrecisionScore(item));
        item.setPrecisionLabel(resolvePrecisionLabel(item.getPrecisionScore()));
        item.setPrecisionNote(buildPrecisionNote(item));
        item.setRecommendationScore(Math.min(100, item.getDataConfidenceScore() + item.getPrecisionScore() / 5));
        item.setProbLevel("参考匹配");
        item.setAdmissionProb(0);
        item.setRiskLevel("需复核");
        item.setRiskColor("yellow");
        item.setTrend("需结合后续年份复核");
        item.setLegacySubjectFallback(false);
        item.setSpecialTypeFlag(false);
        item.setNeedsManualReview(line.getPlanCount() == null || line.getPlanCount() <= 0);
        item.setReviewFlags(item.isNeedsManualReview() ? List.of("missing_plan_count") : List.of());
        item.setGroupMajors(loadGroupMajors(policy.getProvinceCode(), line));
        item.setHistoryRecords(loadHistoryRecords(policy.getProvinceCode(), line));
        item.setAlgorithmExplanation(buildExplanation(policy, item, studentRank, line));

        University university = scoreLineService.getUniversityById(line.getSchoolId());
        if (university != null) {
            item.setProvince(university.getProvince());
            item.setCity(university.getCity());
            item.setTags(university.getTags() == null ? List.of() : university.getTags());
            item.setSchoolNature(university.getNatureName());
        } else {
            item.setProvince("");
            item.setCity("");
            item.setTags(List.of());
            item.setSchoolNature("");
        }
        item.setMajorCatalogUrl(firstNonBlank(line.getSourceUrl(), line.getSourcePageUrl()));
        item.setAdmissionSiteUrl(line.getSourcePageUrl());
        item.setSchoolOfficialUrl(line.getSourceUrl());
        item.setRecommendReason(String.format("按%s%s院校专业组调档位次筛入，组内专业需结合当年专业目录复核。",
                policy.getProvinceName(), policy.getTargetBatch()));
        item.setRiskReason(item.isNeedsManualReview()
                ? String.format("该专业组计划数暂缺，需核对%s专业目录或院校招生官网。", policy.getOfficialSourceName())
                : "仍需复核招生计划变化、组内专业限制、单科/体检/语种要求和服从调剂风险。");
        item.setAlternativeOption("可与相邻位次、同选科要求的院校专业组横向比较后调整顺序。");
        item.setSuitableFor(String.format("适合作为%s%s院校专业组梯度草稿的一项。",
                policy.getProvinceName(), policy.getTargetBatch()));
        return item;
    }

    private void applyGroupPlanPrecision(VolunteerService.VolunteerItem item, DataAdmissionGroupLine line) {
        Integer planCount = line.getPlanCount();
        if (planCount == null || planCount <= 0) {
            item.setPlanExpansionIndex(0);
            item.setPlanExpansionLabel("计划待核验");
            item.setPlanExpansionNote("院校专业组计划数暂缺，暂不能计算扩招指数。");
            item.setSchoolEnrollmentIndex(0);
            item.setSchoolEnrollmentLabel("供给待核验");
            item.setSchoolEnrollmentNote("缺少专业组计划数，招生供给指数暂不可用。");
            return;
        }
        double supply;
        if (planCount >= 80) supply = 82;
        else if (planCount >= 40) supply = 72;
        else if (planCount >= 20) supply = 62;
        else if (planCount >= 10) supply = 52;
        else if (planCount >= 5) supply = 42;
        else supply = 30;
        if ("official".equalsIgnoreCase(safeText(line.getSourceLevel()))) {
            supply += 6;
        }
        supply = Math.max(0, Math.min(100, Math.round(supply * 10.0) / 10.0));
        item.setPlanExpansionIndex(100);
        item.setPlanExpansionLabel("单年计划");
        item.setPlanExpansionNote("当前院校专业组仅有当年计划数，扩招指数按100作为中性处理；后续有连续年份后再判断扩招/缩招。");
        item.setSchoolEnrollmentIndex(supply);
        item.setSchoolEnrollmentLabel(resolveSupplyLabel(supply));
        item.setSchoolEnrollmentNote(String.format("招生供给指数%.0f分：按专业组计划%d人、来源层级和可核验程度计算。", supply, planCount));
    }

    private List<String> loadGroupMajors(String provinceCode, DataAdmissionGroupLine line) {
        if (line.getYear() == null || safeText(line.getSchoolId()).isBlank() || safeText(line.getGroupCode()).isBlank()) {
            return List.of();
        }
        return groupPlanMapper.selectGroupMajors(provinceCode, line.getYear(), line.getSchoolId(),
                        line.getGroupCode(), line.getSubjectType(), 6)
                .stream()
                .map(DataAdmissionGroupPlan::getMajorName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .limit(6)
                .toList();
    }

    private List<VolunteerService.HistoryRecord> loadHistoryRecords(String provinceCode, DataAdmissionGroupLine line) {
        if (safeText(line.getSchoolId()).isBlank() || safeText(line.getGroupCode()).isBlank()) {
            return List.of();
        }
        return groupLineMapper.selectRecentHistory(provinceCode, line.getSchoolId(), line.getGroupCode(), line.getSubjectType(), 3)
                .stream()
                .map(this::toHistoryRecord)
                .toList();
    }

    private VolunteerService.HistoryRecord toHistoryRecord(DataAdmissionGroupLine line) {
        VolunteerService.HistoryRecord record = new VolunteerService.HistoryRecord();
        record.setProvinceCode(line.getProvinceCode());
        record.setGroupCode(line.getGroupCode());
        record.setYear(line.getYear());
        record.setMinScore(line.getMinScore());
        record.setMinRank(line.getMinRank());
        record.setPlanCount(line.getPlanCount());
        record.setBatch(line.getBatch());
        record.setSubjectType(line.getSubjectType());
        record.setDataSourceType("院校专业组");
        record.setConfidenceLabel(resolveConfidence(line));
        record.setRankSourceType(line.getMinRank() != null && line.getMinRank() > 0 ? "original" : "missing");
        record.setRankSourceNote(line.getMinRank() != null && line.getMinRank() > 0
                ? "院校专业组原始调档位次，来源于官方/学校官网公开数据。"
                : "缺少原始调档位次，需人工复核。");
        record.setRankLow(line.getMinRank());
        record.setRankHigh(line.getMinRank());
        record.setRankSourceUrl(line.getSourceUrl());
        record.setRankSourcePageUrl(line.getSourcePageUrl());
        return record;
    }

    private VolunteerService.GradientRangeSummary buildRangeSummary(ProvincePolicyService.ProvincePolicy policy, int rank) {
        Map<String, VolunteerService.GradientRangeDetail> ranges = new LinkedHashMap<>();
        putRange(policy, ranges, "冲", rank, 0.65, 0.95);
        putRange(policy, ranges, "稳", rank, 0.95, 1.20);
        putRange(policy, ranges, "保", rank, 1.20, 1.80);
        putRange(policy, ranges, "垫", rank, 1.80, 3.00);
        VolunteerService.GradientRangeSummary summary = new VolunteerService.GradientRangeSummary();
        summary.setSource(policy.getProvinceCode().toLowerCase(Locale.ROOT) + "_professional_group_policy_preset");
        summary.setStrategyMode(policy.getProvinceName() + policy.getTargetBatch());
        summary.setRanges(ranges);
        summary.setExplanation(String.format("%s按院校专业组作为志愿单位。本次按用户手填官方位次设置冲8、稳19、保13、垫5的45个专业组梯度；候选必须有官方/学校官网来源链接，数据不足不补假数据。",
                policy.getProvinceName()));
        return summary;
    }

    private void putRange(ProvincePolicyService.ProvincePolicy policy, Map<String, VolunteerService.GradientRangeDetail> ranges, String gradient,
                          int rank, double minRatio, double maxRatio) {
        VolunteerService.GradientRangeDetail detail = new VolunteerService.GradientRangeDetail();
        detail.setGradient(gradient);
        detail.setRankRatioMin(minRatio);
        detail.setRankRatioMax(maxRatio);
        detail.setRankLow(Math.max(1, (int) Math.round(rank * minRatio)));
        detail.setRankHigh(Math.max(detail.getRankLow(), (int) Math.round(rank * maxRatio)));
        detail.setRankOffsetMin(detail.getRankLow() - rank);
        detail.setRankOffsetMax(detail.getRankHigh() - rank);
        detail.setTargetCount(TARGET_COUNTS.getOrDefault(gradient, 0));
        detail.setLabel(String.format("%s：第%,d ~ %,d位", gradient, detail.getRankLow(), detail.getRankHigh()));
        detail.setRangeSourceNote(policy.getProvinceName() + "按院校专业组投档位次比例区间筛选。");
        ranges.put(gradient, detail);
    }

    private VolunteerService.PlanMetrics buildMetrics(List<VolunteerService.VolunteerItem> items,
                                                      List<VolunteerService.ManualReviewItem> manualReviewItems,
                                                      int specialExcluded) {
        VolunteerService.PlanMetrics metrics = new VolunteerService.PlanMetrics();
        metrics.setProvinceCode(items.isEmpty() ? "" : safeText(items.get(0).getProvinceCode()));
        metrics.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        metrics.setTargetCount(TARGET_TOTAL);
        metrics.setTotalCount(items.size());
        metrics.setChongCount(countByGradient(items, "冲"));
        metrics.setWenCount(countByGradient(items, "稳"));
        metrics.setBaoCount(countByGradient(items, "保"));
        metrics.setDianCount(countByGradient(items, "垫"));
        metrics.setManualReviewCount(manualReviewItems.size());
        metrics.setSpecialExcludedCount(specialExcluded);
        metrics.setLowConfidenceCount((int) items.stream().filter(item -> item.getDataConfidenceScore() < 70).count());
        metrics.setOfficialRequirementCount((int) items.stream().filter(item -> "official_group".equals(item.getSubjectRequirementSource())).count());
        metrics.setMissingRequirementCount((int) items.stream().filter(item -> safeText(item.getResubjectRequirement()).isBlank()).count());
        metrics.setNonMajorLevelCount(0);
        metrics.setLegacyFallbackCount(0);
        metrics.setExpandedPlanCount(0);
        metrics.setShrunkPlanCount(0);
        metrics.setMissingPlanIndexCount((int) items.stream().filter(item -> item.getPlanExpansionIndex() <= 0).count());
        metrics.setHighSupplyCount((int) items.stream().filter(item -> item.getSchoolEnrollmentIndex() >= 75).count());
        metrics.setLowSupplyCount((int) items.stream().filter(item -> item.getSchoolEnrollmentIndex() > 0 && item.getSchoolEnrollmentIndex() < 45).count());
        metrics.setAvgPrecisionScore(items.isEmpty()
                ? 0
                : Math.round((float) items.stream().mapToInt(VolunteerService.VolunteerItem::getPrecisionScore).average().orElse(0)));
        return metrics;
    }

    private int countByGradient(List<VolunteerService.VolunteerItem> items, String gradient) {
        return (int) items.stream().filter(item -> gradient.equals(item.getGradient())).count();
    }

    private List<VolunteerService.ManualReviewItem> buildManualReviewList(List<VolunteerService.VolunteerItem> items) {
        List<VolunteerService.ManualReviewItem> list = new ArrayList<>();
        for (VolunteerService.VolunteerItem item : items) {
            if (!item.isNeedsManualReview()) {
                continue;
            }
            VolunteerService.ManualReviewItem review = new VolunteerService.ManualReviewItem();
            review.setIndex(item.getIndex());
            review.setUniversityName(item.getUniversityName());
            review.setMajorName(item.getMajorName());
            review.setGradient(item.getGradient());
            review.setConfidenceLabel(item.getConfidenceLabel());
            review.setDataSourceType(item.getDataSourceType());
            review.setSubjectRequirementSource(item.getSubjectRequirementSource());
            review.setReasons(List.of("院校专业组计划数暂缺或来源未结构化，需核对当年专业目录与院校招生章程。"));
            List<String> links = new ArrayList<>();
            addIfPresent(links, item.getMajorCatalogUrl());
            addIfPresent(links, item.getAdmissionSiteUrl());
            addIfPresent(links, item.getSchoolOfficialUrl());
            review.setEvidenceLinks(links);
            list.add(review);
        }
        return list;
    }

    private String buildDataQualityWarning(List<VolunteerService.VolunteerItem> items, int specialExcluded) {
        List<String> warnings = new ArrayList<>();
        if (items.size() < TARGET_TOTAL) {
            warnings.add(String.format(
                    "当前仅找到 %d 个公开可核验院校专业组，未达到 %d 个；系统不会用低可信数据补满，差额请按数据补齐流程继续导入官方 / 学校官网公开来源。",
                    items.size(), TARGET_TOTAL));
        }
        if (specialExcluded > 0) {
            warnings.add(String.format("已默认排除%d条提前批、专项、军警公安、定向、预科、艺术体育等普通批不适用记录。", specialExcluded));
        }
        long missingPlan = items.stream().filter(item -> item.isNeedsManualReview()).count();
        if (missingPlan > 0) {
            warnings.add(String.format("%d个院校专业组缺计划数或未结构化，需复核省级考试院专业目录和学校招生官网。", missingPlan));
        }
        return warnings.isEmpty() ? null : String.join(" ", warnings);
    }

    private RankResolution resolveRankResolution(ProvincePolicyService.ProvincePolicy policy,
                                                 VolunteerService.GenerateRequest req,
                                                 String subjectType) {
        int submittedRank = req.getProvinceRank();
        AlgorithmService.RankEstimate estimate = provinceRankService.estimateRank(
                policy.getProvinceCode(), req.getTotalScore(), subjectType, null);
        if (submittedRank > 0) {
            return new RankResolution(submittedRank, submittedRank,
                    buildRankEstimateSummary(policy, req, subjectType, estimate, submittedRank, false));
        }
        if (estimate == null || estimate.getDataPoints() < 1 || estimate.getEstimatedRank() <= 0) {
            throw new BizException(String.format(
                    "未填写全省位次，且%s%s官方一分一段表暂不可用，无法按分数估算位次；请手动填写考试院确认的全省位次。",
                    policy.getProvinceName(), subjectType));
        }
        int effectiveRank = Math.max(1, estimate.getEstimatedRank());
        return new RankResolution(null, effectiveRank,
                buildRankEstimateSummary(policy, req, subjectType, estimate, effectiveRank, true));
    }

    private VolunteerService.RankEstimateSummary buildRankEstimateSummary(ProvincePolicyService.ProvincePolicy policy,
                                                                          VolunteerService.GenerateRequest req,
                                                                          String subjectType,
                                                                          AlgorithmService.RankEstimate estimate,
                                                                          int effectiveRank,
                                                                          boolean rankEstimated) {
        VolunteerService.RankEstimateSummary summary = new VolunteerService.RankEstimateSummary();
        summary.setProvinceCode(policy.getProvinceCode());
        summary.setProvinceName(policy.getProvinceName());
        summary.setSubjectType(subjectType);
        summary.setTotalScore(req.getTotalScore());
        summary.setSubmittedRank(req.getProvinceRank() > 0 ? req.getProvinceRank() : null);
        summary.setEffectiveRank(effectiveRank);
        summary.setRankEstimated(rankEstimated);
        if (estimate != null) {
            summary.setEstimatedRank(estimate.getEstimatedRank() > 0 ? estimate.getEstimatedRank() : null);
            summary.setRankLow(estimate.getRankLow() > 0 ? estimate.getRankLow() : null);
            summary.setRankHigh(estimate.getRankHigh() > 0 ? estimate.getRankHigh() : null);
            summary.setReferenceYear(estimate.getReferenceYear() > 0 ? estimate.getReferenceYear() : null);
            summary.setOfficialDataReady(estimate.getDataPoints() > 0);
            summary.setSourceName(estimate.getSource());
            summary.setSourceUrl(estimate.getSourceUrl());
            summary.setSourcePageUrl(estimate.getSourcePageUrl());
            summary.setParseMethod(estimate.getParseMethod());
            summary.setNote(estimate.getNote());
            if (req.getProvinceRank() > 0 && estimate.getDataPoints() > 0) {
                int low = Math.max(1, estimate.getRankLow());
                int high = Math.max(low, estimate.getRankHigh());
                summary.setMatched(req.getProvinceRank() >= low && req.getProvinceRank() <= high);
            }
        }
        summary.setReminder(rankEstimated
                ? String.format("未手填全省位次，本次采用官方同分区间保守位次第%,d名生成；正式填报必须以%s原始一分一段表和成绩单核对。",
                effectiveRank, policy.getOfficialSourceName())
                : String.format("本次按你填写的全省位次第%,d名生成；分数位次校验只作一致性提醒，最终以%s为准。",
                effectiveRank, policy.getOfficialSourceName()));
        return summary;
    }

    private Map<String, Object> buildRequestSnapshot(VolunteerService.GenerateRequest req,
                                                     VolunteerService.GradientRangeSummary rangeSummary) {
        return buildRequestSnapshot(req, rangeSummary, null);
    }

    private Map<String, Object> buildRequestSnapshot(VolunteerService.GenerateRequest req,
                                                     VolunteerService.GradientRangeSummary rangeSummary,
                                                     VolunteerService.RankEstimateSummary rankEstimate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("provinceCode", safeText(req.getProvinceCode()));
        snapshot.put("volunteerUnitType", ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        snapshot.put("targetBatch", rangeSummary.getStrategyMode());
        snapshot.put("targetCount", TARGET_TOTAL);
        snapshot.put("totalScore", req.getTotalScore());
        snapshot.put("provinceRank", req.getProvinceRank());
        snapshot.put("firstSubject", req.getFirstSubject());
        snapshot.put("resubjects", req.getResubjects() == null ? List.of() : req.getResubjects());
        snapshot.put("preferredMajors", req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors());
        snapshot.put("preferredRegions", req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions());
        snapshot.put("agreedDisclaimer", Boolean.TRUE.equals(req.getAgreedDisclaimer()));
        snapshot.put("disclaimerVersion", safeText(req.getDisclaimerVersion()));
        snapshot.put("gradientRangeSummary", rangeSummary);
        if (rankEstimate != null) {
            snapshot.put("rankEstimate", rankEstimate);
        }
        return snapshot;
    }

    private void validate(VolunteerService.GenerateRequest req) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        if (req.getTotalScore() <= 0 || req.getTotalScore() > 750) {
            throw new BizException("高考总分必须在1-750之间");
        }
        if (!"物理".equals(req.getFirstSubject()) && !"历史".equals(req.getFirstSubject())) {
            throw new BizException("首选科目必须为物理或历史");
        }
        if (req.getResubjects() == null || req.getResubjects().size() != 2) {
            throw new BizException("再选科目必须选择2门");
        }
        if (!Boolean.TRUE.equals(req.getAgreedDisclaimer())
                || !ComplianceConstants.DISCLAIMER_VERSION.equals(safeText(req.getDisclaimerVersion()))) {
            throw new BizException(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
        }
    }

    private String specialTypeReason(DataAdmissionGroupLine line) {
        String text = safeText(line.getUniversityName()) + " " + safeText(line.getGroupName()) + " " + safeText(line.getBatch());
        if (containsAny(text, "提前批", "专项", "公费师范", "免费师范", "优师", "军校", "公安", "警察", "定向",
                "预科", "民族班", "艺术", "美术", "音乐", "舞蹈", "体育", "只招男", "只招女", "仅招男", "仅招女")) {
            return "特殊类型不进入普通本科批主列表";
        }
        return "";
    }

    private boolean matchResubject(String requirement, List<String> resubjects) {
        String req = safeText(requirement);
        if (req.isBlank() || req.contains("不限")) {
            return true;
        }
        if (resubjects == null || resubjects.isEmpty()) {
            return false;
        }
        if (req.contains("和")) {
            String[] parts = req.split("和");
            for (String part : parts) {
                if (!resubjects.contains(part.trim())) return false;
            }
            return true;
        }
        if (req.contains("或")) {
            String[] parts = req.split("或");
            for (String part : parts) {
                if (resubjects.contains(part.trim())) return true;
            }
            return false;
        }
        for (String subject : resubjects) {
            if (req.contains(subject)) return true;
        }
        return false;
    }

    private String buildExplanation(ProvincePolicyService.ProvincePolicy policy, VolunteerService.VolunteerItem item, int studentRank, DataAdmissionGroupLine line) {
        return String.format(
                "%s按院校专业组投档，本条按%s档区间筛入；专业组调档位次第%,d位，与考生采用位次相差%,d位（%.1f%%）；计划因素：%s；扩招指数%s，招生供给指数%s，精度分%d。数据来源为%s，来源层级%s。组内最多展示6个专业，是否服从调剂需由考生结合官方专业目录自主决定。本建议仅供参考。",
                policy.getProvinceName(),
                item.getGradient(),
                item.getHistoryMinRank(),
                item.getHistoryMinRank() - studentRank,
                item.getRankGapRatio(),
                defaultText(item.getPlanRiskNote(), "计划数暂缺，需复核"),
                item.getPlanExpansionIndex() > 0 ? String.format("%.1f", item.getPlanExpansionIndex()) : "待核验",
                item.getSchoolEnrollmentIndex() > 0 ? String.format("%.0f（%s）", item.getSchoolEnrollmentIndex(), item.getSchoolEnrollmentLabel()) : "待核验",
                item.getPrecisionScore(),
                defaultText(line.getSourceName(), "官方/学校官网公开数据"),
                defaultText(line.getSourceLevel(), "需复核"));
    }

    private VolunteerService.AdvisorAdvice buildAdvisorAdvice(ProvincePolicyService.ProvincePolicy policy,
                                                              VolunteerService.PlanResult result) {
        List<VolunteerService.VolunteerItem> items = result.getItems() == null ? List.of() : result.getItems();
        long missingPlan = items.stream().filter(item -> "计划数暂缺".equals(item.getPlanTrend())).count();
        long smallPlan = items.stream().filter(item -> item.getLatestPlanCount() != null
                && item.getLatestPlanCount() > 0
                && item.getLatestPlanCount() <= 3).count();
        VolunteerService.AdvisorAdvice advice = new VolunteerService.AdvisorAdvice();
        advice.setTitle(policy.getProvinceName() + "张雪峰.skill 报考建议");
        advice.setPositioning(String.format("以%s官方位次第%,d名为边界建立院校专业组可行集；数据未达到门槛的省份继续锁定，不用低可信数据补齐。",
                policy.getProvinceName(), result.getProvinceRank()));
        advice.setPriorityAdvice("院校专业组模式下，同一学校不同专业组差异很大，优先用就业倒推和中位数原则比较组内专业、选科要求、学费和调剂风险，而不是只看学校名称。");
        advice.setGradientAdvice(String.format("当前按%s设置45个专业组梯度，冲稳保垫必须保持顺序；保底组应优先选择计划数清楚、来源可追溯的条目。", policy.getTargetBatch()));
        advice.setCityAdvice("若未来就业地明确，优先保留目标城市或邻近省会的稳/保专业组；外地强校需结合就业质量报告、校招范围和普通毕业生去向判断。");
        advice.setMajorAdvice("组内专业只展示已结构化导入的公开数据，最终必须打开专业目录核对完整专业、学费、体检、语种和单科限制。");
        advice.setPlanChangeAdvice(String.format("招生计划信号：计划暂缺%d项、小计划数%d项。计划缺失和小计划数都会放大位次波动，必须人工复核。", missingPlan, smallPlan));
        advice.setRiskChecklist(List.of(
                "院校专业组进档后仍存在组内专业分配和调剂风险，不能把调档位次等同于目标专业录取位次。",
                "计划数暂缺的专业组只适合作为待查项，需核对省考试院专业目录或高校招生官网。",
                "选科要求、体检、语种、单科成绩、收费和校区差异必须逐条确认。",
                "本建议只基于已导入可核验公开数据，不构成录取承诺。"
        ));
        advice.setActionItems(List.of(
                "先核对官方一分一段位次和当前批次规则。",
                "逐条打开来源链接，确认专业组线、组内专业和招生计划。",
                "把计划数清楚、来源可靠的稳/保组标为保留。",
                "对计划缺失或专业目录不清的条目标为待查或淘汰。",
                "最终按考试院填报系统和高校招生章程执行。"
        ));
        advice.setSourceNote(VolunteerService.ADVISOR_SOURCE_NOTE);
        advice.setSourceProjectName(VolunteerService.ADVISOR_SOURCE_PROJECT_NAME);
        advice.setSourceProjectUrl(VolunteerService.ADVISOR_SOURCE_PROJECT_URL);
        return advice;
    }

    private String buildReferenceNotice(ProvincePolicyService.ProvincePolicy policy) {
        return String.format(
                "本系统输出为%s%s院校专业组草稿，仅基于已导入且可核验的官方/学校官网公开数据整理。参考匹配不是录取预测，不构成录取承诺；最终以%s、招生专业目录、院校招生章程和考生本人决策为准。",
                policy.getProvinceName(), policy.getTargetBatch(), policy.getOfficialSourceName());
    }

    private String batchKeyword(String targetBatch) {
        String value = safeText(targetBatch);
        return value.contains("本科") ? "本科" : value;
    }

    private int calcConfidenceScore(DataAdmissionGroupLine line) {
        int score = 55;
        if (line.getYear() != null && line.getYear() >= 2025) score += 15;
        if (line.getMinRank() != null && line.getMinRank() > 0) score += 10;
        if (line.getPlanCount() != null && line.getPlanCount() > 0) score += 10;
        if (!safeText(line.getSourceUrl()).isBlank() || !safeText(line.getSourcePageUrl()).isBlank()) score += 10;
        if ("official".equalsIgnoreCase(safeText(line.getSourceLevel()))) score += 8;
        return Math.min(100, score);
    }

    private int calcPrecisionScore(VolunteerService.VolunteerItem item) {
        double score = item.getDataConfidenceScore() * 0.55;
        score += switch (safeText(item.getReferenceFitLevel())) {
            case "较高" -> 18;
            case "中等" -> 12;
            case "偏低" -> 5;
            default -> 0;
        };
        score += item.getSchoolEnrollmentIndex() * 0.20;
        if (item.getPlanExpansionIndex() <= 0) {
            score -= 8;
        }
        if (item.isNeedsManualReview()) {
            score -= 10;
        }
        if (item.getLatestPlanCount() != null && item.getLatestPlanCount() > 0 && item.getLatestPlanCount() <= 3) {
            score -= 6;
        }
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    private String resolvePrecisionLabel(int score) {
        if (score >= 82) return "精度较高";
        if (score >= 65) return "精度中等";
        if (score >= 45) return "精度偏低";
        return "必须复核";
    }

    private String buildPrecisionNote(VolunteerService.VolunteerItem item) {
        return String.format("精度分%d：综合专业组调档位次、计划数、来源层级和供给指数；该分数只衡量推荐依据完整度，不代表录取承诺。",
                item.getPrecisionScore());
    }

    private String resolveSupplyLabel(double supplyIndex) {
        if (supplyIndex >= 80) return "招生供给强";
        if (supplyIndex >= 60) return "供给中上";
        if (supplyIndex >= 45) return "供给中等";
        if (supplyIndex > 0) return "供给偏紧";
        return "供给待核验";
    }

    private String resolveConfidence(DataAdmissionGroupLine line) {
        if ("official".equalsIgnoreCase(safeText(line.getSourceLevel())) && line.getYear() != null && line.getYear() >= 2025) {
            return "高可信";
        }
        if (line.getYear() != null && line.getYear() >= 2025) {
            return "中可信";
        }
        return "需复核";
    }

    private String resolveReferenceFitLevel(String gradient) {
        return switch (safeText(gradient)) {
            case "保", "垫" -> "较高";
            case "稳" -> "中等";
            default -> "偏低";
        };
    }

    private int gradientOrder(String gradient) {
        int idx = GRADIENT_ORDER.indexOf(gradient);
        return idx < 0 ? 99 : idx;
    }

    private String mapSubjectType(String firstSubject) {
        return "历史".equals(firstSubject) ? "历史类" : "物理类";
    }

    private boolean containsAny(String text, String... keywords) {
        String value = safeText(text);
        for (String key : keywords) {
            if (!safeText(key).isBlank() && value.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private void addIfPresent(List<String> links, String url) {
        if (!safeText(url).isBlank() && !links.contains(url)) {
            links.add(url);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!safeText(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String defaultText(String value, String fallback) {
        return safeText(value).isBlank() ? fallback : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record RankResolution(Integer submittedRank, int effectiveRank,
                                  VolunteerService.RankEstimateSummary summary) {
    }

    private record PickResult(List<VolunteerService.VolunteerItem> items, int specialExcludedCount) {
    }
}
