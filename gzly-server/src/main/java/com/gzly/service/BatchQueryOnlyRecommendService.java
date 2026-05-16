package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.recommend.RecommendEngineDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchQueryOnlyRecommendService {

    @Autowired(required = false)
    private BatchListingRecommendationService batchListingRecommendationService;

    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req,
                                                PolicyRuleConfig policy,
                                                BatchRuleRegistry.BatchRule rule,
                                                String reason) {
        return generate(req, policy, rule, reason, null);
    }

    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req,
                                                PolicyRuleConfig policy,
                                                BatchRuleRegistry.BatchRule rule,
                                                String reason,
                                                RecommendEngineDecision decision) {
        VolunteerService.PlanResult result = new VolunteerService.PlanResult();
        result.setId(0L);
        result.setProvinceCode(req == null || req.getProvinceCode() == null ? "GZ" : req.getProvinceCode());
        result.setProvinceName("贵州");
        result.setVolunteerUnitType(rule.recommendMode().name());
        result.setVolunteerUnitLabel(rule.volunteerMode());
        result.setTargetBatch(rule.batchName());
        result.setTargetCount(rule.targetCount());
        result.setTotalScore(req == null ? 0 : req.getTotalScore());
        result.setProvinceRank(req == null ? 0 : req.getProvinceRank());
        result.setFirstSubject(req == null ? "" : safe(req.getFirstSubject()));
        result.setResubjects(req == null || req.getResubjects() == null ? List.of() : req.getResubjects());
        result.setPreferredMajors(req == null || req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors());
        result.setPreferredRegions(req == null || req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions());
        result.setStrategyMode(req == null ? "" : safe(req.getStrategyMode()));
        result.setDecisionPriority(req == null ? "" : safe(req.getDecisionPriority()));
        result.setCareerGoal(req == null ? "" : safe(req.getCareerGoal()));
        result.setTuitionBudget(req == null ? "" : safe(req.getTuitionBudget()));
        result.setAcceptPrivate(req == null || !Boolean.FALSE.equals(req.getAcceptPrivate()));
        result.setAcceptSinoForeign(req != null && Boolean.TRUE.equals(req.getAcceptSinoForeign()));
        // 非主链路批次：尝试用 BatchListingRecommendationService 从已导入的官方数据里拉一份可查询列表。
        // 没数据时仍返回空 items，并把缺口写到 dataQualityWarning，避免回归。
        List<VolunteerService.VolunteerItem> listingItems = List.of();
        String listingMessage = null;
        List<String> listingWarnings = List.of();
        if (batchListingRecommendationService != null && decision != null) {
            BatchListingRecommendationService.BatchListingResult listing =
                    batchListingRecommendationService.listForBatch(req, policy, decision);
            if (listing != null) {
                listingItems = listing.items() == null ? List.of() : listing.items();
                listingMessage = listing.message();
                listingWarnings = listing.warnings() == null ? List.of() : listing.warnings();
            }
        }
        result.setItems(listingItems);
        result.setCreatedAt(LocalDateTime.now().toString());
        String mergedReason = reason == null || reason.isBlank() ? rule.supportNote() : reason;
        if (listingMessage != null && !listingMessage.isBlank()) {
            mergedReason = mergedReason == null || mergedReason.isBlank()
                    ? listingMessage
                    : mergedReason + " " + listingMessage;
        }
        result.setDataQualityWarning(mergedReason);
        result.setManualReviewItems(List.of());
        VolunteerService.PlanMetrics metrics = new VolunteerService.PlanMetrics();
        metrics.setTotalCount(listingItems.size());
        metrics.setTargetCount(rule.targetCount());
        metrics.setProvinceCode(result.getProvinceCode());
        metrics.setVolunteerUnitType(rule.recommendMode().name());
        metrics.setGeneratedAtMs(System.currentTimeMillis());
        result.setMetrics(metrics);
        List<String> warnings = buildWarnings(rule, reason);
        for (String warning : listingWarnings) {
            if (warning != null && !warning.isBlank() && !warnings.contains(warning)) {
                warnings.add(warning);
            }
        }
        result.setWarnings(warnings);
        result.setModelInfo(modelInfo(rule));
        result.setPolicy(policyMap(policy, rule));
        result.setSupportLevel(rule.baseSupportLevel().name());
        result.setRecommendMode(rule.recommendMode().name());
        result.setEngineName(rule.engine());
        result.setSupportReason(result.getDataQualityWarning());
        return result;
    }

    private static List<String> buildWarnings(BatchRuleRegistry.BatchRule rule, String reason) {
        List<String> warnings = new ArrayList<>();
        warnings.add(rule.supportNote());
        if (reason != null && !reason.isBlank() && !warnings.contains(reason)) {
            warnings.add(reason);
        }
        return warnings;
    }

    private static Map<String, Object> modelInfo(BatchRuleRegistry.BatchRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("engine", rule.engine());
        map.put("recommendMode", rule.recommendMode().name());
        map.put("fallbackUsed", true);
        map.put("visibleMetric", "query_only");
        map.put("appliedCount", 0);
        return map;
    }

    private static Map<String, Object> policyMap(PolicyRuleConfig policy, BatchRuleRegistry.BatchRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (policy != null) {
            map.put("id", policy.getId());
            map.put("province", policy.getProvince());
            map.put("year", policy.getYear());
            map.put("candidateType", policy.getCandidateType());
            map.put("batchCode", policy.getBatchCode());
            map.put("batchName", policy.getBatchName());
            map.put("volunteerMode", policy.getVolunteerMode());
            map.put("maxVolunteerCount", policy.getMaxVolunteerCount());
            map.put("policyStatus", policy.getPolicyStatus());
            map.put("officialSourceTitle", policy.getOfficialSourceTitle());
            map.put("officialSourceUrl", policy.getOfficialSourceUrl());
        }
        map.put("supportLevel", rule.baseSupportLevel().name());
        map.put("recommendMode", rule.recommendMode().name());
        map.put("engine", rule.engine());
        map.put("engineName", rule.engine());
        map.put("supportNote", rule.supportNote());
        return map;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
