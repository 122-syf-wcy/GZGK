package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.exception.BizException;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.mapper.PolicyRuleConfigMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PolicyRuleService {

    public static final int DEFAULT_YEAR = 2025;
    public static final String DEFAULT_CANDIDATE_TYPE = "普通类";
    public static final String DEFAULT_BATCH_CODE = "NORMAL_UNDERGRADUATE";

    private final PolicyRuleConfigMapper mapper;
    private final ProvincePolicyService provincePolicyService;

    public PolicyContext requirePolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int resolvedYear = year == null || year <= 0 ? DEFAULT_YEAR : year;
        String resolvedCandidateType = blankToDefault(candidateType, DEFAULT_CANDIDATE_TYPE);
        String resolvedBatchCode = normalizeBatchCode(batchCode);

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
        String value = blankToDefault(batchCode, DEFAULT_BATCH_CODE).trim();
        String lower = value.toLowerCase();
        return switch (lower) {
            case "ordinary_undergraduate", "normal_undergraduate", "本科批", "普通本科批", "普通类本科批" ->
                    "NORMAL_UNDERGRADUATE";
            case "ordinary_specialty", "normal_specialty", "高职专科批", "普通类高职专科批", "专科批" ->
                    "NORMAL_SPECIALTY";
            case "early_c", "提前批c段", "普通类本科提前批c段" -> "EARLY_C";
            case "early_a_b", "early_ab", "提前批a/b段", "提前批ab段", "普通类本科提前批a/b段" -> "EARLY_A_B";
            default -> value.toUpperCase().startsWith("NORMAL_") || value.toUpperCase().startsWith("EARLY_")
                    ? value.toUpperCase()
                    : value;
        };
    }

    @Data
    public static class PolicyContext {
        private PolicyRuleConfig config;
        private boolean pendingConfirm;
        private String warning;
    }
}
