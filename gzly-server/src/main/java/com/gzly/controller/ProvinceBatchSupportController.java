package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.DataReadinessService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvinceAlgorithmPolicyService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.mapper.PolicyRuleConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DataAccessException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/volunteer")
public class ProvinceBatchSupportController {
    private static final int DEFAULT_YEAR = 2026;
    private static final int LATEST_OFFICIAL_DATA_YEAR = 2025;
    private static final List<Integer> HISTORICAL_DATA_SOURCE_YEARS = List.of(2024, 2025);

    private final ProvincePolicyService provincePolicyService;
    private final ProvinceAlgorithmPolicyService provinceAlgorithmPolicyService;
    private final PolicyRuleService policyRuleService;
    private final PolicyRuleConfigMapper policyRuleConfigMapper;
    private final DataReadinessService dataReadinessService;
    private final JdbcTemplate jdbcTemplate;

    public ProvinceBatchSupportController(ProvincePolicyService provincePolicyService,
                                          ProvinceAlgorithmPolicyService provinceAlgorithmPolicyService,
                                          PolicyRuleService policyRuleService,
                                          PolicyRuleConfigMapper policyRuleConfigMapper,
                                          DataReadinessService dataReadinessService,
                                          JdbcTemplate jdbcTemplate) {
        this.provincePolicyService = provincePolicyService;
        this.provinceAlgorithmPolicyService = provinceAlgorithmPolicyService;
        this.policyRuleService = policyRuleService;
        this.policyRuleConfigMapper = policyRuleConfigMapper;
        this.dataReadinessService = dataReadinessService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"/{provinceCode}/batch-support", "/batch-support/{provinceCode}"})
    @Cacheable(value = "provinceBatchSupport", key = "#root.target.cacheKey(#provinceCode, #year)")
    public Result<Map<String, Object>> batchSupport(@PathVariable String provinceCode,
                                                    @RequestParam(name = "year", required = false) Integer year) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int targetYear = year == null || year <= 0 ? DEFAULT_YEAR : year;
        ProvincePolicyService.ProvincePolicy provincePolicy = provincePolicyService.getPolicy(province);
        DataReadinessService.Readiness readiness = dataReadinessService.get(province, targetYear);
        List<PolicyRuleConfig> policies = policies(province, targetYear);
        if (policies.isEmpty() && queryOnlyFallbackProvince(province)) {
            policies = List.of(queryOnlyFallbackPolicy(provincePolicy, targetYear));
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (PolicyRuleConfig config : policies) {
            items.add(toItem(provincePolicy, config, readiness));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provinceCode", province);
        response.put("provinceName", provincePolicy.getProvinceName());
        response.put("year", targetYear);
        response.put("activeAdmissionYear", targetYear);
        response.put("latestOfficialDataYear", LATEST_OFFICIAL_DATA_YEAR);
        response.put("targetYear", targetYear);
        response.put("futureImportYear", targetYear);
        response.put("historyYears", HISTORICAL_DATA_SOURCE_YEARS);
        response.put("trainingYears", HISTORICAL_DATA_SOURCE_YEARS);
        response.put("dataSourceYears", HISTORICAL_DATA_SOURCE_YEARS);
        response.put("recommendationPhase", readiness.recommendationPhase);
        response.put("phaseGate", dataReadinessService.phaseGate(readiness.recommendationPhase));
        response.put("phaseGates", dataReadinessService.phaseGates());
        response.put("estimateMode", true);
        response.put("officialDataReady", dataReadinessService.isFullRecommendReady(province, targetYear));
        response.put("dataReadiness", dataReadinessService.toMap(readiness));
        response.put("publicYearLocked", true);
        response.put("items", items);
        response.put("summary", summary(items));
        response.put("warnings", warnings(provincePolicy, readiness));
        return Result.ok(response);
    }

    public String cacheKey(String provinceCode, Integer year) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int targetYear = year == null || year <= 0 ? DEFAULT_YEAR : year;
        return province + ":" + targetYear + ":" + ProvinceAlgorithmPolicyService.POLICY_VERSION;
    }

    private List<PolicyRuleConfig> policies(String province, int year) {
        return policyRuleConfigMapper.selectList(new LambdaQueryWrapper<PolicyRuleConfig>()
                .eq(PolicyRuleConfig::getProvince, province)
                .eq(PolicyRuleConfig::getYear, year)
                .eq(PolicyRuleConfig::getEnabled, 1)
                .orderByAsc(PolicyRuleConfig::getCandidateType)
                .orderByAsc(PolicyRuleConfig::getBatchCode));
    }

    public PolicyRuleConfig queryOnlyFallbackPolicy(String province, int year) {
        return queryOnlyFallbackPolicy(provincePolicyService.getPolicy(province), year);
    }

    public boolean queryOnlyFallbackProvince(String province) {
        String normalized = provincePolicyService.normalizeProvinceCode(province);
        return Set.of(ProvincePolicyService.GX, ProvincePolicyService.HI,
                ProvincePolicyService.YN, ProvincePolicyService.HA).contains(normalized);
    }

    private PolicyRuleConfig queryOnlyFallbackPolicy(ProvincePolicyService.ProvincePolicy province, int year) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(province.getProvinceCode());
        config.setYear(year);
        config.setCandidateType("普通类");
        config.setBatchCode(province.getProvinceCode() + "_BENKE");
        config.setBatchName(province.getTargetBatch());
        config.setVolunteerMode("院校专业组（策略建议）");
        config.setMaxVolunteerCount(0);
        config.setMajorPerSchoolCount(0);
        config.setHasAdjustment(0);
        config.setFilingPrinciple("策略建议，不生成院校清单");
        config.setAdmissionOrder("官方结构化源补齐后再开放专业志愿表");
        config.setPolicyStatus("query_only_fallback");
        config.setOfficialSourceTitle(province.getOfficialSourceName());
        config.setOfficialSourceUrl("");
        config.setOfficialSourceText(province.getProvinceName() + "当前仅开放策略建议和数据缺口说明。");
        config.setEnabled(1);
        return config;
    }

    private Map<String, Object> toItem(ProvincePolicyService.ProvincePolicy province,
                                       PolicyRuleConfig config,
                                       DataReadinessService.Readiness readiness) {
        ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithm = provinceAlgorithmPolicyService.resolve(province.getProvinceCode(), config);
        String recommendMode = algorithm.getRecommendMode();
        String engineName = algorithm.getEngineName();
        boolean generatorReady = algorithm.isGeneratorReady();
        boolean fullReady = generatorReady && readiness.policyReady && readiness.scoreSegmentReady && readiness.admissionPlanReady
                && readiness.majorRequirementReady && readiness.majorMetaReady && readiness.mlTrainingReady
                && !DataReadinessService.PRE_OFFICIAL_DATA.equalsIgnoreCase(readiness.recommendationPhase);
        String supportLevel = resolveSupportLevel(generatorReady, fullReady, readiness);
        long scoreLineCount = countScoreLines(province.getProvinceCode(), config.getYear(), config.getCandidateType());
        long planCount = countPlans(province.getProvinceCode(), config.getYear(), config.getBatchName());
        long historyLineCount = countScoreLines(province.getProvinceCode(), LATEST_OFFICIAL_DATA_YEAR, config.getCandidateType());

        String category = category(config);
        List<String> missing = new ArrayList<>();
        missing.add("official_2026_admission_plan");
        missing.add("official_2026_score_or_rank");
        if (!readiness.scoreSegmentReady) missing.add("data_score_rank");
        if (!readiness.admissionPlanReady) missing.add(isGroupProvince(province) ? "data_admission_group_plan" : "data_admission_plan_gz");
        if (!readiness.majorRequirementReady) missing.add("data_major_requirement");
        if (!readiness.majorMetaReady) missing.add("data_major_meta");
        if (!readiness.mlTrainingReady) missing.add("ml_training");
        appendCategorySpecificMissingData(category, missing);
        appendProvinceSpecificMissingData(province.getProvinceCode(), missing);

        String supportReason = fullReady
                ? config.getBatchName() + "已具备完整数据生成门禁。"
                : supportReason(province, config, readiness, generatorReady, supportLevel);

        Map<String, Object> dataStatus = new LinkedHashMap<>();
        dataStatus.put("status", readiness.recommendationPhase);
        dataStatus.put("policyCount", 1);
        dataStatus.put("scoreLineCount", scoreLineCount);
        dataStatus.put("majorScoreCount", 0L);
        dataStatus.put("historyCount", historyLineCount);
        dataStatus.put("planCount", planCount);
        dataStatus.put("requirementCount", 0L);
        dataStatus.put("ready", fullReady);
        dataStatus.put("detail", supportReason);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("batchCode", config.getBatchCode());
        item.put("batchName", config.getBatchName());
        item.put("candidateType", config.getCandidateType());
        item.put("category", category);
        item.put("supportLevel", supportLevel);
        item.put("recommendMode", recommendMode);
        item.put("engine", engineName);
        item.put("engineName", engineName);
        item.put("policyVersion", algorithm.getPolicyVersion());
        item.put("algorithmFamily", algorithm.getAlgorithmFamily());
        item.put("generationEngine", algorithm.getGenerationEngine());
        item.put("modelRoute", algorithm.getModelRoute());
        item.put("modelRouteStatus", algorithm.getModelRouteStatus());
        item.put("mlEligible", algorithm.isMlEligible());
        item.put("mlModelPolicy", algorithm.getMlModelPolicy());
        item.put("activationGate", algorithm.getActivationGate());
        item.put("algorithmReason", algorithm.getReason());
        item.put("algorithmPolicy", algorithm.toMap());
        item.put("generatorReady", generatorReady);
        item.put("targetCount", config.getMaxVolunteerCount());
        item.put("maxVolunteerCount", config.getMaxVolunteerCount());
        item.put("majorPerSchoolCount", config.getMajorPerSchoolCount());
        item.put("hasAdjustment", config.getHasAdjustment() != null && config.getHasAdjustment() == 1);
        item.put("volunteerMode", config.getVolunteerMode());
        item.put("policyConfigured", true);
        item.put("policyStatus", config.getPolicyStatus());
        item.put("officialSourceTitle", config.getOfficialSourceTitle());
        item.put("officialSourceUrl", config.getOfficialSourceUrl());
        item.put("scoreLineCount", scoreLineCount);
        item.put("majorScoreCount", 0L);
        item.put("historicalScoreLineCount", historyLineCount);
        item.put("historicalMajorScoreCount", 0L);
        item.put("planCount", planCount);
        item.put("requirementCount", 0L);
        item.put("dataStatus", dataStatus);
        item.put("missingData", missing);
        item.put("supportNote", supportReason);
        item.put("supportReason", supportReason);
        item.put("dataSourceYears", HISTORICAL_DATA_SOURCE_YEARS);
        item.put("latestOfficialDataYear", LATEST_OFFICIAL_DATA_YEAR);
        item.put("warnings", List.of(
                "不得使用历史数据冒充 2026 正式招生计划或投档线。",
                "推荐前必须通过省份、身份、批次三重政策门禁。"));
        return item;
    }

    private Map<String, Object> summary(List<Map<String, Object>> items) {
        Map<String, Object> s = new LinkedHashMap<>();
        long full = items.stream().filter(i -> "FULL_RECOMMEND".equals(i.get("supportLevel"))).count();
        long estimate = items.stream().filter(i -> "ESTIMATE_RECOMMEND".equals(i.get("supportLevel"))).count();
        long query = items.size() - full - estimate;
        s.put("FULL_RECOMMEND", full);
        s.put("TRIAL_RECOMMEND", 0);
        s.put("ESTIMATE_RECOMMEND", estimate);
        s.put("QUERY_ONLY", query);
        s.put("total", items.size());
        return s;
    }

    private String resolveSupportLevel(boolean generatorReady,
                                       boolean fullReady,
                                       DataReadinessService.Readiness readiness) {
        if (fullReady) {
            return "FULL_RECOMMEND";
        }
        if (generatorReady && DataReadinessService.PRE_OFFICIAL_DATA.equalsIgnoreCase(readiness.recommendationPhase)) {
            return "ESTIMATE_RECOMMEND";
        }
        return "QUERY_ONLY";
    }

    private List<String> warnings(ProvincePolicyService.ProvincePolicy province, DataReadinessService.Readiness readiness) {
        if (!DataReadinessService.PRE_OFFICIAL_DATA.equalsIgnoreCase(readiness.recommendationPhase)) {
            return List.of("请继续以" + province.getOfficialSourceName() + "和高校官方信息为准。");
        }
        return List.of(province.getProvinceName() + " 2026 官方数据尚未完整导入并核验；当前仅展示批次策略、数据缺口和来源，不开放完整数据生成。");
    }

    private String supportReason(ProvincePolicyService.ProvincePolicy province,
                                 PolicyRuleConfig config,
                                 DataReadinessService.Readiness readiness,
                                 boolean generatorReady,
                                 String supportLevel) {
        if (!generatorReady) {
            return province.getProvinceName() + config.getBatchName()
                    + "当前仅完成政策展示；该身份/批次的生成算法尚未开放，仅展示官方批次策略和数据缺口，不开放完整数据生成。";
        }
        if ("ESTIMATE_RECOMMEND".equals(supportLevel)) {
            return province.getProvinceName() + config.getBatchName() + "处于" + readiness.recommendationPhase
                    + "；targetYear=2026 仅用于展示，可基于2024/2025历史数据窗口（以已核验数据为准）生成估算志愿草稿，但不代表2026官方招生计划、投档线或录取承诺，完整数据生成仍关闭。"
                    + provinceSpecificSupportNote(province.getProvinceCode());
        }
        return province.getProvinceName() + config.getBatchName() + "仍处于" + readiness.recommendationPhase
                + "；仅展示官方批次策略、资格门槛、综合分/分数位次缺口，不开放完整数据生成。"
                + provinceSpecificSupportNote(province.getProvinceCode());
    }

    private void appendCategorySpecificMissingData(String category, List<String> missing) {
        if ("ART".equals(category) || "SPORTS".equals(category)) {
            missing.add("official_2026_composite_score_rule");
            missing.add("official_2026_special_qualification");
            return;
        }
        if ("SKILL".equals(category)) {
            missing.add("official_2026_skill_exam_rule");
            missing.add("official_2026_skill_qualification");
            return;
        }
        if ("EARLY".equals(category)) {
            missing.add("official_2026_qualification_rule");
        }
    }

    private void appendProvinceSpecificMissingData(String provinceCode, List<String> missing) {
        if (ProvincePolicyService.HB.equals(provinceCode)) {
            missing.add("HB_A00306_manual_rank_review");
        } else if (ProvincePolicyService.SC.equals(provinceCode) || ProvincePolicyService.AH.equals(provinceCode)) {
            missing.add("formal_import_strategy_confirmation");
        } else if (ProvincePolicyService.GX.equals(provinceCode)) {
            missing.add("gx_undergraduate_group_plan_and_major_catalog");
            missing.add("gx_professional_major_code_source");
        } else if (ProvincePolicyService.HI.equals(provinceCode)) {
            missing.add("hi_score_rank_3plus3_official_source");
            missing.add("hi_required_subjects_major_catalog");
        } else if (ProvincePolicyService.YN.equals(provinceCode)) {
            missing.add("yn_2025_new_gaokao_ocr_reviewed_source");
            missing.add("yn_group_plan_major_catalog");
        } else if (ProvincePolicyService.HA.equals(provinceCode)) {
            missing.add("ha_official_core_gaokao_source");
            missing.add("ha_group_plan_major_catalog");
        }
    }

    private String provinceSpecificSupportNote(String provinceCode) {
        if (ProvincePolicyService.HB.equals(provinceCode)) {
            return " 湖北2025历史源中清华大学 A00306 历史类 674 分与一分一段最高 673 分存在官方口径冲突，需人工复核后再进入完整数据链路。";
        }
        if (ProvincePolicyService.SC.equals(provinceCode) || ProvincePolicyService.AH.equals(provinceCode)) {
            return " 该省同年生产数据已存在，后续导入需先确认跳过既有、仅新增或人工替换策略。";
        }
        if (ProvincePolicyService.GX.equals(provinceCode)) {
            return " 广西当前分数线部分可用，但本科主批专业组计划、专业代码和完整专业目录仍未形成可生成候选池。";
        }
        if (ProvincePolicyService.HI.equals(provinceCode)) {
            return " 海南保持3+3综合改革口径；一分一段/标准分位次和requiredSubjects专业目录仍需官方源补齐。";
        }
        if (ProvincePolicyService.YN.equals(provinceCode)) {
            return " 云南2025首年新高考官方图片源仍在OCR/人工复核，暂不生成院校清单。";
        }
        if (ProvincePolicyService.HA.equals(provinceCode)) {
            return " 河南普通高考核心官方源仍缺，暂不生成院校清单。";
        }
        return "";
    }

    private String category(PolicyRuleConfig config) {
        String value = ((config.getCandidateType() == null ? "" : config.getCandidateType()) + " "
                + (config.getBatchName() == null ? "" : config.getBatchName()) + " "
                + (config.getBatchCode() == null ? "" : config.getBatchCode())).toUpperCase();
        if (value.contains("ART") || value.contains("艺术")) return "ART";
        if (value.contains("SPORT") || value.contains("体育")) return "SPORTS";
        if (value.contains("技能")) return "SKILL";
        if (value.contains("提前")) return "EARLY";
        return "ORDINARY";
    }

    private boolean isGroupProvince(ProvincePolicyService.ProvincePolicy province) {
        return ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45.equals(province.getVolunteerUnitType());
    }

    private long countScoreLines(String province, int year, String candidateType) {
        String subjectLike = candidateType != null && candidateType.contains("历史") ? "%历史%" : "%";
        return queryLong("SELECT COUNT(*) FROM data_score_rank WHERE province_code=? AND year=? AND subject_type LIKE ?", province, year, subjectLike);
    }

    private long countPlans(String province, int year, String batchName) {
        if (ProvincePolicyService.GZ.equals(province)) {
            return queryLong("SELECT COUNT(*) FROM data_admission_plan_gz WHERE year=? AND batch LIKE ?", year, like(batchName));
        }
        return queryLong("SELECT COUNT(*) FROM data_admission_group_plan WHERE province_code=? AND year=? AND batch LIKE ?", province, year, like(batchName));
    }

    private long queryLong(String sql, Object... args) {
        try {
            Long v = jdbcTemplate.queryForObject(sql, Long.class, args);
            return v == null ? 0L : v;
        } catch (DataAccessException ignored) {
            return 0L;
        }
    }

    private String like(String batchName) {
        String name = batchName == null ? "" : batchName;
        String key = name.replace("普通", "").replace("类", "").replace("批", "").trim();
        return "%" + (key.isEmpty() ? name : key) + "%";
    }
}
