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

    private static final List<String> GRADIENT_ORDER = List.of("冲", "稳", "保", "垫");
    /** 与 VolunteerService.PORTFOLIO_SAFETY_THRESHOLD 同口径。 */
    private static final double PORTFOLIO_SAFETY_THRESHOLD = 98.0;

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
    private final com.gzly.algorithm.ProfessionalGroupAlgorithmEnricher algorithmEnricher;

    // 原方法级 @Transactional 已移除：生成过程只有末尾一次 plan_history 单条 INSERT（自带原子性），
    // 事务包住全部查询与算法计算只会长时间占用连接；幂等保护由 RecommendationOrchestrator 的请求锁承担。
    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req, Long userId, String clientIp) {
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(
                req == null ? null : req.getProvinceCode());
        validate(policy, req);
        long startedAt = System.currentTimeMillis();
        metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_TOTAL);
        try {
            if (!ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45.equals(policy.getVolunteerUnitType())) {
                throw new BizException("该省份不适用院校专业组45志愿生成策略");
            }
            String provinceCode = policy.getProvinceCode();
            String subjectType = resolveSubjectTrack(policy, req);
            RankResolution rankResolution = resolveRankResolution(policy, req, subjectType);
            req.setProvinceRank(rankResolution.effectiveRank());
            Integer rankYear = dataScoreRankMapper.selectLatestYear(provinceCode, subjectType);
            if (rankYear == null || rankYear < 2025) {
                throw new BizException(String.format("%s官方一分一段表尚未导入或未通过核验：请先导入2025年%s（%s）官方一分一段后再开放生成。",
                        policy.getProvinceName(), policy.getProvinceName(), String.join("/", policy.getSubjectTypes())));
            }
            Integer year = groupLineMapper.selectLatestYear(provinceCode, subjectType);
            if (year == null) {
                throw new BizException(String.format("%s%s数据尚未导入：请先导入2025年%s一分一段、院校专业组计划与调档线后再开放生成。",
                        policy.getProvinceName(), policy.getTargetBatch(), policy.getProvinceName()));
            }

            // 志愿总数与梯度分配改为政策驱动（各省 30-48 不等），策略模式（保守/均衡/冲刺）生效。
            int targetTotal = resolveTargetTotal(policy, req);
            String strategyMode = defaultText(req.getStrategyMode(), "均衡型");
            Map<String, Integer> gradientCounts = resolveGradientCounts(req, targetTotal, strategyMode);

            VolunteerService.GradientRangeSummary rangeSummary = buildRangeSummary(
                    policy, rankResolution.effectiveRank(), strategyMode, gradientCounts);
            List<VolunteerService.VolunteerItem> items = new ArrayList<>();
            int specialExcluded = 0;
            for (String gradient : GRADIENT_ORDER) {
                VolunteerService.GradientRangeDetail range = rangeSummary.getRanges().get(gradient);
                PickResult picked = pickGradient(policy, req, subjectType, year, gradient, range);
                items.addAll(picked.items());
                specialExcluded += picked.specialExcludedCount();
                range.setActualCount(picked.items().size());
            }

            if (items.size() < targetTotal) {
                metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_INCOMPLETE);
                throw new BizException(String.format(
                        "%s%s公开可核验院校专业组数据不足：当前只命中%d个，未达到%d个。系统不会用低可信或无来源数据补满，请先补齐%s院校专业组计划和调档线。",
                        policy.getProvinceName(), policy.getTargetBatch(), items.size(), targetTotal, policy.getProvinceName()));
            }

            // 批量装载组内专业与历史线（机会指数的位次特征依赖 historyRecords，必须先于富集）
            hydrateGroupDetails(policy, subjectType, year, items);

            // ── 阶段 2 并线：接入共享算法层（机会指数 / 意向匹配 / 策略化排序 / 诊断 / 整表安全度） ──
            com.gzly.algorithm.ProfessionalGroupAlgorithmEnricher.Outcome enriched =
                    algorithmEnricher.enrich(items, req, rankResolution.effectiveRank(), targetTotal, subjectType);
            items = new ArrayList<>(enriched.getItems());

            // 概率定档可能使各档数量偏离预设（冲空/保爆），如实统计并在梯度说明中标注
            if (enriched.getGradientReclassifiedCount() > 0) {
                rangeSummary.setExplanation(rangeSummary.getExplanation() + String.format(
                        " 其中%d条按校准录取概率重新定档（概率带：垫≥95%%/保≥85%%/稳≥55%%），各档实际数量以列表为准，可能与预设比例有偏离。",
                        enriched.getGradientReclassifiedCount()));
            }

            List<VolunteerService.ManualReviewItem> manualReviewItems = buildManualReviewList(items);
            VolunteerService.PlanMetrics metrics = buildMetrics(items, manualReviewItems, specialExcluded,
                    targetTotal, strategyMode, enriched.getPortfolioSafety());
            metrics.setGradientReclassifiedCount(enriched.getGradientReclassifiedCount());
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
            history.setDataQualityWarning(appendPortfolioSafetyWarning(
                    buildDataQualityWarning(items, specialExcluded), enriched.getPortfolioSafety()));
            SafetyCodeService.SafetyCodeIssue safetyCodeIssue = safetyCodeService.issue(req.getSafetyCode());
            history.setSafetyCodeHash(safetyCodeIssue.safetyCodeHash());
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
            result.setTargetCount(targetTotal);
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
            result.setDiagnosis(enriched.getDiagnosis());

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
        return pickGradient(policy, req, subjectType, year, gradient, range, Math.max(0, range.getTargetCount()));
    }

    private PickResult pickGradient(ProvincePolicyService.ProvincePolicy policy, VolunteerService.GenerateRequest req, String subjectType, int year,
                                    String gradient, VolunteerService.GradientRangeDetail range, int targetCount) {
        List<DataAdmissionGroupLine> candidates = groupLineMapper.selectCandidates(
                policy.getProvinceCode(),
                year,
                subjectType,
                range.getRankLow(),
                range.getRankHigh(),
                batchKeyword(policy.getTargetBatch()),
                targetCount * 8 + 30);
        Map<String, VolunteerService.VolunteerItem> deduped = new LinkedHashMap<>();
        int specialExcluded = 0;
        for (DataAdmissionGroupLine line : candidates) {
            String specialReason = specialTypeReason(line);
            if (!specialReason.isBlank()) {
                specialExcluded++;
                continue;
            }
            if (!subjectRequirementMatches(policy, line, req)) {
                continue;
            }
            String key = safeText(line.getSchoolId()) + "|" + safeText(line.getGroupCode());
            deduped.putIfAbsent(key, toVolunteerItem(policy, line, gradient, req.getProvinceRank(), range));
        }
        List<VolunteerService.VolunteerItem> items = deduped.values().stream()
                .sorted(Comparator.comparingInt(VolunteerService.VolunteerItem::getHistoryMinRank))
                .limit(targetCount)
                .toList();
        return new PickResult(items, specialExcluded);
    }

    /**
     * 候选检索缝合口（阶段 1，供 ProfessionalGroupProvider 委派调用）。
     * 与主链路 pickGradient 完全同一实现，仅将梯度目标数改由调用方显式给出；
     * 数据未导入（无可用年份）时返回空列表而不抛异常，由上层就绪度门禁负责拦截。
     */
    public List<VolunteerService.VolunteerItem> fetchCandidates(VolunteerService.GenerateRequest req, String gradient,
                                                                int rankLow, int rankHigh, int maxCount) {
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(req.getProvinceCode());
        String subjectType = mapSubjectType(req.getFirstSubject());
        Integer year = groupLineMapper.selectLatestYear(policy.getProvinceCode(), subjectType);
        if (year == null) {
            return List.of();
        }
        VolunteerService.GradientRangeDetail range = new VolunteerService.GradientRangeDetail();
        range.setGradient(gradient);
        range.setRankLow(rankLow);
        range.setRankHigh(rankHigh);
        range.setTargetCount(maxCount);
        List<VolunteerService.VolunteerItem> items = pickGradient(policy, req, subjectType, year, gradient, range, maxCount).items();
        hydrateGroupDetails(policy, subjectType, year, items);
        return items;
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
        // groupMajors / historyRecords 由 hydrateGroupDetails 在选完后批量装载（替代逐条查询的 N+1）
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

    /**
     * 批量装载组内专业与历史线（阶段 4：替代 toVolunteerItem 内逐条查询的 N+1，
     * 45 条志愿从约 90 次查询降为 2 次 IN 批量查询）。必须在算法富集之前调用——
     * 机会指数的位次特征消费 historyRecords。
     */
    private void hydrateGroupDetails(ProvincePolicyService.ProvincePolicy policy, String subjectType, int year,
                                     List<VolunteerService.VolunteerItem> items) {
        List<Map<String, String>> pairs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (VolunteerService.VolunteerItem item : items) {
            String schoolId = safeText(item.getSchoolId());
            String groupCode = safeText(item.getGroupCode());
            if (schoolId.isBlank() || groupCode.isBlank()) {
                continue;
            }
            if (seen.add(schoolId + "|" + groupCode)) {
                pairs.add(Map.of("schoolId", schoolId, "groupCode", groupCode));
            }
        }
        Map<String, List<VolunteerService.HistoryRecord>> historyByKey = new LinkedHashMap<>();
        Map<String, List<String>> majorsByKey = new LinkedHashMap<>();
        int displayLimit = Math.max(6, policy.getMajorPerGroupCount());
        if (!pairs.isEmpty()) {
            try {
                for (DataAdmissionGroupLine line : groupLineMapper.selectRecentHistoryBatch(
                        policy.getProvinceCode(), subjectType, pairs, 3)) {
                    historyByKey.computeIfAbsent(safeText(line.getSchoolId()) + "|" + safeText(line.getGroupCode()),
                            k -> new ArrayList<>()).add(toHistoryRecord(line));
                }
                Map<String, List<String>> rawMajors = new LinkedHashMap<>();
                for (DataAdmissionGroupPlan plan : groupPlanMapper.selectGroupMajorsBatch(
                        policy.getProvinceCode(), year, subjectType, pairs)) {
                    String name = safeText(plan.getMajorName());
                    if (name.isBlank()) {
                        continue;
                    }
                    rawMajors.computeIfAbsent(safeText(plan.getSchoolId()) + "|" + safeText(plan.getGroupCode()),
                            k -> new ArrayList<>()).add(name);
                }
                rawMajors.forEach((key, names) -> majorsByKey.put(key,
                        names.stream().distinct().limit(displayLimit).toList()));
            } catch (Exception e) {
                log.warn("组内专业/历史线批量装载失败，条目将以空明细展示: {}", e.getMessage());
            }
        }
        for (VolunteerService.VolunteerItem item : items) {
            String key = safeText(item.getSchoolId()) + "|" + safeText(item.getGroupCode());
            item.setHistoryRecords(historyByKey.getOrDefault(key, List.of()));
            item.setGroupMajors(majorsByKey.getOrDefault(key, List.of()));
        }
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

    /**
     * 位次比例区间随策略模式变化，与贵州主链路 / BacktestService 的比例预设同一组数值；
     * 均衡型即重构前的固定区间（0.65/0.95/1.20/1.80/3.00），行为向后兼容。
     */
    private VolunteerService.GradientRangeSummary buildRangeSummary(ProvincePolicyService.ProvincePolicy policy, int rank,
                                                                    String strategyMode, Map<String, Integer> gradientCounts) {
        double[][] ratios = ratioPreset(strategyMode);
        Map<String, VolunteerService.GradientRangeDetail> ranges = new LinkedHashMap<>();
        for (int i = 0; i < GRADIENT_ORDER.size(); i++) {
            String gradient = GRADIENT_ORDER.get(i);
            putRange(policy, ranges, gradient, rank, ratios[i][0], ratios[i][1],
                    gradientCounts.getOrDefault(gradient, 0));
        }
        VolunteerService.GradientRangeSummary summary = new VolunteerService.GradientRangeSummary();
        summary.setSource(policy.getProvinceCode().toLowerCase(Locale.ROOT) + "_professional_group_policy_preset");
        summary.setStrategyMode(strategyMode);
        summary.setRanges(ranges);
        int total = gradientCounts.values().stream().mapToInt(Integer::intValue).sum();
        summary.setExplanation(String.format(
                "%s按院校专业组作为志愿单位。本次按用户手填官方位次和%s策略设置冲%d、稳%d、保%d、垫%d共%d个专业组梯度；候选必须有官方/学校官网来源链接，数据不足不补假数据。",
                policy.getProvinceName(), strategyMode,
                gradientCounts.getOrDefault("冲", 0), gradientCounts.getOrDefault("稳", 0),
                gradientCounts.getOrDefault("保", 0), gradientCounts.getOrDefault("垫", 0), total));
        return summary;
    }

    private double[][] ratioPreset(String strategyMode) {
        // 数值单源：GradientAllocationEngine.ratioPreset（消除三份副本）。
        return com.gzly.algorithm.GradientAllocationEngine.ratioPreset(strategyMode);
    }

    private void putRange(ProvincePolicyService.ProvincePolicy policy, Map<String, VolunteerService.GradientRangeDetail> ranges, String gradient,
                          int rank, double minRatio, double maxRatio, int targetCount) {
        VolunteerService.GradientRangeDetail detail = new VolunteerService.GradientRangeDetail();
        detail.setGradient(gradient);
        detail.setRankRatioMin(minRatio);
        detail.setRankRatioMax(maxRatio);
        detail.setRankLow(Math.max(1, (int) Math.round(rank * minRatio)));
        detail.setRankHigh(Math.max(detail.getRankLow(), (int) Math.round(rank * maxRatio)));
        detail.setRankOffsetMin(detail.getRankLow() - rank);
        detail.setRankOffsetMax(detail.getRankHigh() - rank);
        detail.setTargetCount(targetCount);
        detail.setLabel(String.format("%s：第%,d ~ %,d位", gradient, detail.getRankLow(), detail.getRankHigh()));
        detail.setRangeSourceNote(policy.getProvinceName() + "按院校专业组投档位次比例区间筛选。");
        ranges.put(gradient, detail);
    }

    /**
     * 志愿总数：政策库下发值优先（各省 30-48 不等），缺省回退省份内置口径。
     * 云南官方规则：本科批基础 40 个，符合国家/地方/高校专项计划条件 +10 个、
     * 符合少数民族预科条件 +10 个（2026 年考试院填报须知，文档 11.7）。
     */
    private int resolveTargetTotal(ProvincePolicyService.ProvincePolicy policy, VolunteerService.GenerateRequest req) {
        int base = req.getPolicyMaxVolunteerCount() != null && req.getPolicyMaxVolunteerCount() > 0
                ? req.getPolicyMaxVolunteerCount()
                : policy.getTargetCount();
        if (ProvincePolicyService.YN.equals(policy.getProvinceCode())) {
            List<String> tags = req.getQualificationTags() == null ? List.of() : req.getQualificationTags();
            boolean specialProgram = tags.stream().filter(Objects::nonNull)
                    .anyMatch(t -> t.contains("国家专项") || t.contains("地方专项") || t.contains("高校专项") || t.contains("专项计划"));
            boolean minorityPrep = tags.stream().filter(Objects::nonNull)
                    .anyMatch(t -> t.contains("少数民族预科") || t.contains("预科"));
            if (specialProgram) {
                base += 10;
            }
            if (minorityPrep) {
                base += 10;
            }
        }
        return base;
    }

    /** 科类轨道：3+3（海南）统一"综合"，3+1+2 按首选科目映射物理类/历史类。 */
    private String resolveSubjectTrack(ProvincePolicyService.ProvincePolicy policy, VolunteerService.GenerateRequest req) {
        if (ProvincePolicyService.SUBJECT_MODE_33.equals(policy.getSubjectMode())) {
            return com.gzly.algorithm.core.ThreeThreeSubjectMatcher.TRACK_COMPREHENSIVE;
        }
        return mapSubjectType(req.getFirstSubject());
    }

    /**
     * 梯度目标数量：批次政策的 gradient_preset_json 优先（承载湖北 15/15/15、海南 10/10/10、
     * 云南 8/16/16 等官方建议比例），无预设时按策略模式走 GradientAllocationEngine 统一分配。
     */
    private Map<String, Integer> resolveGradientCounts(VolunteerService.GenerateRequest req, int targetTotal, String strategyMode) {
        Map<String, Integer> preset = parseGradientPreset(req.getPolicyGradientPresetJson(), targetTotal);
        if (preset != null) {
            return preset;
        }
        return com.gzly.algorithm.GradientAllocationEngine.allocateCounts(targetTotal, strategyMode);
    }

    /** 预设 JSON 形如 {"counts":{"冲":15,"稳":15,"保":11,"垫":4}}；总和不等于志愿总数时视为无效。 */
    private Map<String, Integer> parseGradientPreset(String presetJson, int targetTotal) {
        if (presetJson == null || presetJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(presetJson);
            com.fasterxml.jackson.databind.JsonNode counts = root.path("counts");
            if (!counts.isObject()) {
                return null;
            }
            Map<String, Integer> result = new LinkedHashMap<>();
            int sum = 0;
            for (String gradient : GRADIENT_ORDER) {
                int value = counts.path(gradient).asInt(0);
                if (value < 0) {
                    return null;
                }
                result.put(gradient, value);
                sum += value;
            }
            if (sum != targetTotal) {
                log.warn("gradient_preset_json 总和 {} 与志愿总数 {} 不一致，忽略预设", sum, targetTotal);
                return null;
            }
            return result;
        } catch (Exception e) {
            log.warn("gradient_preset_json 解析失败，回退策略分配: {}", e.getMessage());
            return null;
        }
    }

    private String appendPortfolioSafetyWarning(String warning,
                                                com.gzly.algorithm.ProfessionalGroupAlgorithmEnricher.PortfolioSafety safety) {
        if (safety == null || safety.probability() >= PORTFOLIO_SAFETY_THRESHOLD) {
            return warning;
        }
        String safetyWarning = safety.note() + " 建议减少冲档连续项，增加计划数清楚、来源可核验的保/兜底专业组。";
        if (warning == null || warning.isBlank()) {
            return safetyWarning;
        }
        return warning + " " + safetyWarning;
    }

    private VolunteerService.PlanMetrics buildMetrics(List<VolunteerService.VolunteerItem> items,
                                                      List<VolunteerService.ManualReviewItem> manualReviewItems,
                                                      int specialExcluded,
                                                      int targetTotal,
                                                      String strategyMode,
                                                      com.gzly.algorithm.ProfessionalGroupAlgorithmEnricher.PortfolioSafety portfolioSafety) {
        VolunteerService.PlanMetrics metrics = new VolunteerService.PlanMetrics();
        metrics.setProvinceCode(items.isEmpty() ? "" : safeText(items.get(0).getProvinceCode()));
        metrics.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        metrics.setTargetCount(targetTotal);
        metrics.setStrategyMode(strategyMode);
        if (portfolioSafety != null) {
            metrics.setPortfolioSafetyProbability(portfolioSafety.probability());
            metrics.setPortfolioSafetyLevel(portfolioSafety.level());
            metrics.setPortfolioSafetyNote(portfolioSafety.note());
            metrics.setSafeTailCount(portfolioSafety.safeTailCount());
        }
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
                                                     VolunteerService.GradientRangeSummary rangeSummary,
                                                     VolunteerService.RankEstimateSummary rankEstimate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("provinceCode", safeText(req.getProvinceCode()));
        snapshot.put("volunteerUnitType", ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        snapshot.put("targetBatch", defaultText(req.getPolicyBatchName(), ""));
        snapshot.put("strategyMode", rangeSummary.getStrategyMode());
        snapshot.put("targetCount", rangeSummary.getRanges() == null ? 0
                : rangeSummary.getRanges().values().stream()
                        .mapToInt(VolunteerService.GradientRangeDetail::getTargetCount).sum());
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

    /**
     * 按省份口径校验：海南为 3+3 + 标准分 900 制（分数区间 [100,900]、选考 3 门、无首选科目），
     * 其余省份为 3+1+2 + 原始分 750 制。
     */
    private void validate(ProvincePolicyService.ProvincePolicy policy, VolunteerService.GenerateRequest req) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        boolean standard900 = ProvincePolicyService.SCORE_SYSTEM_STANDARD_900.equals(policy.getScoreSystem());
        if (standard900) {
            if (req.getTotalScore() < 100 || req.getTotalScore() > 900) {
                throw new BizException(policy.getProvinceName() + "为标准分900分制，综合标准分必须在100-900之间");
            }
        } else if (req.getTotalScore() <= 0 || req.getTotalScore() > 750) {
            throw new BizException("高考总分必须在1-750之间");
        }
        if (ProvincePolicyService.SUBJECT_MODE_33.equals(policy.getSubjectMode())) {
            List<String> selected = req.getSelectedSubjects() != null && !req.getSelectedSubjects().isEmpty()
                    ? req.getSelectedSubjects() : req.getResubjects();
            long validCount = selected == null ? 0 : selected.stream()
                    .filter(s -> s != null && !s.isBlank()).distinct().count();
            if (validCount != 3) {
                throw new BizException(policy.getProvinceName() + "为3+3模式，须提供3门选考科目（selectedSubjects）");
            }
        } else {
            if (!"物理".equals(req.getFirstSubject()) && !"历史".equals(req.getFirstSubject())) {
                throw new BizException("首选科目必须为物理或历史");
            }
            if (req.getResubjects() == null || req.getResubjects().size() != 2) {
                throw new BizException("再选科目必须选择2门");
            }
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
        // 实现已收敛至 ThreeOneTwoSubjectMatcher（宽松语义）；与贵州主链路的严格语义差异见该类注释。
        return com.gzly.algorithm.core.ThreeOneTwoSubjectMatcher.matchesLenient(requirement, resubjects);
    }

    /**
     * 按省份选科模式路由：3+3（海南）用「要求科目集合 ⊆ 考生 3 门选科」判定，
     * 首选/再选两列要求文本合并判断；3+1+2 沿用再选宽松匹配。
     */
    private boolean subjectRequirementMatches(ProvincePolicyService.ProvincePolicy policy,
                                              DataAdmissionGroupLine line,
                                              VolunteerService.GenerateRequest req) {
        if (ProvincePolicyService.SUBJECT_MODE_33.equals(policy.getSubjectMode())) {
            List<String> selected = req.getSelectedSubjects() != null && !req.getSelectedSubjects().isEmpty()
                    ? req.getSelectedSubjects() : req.getResubjects();
            String combined = (safeText(line.getFirstSubjectRequirement()) + " "
                    + safeText(line.getResubjectRequirement())).trim();
            return com.gzly.algorithm.core.ThreeThreeSubjectMatcher.matches(combined, selected);
        }
        return matchResubject(line.getResubjectRequirement(), req.getResubjects());
    }

    private String buildExplanation(ProvincePolicyService.ProvincePolicy policy, VolunteerService.VolunteerItem item, int studentRank, DataAdmissionGroupLine line) {
        return String.format(
                "%s按院校专业组投档，本条按%s档区间筛入；专业组调档位次第%,d位，与考生采用位次相差%,d位（%.1f%%）；计划因素：%s；扩招指数%s，招生供给指数%s，精度分%d。数据来源为%s，来源层级%s。组内专业按本省志愿设置展示，是否服从调剂需由考生结合官方专业目录自主决定。本建议仅供参考。",
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
        advice.setGradientAdvice(String.format("当前按%s设置%d个专业组梯度，冲稳保垫必须保持顺序；保底组应优先选择计划数清楚、来源可追溯的条目。",
                policy.getTargetBatch(), result.getTargetCount()));
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
