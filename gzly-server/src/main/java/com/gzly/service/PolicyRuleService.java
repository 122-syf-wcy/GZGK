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
        String resolvedCandidateType = normalizeCandidateTypeFor(province,
                blankToDefault(candidateType, DEFAULT_CANDIDATE_TYPE));
        String resolvedBatchCode = normalizeBatchCodeFor(province, batchCode);
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
        if (config == null) {
            config = syntheticConfigFor(province, resolvedYear, resolvedCandidateType, resolvedBatchCode);
        }
        if (config == null) {
            throw new BizException("当前年份政策未配置，请管理员维护政策规则");
        }

        PolicyContext context = new PolicyContext();
        context.setConfig(config);
        context.setPendingConfirm("pending_confirm".equalsIgnoreCase(config.getPolicyStatus())
                || "draft".equalsIgnoreCase(config.getPolicyStatus()));
        if (context.isPendingConfirm()) {
            context.setWarning(pendingConfirmWarningFor(province));
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
        return normalizeBatchCodeFor(ProvincePolicyService.GZ, batchCode);
    }

    /**
     * 多省版批次代码归一化：贵州走 BatchRuleRegistry，安徽走 AnhuiBatchRuleRegistry，
     * 四川 / 湖北（院校专业组 45）走 SichuanBatchRuleRegistry。
     */
    public String normalizeBatchCode(String provinceCode, String batchCode) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        return normalizeBatchCodeFor(province, batchCode);
    }

    private String normalizeBatchCodeFor(String province, String batchCode) {
        if (ProvincePolicyService.GZ.equals(province)) {
            return BatchRuleRegistry.normalizeBatchCode(blankToDefault(batchCode, BatchRuleRegistry.DEFAULT_BATCH_CODE));
        }
        if (ProvincePolicyService.AH.equals(province)) {
            return AnhuiBatchRuleRegistry.normalizeBatchCode(
                    blankToDefault(batchCode, AnhuiBatchRuleRegistry.DEFAULT_BATCH_CODE));
        }
        return SichuanBatchRuleRegistry.normalizeBatchCode(
                blankToDefault(batchCode, SichuanBatchRuleRegistry.DEFAULT_BATCH_CODE));
    }

    private String normalizeCandidateTypeFor(String province, String candidateType) {
        if (ProvincePolicyService.GZ.equals(province)) {
            return BatchRuleRegistry.normalizeCandidateType(candidateType);
        }
        if (ProvincePolicyService.AH.equals(province)) {
            return AnhuiBatchRuleRegistry.normalizeCandidateType(candidateType);
        }
        return SichuanBatchRuleRegistry.normalizeCandidateType(candidateType);
    }

    private void validateProvinceBatch(String province, String candidateType, String batchCode) {
        if (ProvincePolicyService.GZ.equals(province)) {
            BatchRuleRegistry.BatchRule rule = BatchRuleRegistry.find(batchCode).orElse(null);
            if (rule == null) {
                throw new BizException(400, "当前省份不支持该批次，请重新选择目标批次");
            }
            if (!BatchRuleRegistry.candidateTypeMatches(rule.candidateType(), candidateType)) {
                throw new BizException(400, "考生类别与目标批次不匹配，请重新选择");
            }
            return;
        }
        if (ProvincePolicyService.AH.equals(province)) {
            AnhuiBatchRuleRegistry.BatchRule rule = AnhuiBatchRuleRegistry.find(batchCode).orElse(null);
            if (rule == null) {
                throw new BizException(400, "当前省份不支持该批次，请重新选择目标批次");
            }
            if (!AnhuiBatchRuleRegistry.candidateTypeMatches(rule.candidateType(), candidateType)) {
                throw new BizException(400, "考生类别与目标批次不匹配，请重新选择");
            }
            return;
        }
        // 四川 / 湖北 → SichuanBatchRuleRegistry
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.find(batchCode).orElse(null);
        if (rule == null) {
            throw new BizException(400, "当前省份不支持该批次，请重新选择目标批次");
        }
        if (!SichuanBatchRuleRegistry.candidateTypeMatches(rule.candidateType(), candidateType)) {
            throw new BizException(400, "考生类别与目标批次不匹配，请重新选择");
        }
    }

    private PolicyRuleConfig syntheticConfigFor(String province, int year, String candidateType, String batchCode) {
        if (ProvincePolicyService.GZ.equals(province)) {
            return BatchRuleRegistry.find(batchCode)
                    .map(rule -> syntheticConfig(province, year, candidateType, rule))
                    .orElse(null);
        }
        if (ProvincePolicyService.AH.equals(province)) {
            return AnhuiBatchRuleRegistry.find(batchCode)
                    .map(rule -> syntheticAnhuiConfig(province, year, candidateType, rule))
                    .orElse(null);
        }
        return SichuanBatchRuleRegistry.find(batchCode)
                .map(rule -> syntheticSichuanConfig(province, year, candidateType, rule))
                .orElse(null);
    }

    private String pendingConfirmWarningFor(String province) {
        if (ProvincePolicyService.GZ.equals(province)) {
            return "当前年度政策待确认，请以贵州省招生考试院最新文件为准";
        }
        if (ProvincePolicyService.SC.equals(province)) {
            return "当前年度政策待确认，请以四川省教育考试院最新实施规定为准";
        }
        if (ProvincePolicyService.HB.equals(province)) {
            return "当前年度政策待确认，请以湖北省教育考试院最新文件为准";
        }
        if (ProvincePolicyService.AH.equals(province)) {
            return "当前年度政策待确认，请以安徽省教育招生考试院最新文件为准";
        }
        return "当前年度政策待确认，请以省级招生考试院最新文件为准";
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

    private PolicyRuleConfig syntheticSichuanConfig(String province, int year, String candidateType,
                                                    SichuanBatchRuleRegistry.BatchRule rule) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(province);
        config.setYear(year);
        config.setCandidateType(candidateType);
        config.setBatchCode(rule.batchCode());
        config.setBatchName(rule.batchName());
        config.setVolunteerMode(rule.volunteerMode());
        config.setMaxVolunteerCount(rule.targetCount());
        config.setMajorPerSchoolCount(rule.majorsPerGroup());
        config.setHasAdjustment(rule.hasAdjustment() ? 1 : 0);
        config.setFilingPrinciple(rule.recommendMode().name());
        config.setAdmissionOrder(rule.category().name());
        config.setPolicyStatus("registry_only");
        config.setOfficialSourceTitle(SichuanBatchRuleRegistry.OFFICIAL_SOURCE_TITLE);
        config.setOfficialSourceUrl(SichuanBatchRuleRegistry.OFFICIAL_SOURCE_URL);
        config.setOfficialSourceText(SichuanBatchRuleRegistry.OFFICIAL_SOURCE_TEXT);
        config.setEnabled(1);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }

    private PolicyRuleConfig syntheticAnhuiConfig(String province, int year, String candidateType,
                                                  AnhuiBatchRuleRegistry.BatchRule rule) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(province);
        config.setYear(year);
        config.setCandidateType(candidateType);
        config.setBatchCode(rule.batchCode());
        config.setBatchName(rule.batchName());
        config.setVolunteerMode(rule.volunteerMode());
        config.setMaxVolunteerCount(rule.targetCount());
        config.setMajorPerSchoolCount(rule.majorsPerGroup());
        config.setHasAdjustment(rule.hasAdjustment() ? 1 : 0);
        config.setFilingPrinciple(rule.recommendMode().name());
        config.setAdmissionOrder(rule.category().name());
        config.setPolicyStatus("registry_only");
        config.setOfficialSourceTitle(AnhuiBatchRuleRegistry.OFFICIAL_SOURCE_TITLE);
        config.setOfficialSourceUrl(AnhuiBatchRuleRegistry.OFFICIAL_SOURCE_URL);
        config.setOfficialSourceText(AnhuiBatchRuleRegistry.OFFICIAL_SOURCE_TEXT);
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
