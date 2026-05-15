package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.exception.BizException;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.mapper.PolicyRuleConfigMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PolicyRuleService {

    public static final int ACTIVE_ADMISSION_YEAR = AdmissionYearService.FALLBACK_ACTIVE_ADMISSION_YEAR;
    public static final int DEFAULT_YEAR = ACTIVE_ADMISSION_YEAR;
    public static final int HISTORY_YEAR_START = 2021;
    public static final String DEFAULT_CANDIDATE_TYPE = "普通类";
    public static final String DEFAULT_BATCH_CODE = BatchRuleRegistry.DEFAULT_BATCH_CODE;

    private final PolicyRuleConfigMapper mapper;
    private final ProvincePolicyService provincePolicyService;
    private final AdmissionYearService admissionYearService;

    public PolicyContext requirePolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int resolvedYear = year == null || year <= 0 ? admissionYearService.getActiveAdmissionYear() : year;
        String resolvedCandidateType = BatchRuleRegistry.normalizeCandidateType(blankToDefault(candidateType, DEFAULT_CANDIDATE_TYPE));
        String resolvedBatchCode = normalizeBatchCode(batchCode);
        validateProvinceBatch(province, resolvedCandidateType, resolvedBatchCode);

        PolicyRuleConfig config;
        try {
            config = mapper.selectOne(new LambdaQueryWrapper<PolicyRuleConfig>()
                    .eq(PolicyRuleConfig::getProvince, province)
                    .eq(PolicyRuleConfig::getYear, resolvedYear)
                    .eq(PolicyRuleConfig::getCandidateType, resolvedCandidateType)
                    .eq(PolicyRuleConfig::getBatchCode, resolvedBatchCode)
                    .eq(PolicyRuleConfig::getEnabled, 1)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            throw new BizException("当前年份政策未配置，请管理员维护政策规则");
        }
        if (config == null && BatchRuleRegistry.find(resolvedBatchCode).isPresent()) {
            config = syntheticConfig(province, resolvedYear, resolvedCandidateType, BatchRuleRegistry.require(resolvedBatchCode));
        }
        if (config == null) {
            throw new BizException("当前年份政策未配置，请管理员维护政策规则");
        }

        PolicyContext context = new PolicyContext();
        context.setConfig(config);
        context.setPendingConfirm("pending_confirm".equalsIgnoreCase(config.getPolicyStatus())
                || "draft".equalsIgnoreCase(config.getPolicyStatus()));
        if (context.isPendingConfirm()) {
            context.setWarning("当前年度政策待确认，请以贵州省招生考试院最新文件为准");
        }
        return context;
    }

    public Map<String, Object> toPublicPolicy(PolicyRuleConfig config) {
        Map<String, Object> policy = new LinkedHashMap<>();
        if (config == null) {
            return policy;
        }
        policy.put("id", config.getId());
        policy.put("province", config.getProvince());
        policy.put("year", config.getYear());
        policy.put("candidateType", config.getCandidateType());
        policy.put("batchCode", config.getBatchCode());
        policy.put("batchName", config.getBatchName());
        policy.put("volunteerMode", config.getVolunteerMode());
        BatchRuleRegistry.find(config.getBatchCode()).ifPresent(rule -> {
            policy.put("supportLevel", rule.baseSupportLevel().name());
            policy.put("recommendMode", rule.recommendMode().name());
            policy.put("engine", rule.engine());
            policy.put("engineName", rule.engine());
            policy.put("supportNote", rule.supportNote());
        });
        policy.put("maxVolunteerCount", config.getMaxVolunteerCount());
        policy.put("majorPerSchoolCount", config.getMajorPerSchoolCount());
        policy.put("hasAdjustment", config.getHasAdjustment() != null && config.getHasAdjustment() == 1);
        policy.put("filingPrinciple", config.getFilingPrinciple());
        policy.put("admissionOrder", config.getAdmissionOrder());
        policy.put("policyStatus", config.getPolicyStatus());
        policy.put("officialSourceTitle", config.getOfficialSourceTitle());
        policy.put("officialSourceUrl", config.getOfficialSourceUrl());
        return policy;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public String normalizeBatchCode(String batchCode) {
        return BatchRuleRegistry.normalizeBatchCode(blankToDefault(batchCode, DEFAULT_BATCH_CODE));
    }

    private void validateProvinceBatch(String province, String candidateType, String batchCode) {
        BatchRuleRegistry.BatchRule rule = BatchRuleRegistry.find(batchCode).orElse(null);
        if (rule == null) {
            throw new BizException(400, "当前省份不支持该批次，请重新选择目标批次");
        }
        if (!ProvincePolicyService.GZ.equals(province)) {
            throw new BizException(400, "当前省份不支持该批次，请重新选择目标批次");
        }
        if (!BatchRuleRegistry.candidateTypeMatches(rule.candidateType(), candidateType)) {
            throw new BizException(400, "考生类别与目标批次不匹配，请重新选择");
        }
    }

    private PolicyRuleConfig syntheticConfig(String province, int year, String candidateType,
                                             BatchRuleRegistry.BatchRule rule) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(province);
        config.setYear(year);
        config.setCandidateType(candidateType);
        config.setBatchCode(rule.batchCode());
        config.setBatchName(rule.batchName());
        config.setVolunteerMode(rule.volunteerMode());
        config.setMaxVolunteerCount(rule.targetCount());
        config.setMajorPerSchoolCount(0);
        config.setHasAdjustment(0);
        config.setFilingPrinciple(rule.recommendMode().name());
        config.setAdmissionOrder(rule.category().name());
        config.setPolicyStatus("registry_only");
        config.setOfficialSourceTitle(BatchRuleRegistry.officialSourceTitle());
        config.setOfficialSourceUrl(BatchRuleRegistry.officialSourceUrl());
        config.setOfficialSourceText(BatchRuleRegistry.officialSourceText());
        config.setEnabled(1);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }

    @Data
    public static class PolicyContext {
        private PolicyRuleConfig config;
        private boolean pendingConfirm;
        private String warning;
    }
}
