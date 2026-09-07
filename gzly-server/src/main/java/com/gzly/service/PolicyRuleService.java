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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PolicyRuleService {

    /**
     * 兼容保留：历史常量口径。实际默认年由 {@link #currentAdmissionYear()} 动态解析，
     * 避免硬编码年份随时间失效（此前写死 2025，跨年后所有未显式传 year 的请求都会解析到过期政策）。
     */
    public static final int DEFAULT_YEAR = 2025;
    public static final String DEFAULT_CANDIDATE_TYPE = "普通类";
    public static final String DEFAULT_BATCH_CODE = "NORMAL_UNDERGRADUATE";

    private final PolicyRuleConfigMapper mapper;
    private final ProvincePolicyService provincePolicyService;

    /** 当前招生年 = 当前自然年（填报季 6-8 月；9-12 月按下一届备考期仍以当年政策为最近口径）。 */
    public int currentAdmissionYear() {
        return java.time.LocalDate.now().getYear();
    }

    public PolicyContext requirePolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int resolvedYear = year == null || year <= 0 ? currentAdmissionYear() : year;
        String resolvedCandidateType = blankToDefault(candidateType, DEFAULT_CANDIDATE_TYPE);
        String resolvedBatchCode = normalizeBatchCode(batchCode);

        PolicyRuleConfig config;
        boolean fallbackYearUsed = false;
        try {
            config = selectExact(province, resolvedYear, resolvedCandidateType, resolvedBatchCode);
            if (config == null && year == null) {
                // 未显式指定年份时回退该省该批次最近可用年份的政策（如只种了 2026 而当前默认解析到别的年份），
                // 避免"政策未配置"把入口打挂；显式传年查不到仍视为配置缺失。
                config = selectLatest(province, resolvedCandidateType, resolvedBatchCode);
                fallbackYearUsed = config != null && !Objects.equals(config.getYear(), resolvedYear);
            }
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
        if (fallbackYearUsed) {
            context.setWarning(String.format("当前按 %d 年政策口径生成（%d 年政策待配置），请以考试院最新文件为准",
                    config.getYear(), resolvedYear));
        } else if (context.isPendingConfirm()) {
            context.setWarning("当前年度政策待确认，请以考试院最新文件为准");
        }
        return context;
    }

    private PolicyRuleConfig selectExact(String province, int year, String candidateType, String batchCode) {
        return mapper.selectOne(new LambdaQueryWrapper<PolicyRuleConfig>()
                .eq(PolicyRuleConfig::getProvince, province)
                .eq(PolicyRuleConfig::getYear, year)
                .eq(PolicyRuleConfig::getCandidateType, candidateType)
                .eq(PolicyRuleConfig::getBatchCode, batchCode)
                .eq(PolicyRuleConfig::getEnabled, 1)
                .last("LIMIT 1"));
    }

    private PolicyRuleConfig selectLatest(String province, String candidateType, String batchCode) {
        return mapper.selectOne(new LambdaQueryWrapper<PolicyRuleConfig>()
                .eq(PolicyRuleConfig::getProvince, province)
                .eq(PolicyRuleConfig::getCandidateType, candidateType)
                .eq(PolicyRuleConfig::getBatchCode, batchCode)
                .eq(PolicyRuleConfig::getEnabled, 1)
                .orderByDesc(PolicyRuleConfig::getYear)
                .last("LIMIT 1"));
    }

    /**
     * 与 {@link #requirePolicy} 同条件的宽松查询：查不到返回 null 不抛异常。
     * 供就绪度判定 / batch-support 展示使用，避免"政策未配置"直接把展示接口打挂。
     */
    public PolicyRuleConfig findPolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int resolvedYear = year == null || year <= 0 ? currentAdmissionYear() : year;
        String resolvedCandidateType = blankToDefault(candidateType, DEFAULT_CANDIDATE_TYPE);
        String resolvedBatchCode = normalizeBatchCode(batchCode);
        try {
            return selectExact(province, resolvedYear, resolvedCandidateType, resolvedBatchCode);
        } catch (Exception e) {
            return null;
        }
    }

    /** 某省某年启用中的全部批次政策；查询失败返回空列表。 */
    public java.util.List<PolicyRuleConfig> listPolicies(String provinceCode, Integer year) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int resolvedYear = year == null || year <= 0 ? currentAdmissionYear() : year;
        try {
            return mapper.selectList(new LambdaQueryWrapper<PolicyRuleConfig>()
                    .eq(PolicyRuleConfig::getProvince, province)
                    .eq(PolicyRuleConfig::getYear, resolvedYear)
                    .eq(PolicyRuleConfig::getEnabled, 1)
                    .orderByAsc(PolicyRuleConfig::getBatchCode));
        } catch (Exception e) {
            return java.util.List.of();
        }
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
