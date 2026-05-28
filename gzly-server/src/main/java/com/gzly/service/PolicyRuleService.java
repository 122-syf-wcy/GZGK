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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PolicyRuleService {

    public static final int DEFAULT_YEAR = 2025;
    public static final String DEFAULT_CANDIDATE_TYPE = "普通类";
    public static final String DEFAULT_BATCH_CODE = "NORMAL_UNDERGRADUATE";

    private final PolicyRuleConfigMapper mapper;
    private final ProvincePolicyService provincePolicyService;

    private static final Map<String, Map<String, String>> BATCH_ALIASES = Map.of(
            ProvincePolicyService.GZ, Map.ofEntries(
                    Map.entry("GZ_NORMAL_BENKE", "NORMAL_UNDERGRADUATE"),
                    Map.entry("GZ_ZHUANKE", "NORMAL_SPECIALTY"),
                    Map.entry("GZ_EARLY_C", "EARLY_C"),
                    Map.entry("GZ_EARLY_A", "EARLY_A_B"),
                    Map.entry("GZ_EARLY_B", "EARLY_A_B"),
                    Map.entry("GZ_ZHUANKE_EARLY", "SPECIALTY_EARLY")
            ),
            ProvincePolicyService.SC, Map.ofEntries(
                    Map.entry("SC_BENKE", "SC_BENKE_B"),
                    Map.entry("SC_ZHUANKE", "SC_ZHUANKE_B")
            ),
            ProvincePolicyService.AH, Map.ofEntries(
                    Map.entry("AH_NORMAL_BENKE", "AH_BENKE"),
                    Map.entry("AH_NORMAL_ZHUANKE", "AH_ZHUANKE")
            ),
            ProvincePolicyService.HB, Map.ofEntries(
                    Map.entry("HB_NORMAL_BENKE", "HB_BENKE"),
                    Map.entry("HB_NORMAL_ZHUANKE", "HB_ZHUANKE")
            ),
            ProvincePolicyService.GX, Map.ofEntries(
                    Map.entry("GX_NORMAL_BENKE", "GX_BENKE"),
                    Map.entry("GX_BENKE", "GX_BENKE")
            ),
            ProvincePolicyService.HI, Map.ofEntries(
                    Map.entry("HI_NORMAL_BENKE", "HI_BENKE"),
                    Map.entry("HI_BENKE", "HI_BENKE")
            ),
            ProvincePolicyService.YN, Map.ofEntries(
                    Map.entry("YN_NORMAL_BENKE", "YN_BENKE"),
                    Map.entry("YN_BENKE", "YN_BENKE")
            ),
            ProvincePolicyService.HA, Map.ofEntries(
                    Map.entry("HA_NORMAL_BENKE", "HA_BENKE"),
                    Map.entry("HA_BENKE", "HA_BENKE")
            )
    );

    public PolicyContext requirePolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int resolvedYear = year == null || year <= 0 ? DEFAULT_YEAR : year;
        String resolvedCandidateType = normalizeCandidateType(candidateType);
        String resolvedBatchCode = normalizeBatchCode(province, batchCode);

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
            config = queryOnlyFallbackPolicy(province, resolvedYear, resolvedCandidateType, resolvedBatchCode);
            if (config == null) {
                throw new BizException("当前年份政策未配置，请管理员维护政策规则");
            }
        }

        PolicyContext context = new PolicyContext();
        context.setConfig(config);
        context.setPendingConfirm("pending_confirm".equalsIgnoreCase(config.getPolicyStatus())
                || "draft".equalsIgnoreCase(config.getPolicyStatus()));
        if (context.isPendingConfirm()) {
            String sourceName = config.getOfficialSourceTitle() == null || config.getOfficialSourceTitle().isBlank()
                    ? provincePolicyService.getPolicy(province).getOfficialSourceName()
                    : config.getOfficialSourceTitle();
            context.setWarning("当前年度政策待确认，请以" + sourceName + "最新文件为准");
        }
        return context;
    }

    public boolean hasPolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
        String province = provincePolicyService.normalizeProvinceCode(provinceCode);
        int resolvedYear = year == null || year <= 0 ? DEFAULT_YEAR : year;
        String resolvedCandidateType = normalizeCandidateType(candidateType);
        String resolvedBatchCode = normalizeBatchCode(province, batchCode);
        try {
            Long count = mapper.selectCount(new LambdaQueryWrapper<PolicyRuleConfig>()
                    .eq(PolicyRuleConfig::getProvince, province)
                    .eq(PolicyRuleConfig::getYear, resolvedYear)
                    .eq(PolicyRuleConfig::getCandidateType, resolvedCandidateType)
                    .eq(PolicyRuleConfig::getBatchCode, resolvedBatchCode)
                    .eq(PolicyRuleConfig::getEnabled, 1));
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
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

    public String normalizeCandidateType(String candidateType) {
        String value = blankToDefault(candidateType, DEFAULT_CANDIDATE_TYPE).trim();
        String compact = value.replace(" ", "");
        return switch (compact) {
            case "普通", "普通类", "ORDINARY" -> "普通类";
            case "艺术", "艺术类", "ART" -> "艺术类";
            case "体育", "体育类", "SPORT", "SPORTS" -> "体育类";
            case "技能", "技能高考", "SKILL" -> "技能高考";
            default -> value;
        };
    }

    public String normalizeBatchCode(String batchCode) {
        return normalizeBatchCode(ProvincePolicyService.GZ, batchCode);
    }

    public String normalizeBatchCode(String provinceCode, String batchCode) {
        String province = provinceCode == null ? ProvincePolicyService.GZ : provinceCode.trim().toUpperCase();
        String value = blankToDefault(batchCode, DEFAULT_BATCH_CODE).trim();
        String lower = value.toLowerCase();
        String normalized = switch (lower) {
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
        if (!ProvincePolicyService.GZ.equals(province) && Set.of("NORMAL_UNDERGRADUATE", "NORMAL_SPECIALTY", "EARLY_C", "EARLY_A_B", "SPECIALTY_EARLY").contains(normalized)) {
            normalized = switch (province) {
                case ProvincePolicyService.SC -> "NORMAL_UNDERGRADUATE".equals(normalized) ? "SC_BENKE_B"
                        : "NORMAL_SPECIALTY".equals(normalized) ? "SC_ZHUANKE_B" : normalized;
                case ProvincePolicyService.AH -> "NORMAL_UNDERGRADUATE".equals(normalized) ? "AH_BENKE"
                        : "NORMAL_SPECIALTY".equals(normalized) ? "AH_ZHUANKE" : normalized;
                case ProvincePolicyService.HB -> "NORMAL_UNDERGRADUATE".equals(normalized) ? "HB_BENKE"
                        : "NORMAL_SPECIALTY".equals(normalized) ? "HB_ZHUANKE" : normalized;
                case ProvincePolicyService.GX -> "NORMAL_UNDERGRADUATE".equals(normalized) ? "GX_BENKE" : normalized;
                case ProvincePolicyService.HI -> "NORMAL_UNDERGRADUATE".equals(normalized) ? "HI_BENKE" : normalized;
                case ProvincePolicyService.YN -> "NORMAL_UNDERGRADUATE".equals(normalized) ? "YN_BENKE" : normalized;
                case ProvincePolicyService.HA -> "NORMAL_UNDERGRADUATE".equals(normalized) ? "HA_BENKE" : normalized;
                default -> normalized;
            };
        }
        String upper = normalized.toUpperCase();
        return BATCH_ALIASES.getOrDefault(province, Map.of()).getOrDefault(upper, upper);
    }

    private PolicyRuleConfig queryOnlyFallbackPolicy(String province, int year, String candidateType, String batchCode) {
        if (!Set.of(ProvincePolicyService.GX, ProvincePolicyService.HI,
                ProvincePolicyService.YN, ProvincePolicyService.HA).contains(province)) {
            return null;
        }
        if (!"普通类".equals(candidateType)) {
            return null;
        }
        String expectedBatch = province + "_BENKE";
        if (!expectedBatch.equals(batchCode)) {
            return null;
        }
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(province);
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(province);
        config.setYear(year);
        config.setCandidateType(candidateType);
        config.setBatchCode(expectedBatch);
        config.setBatchName(policy.getTargetBatch());
        config.setVolunteerMode("院校专业组（策略建议）");
        config.setMaxVolunteerCount(0);
        config.setMajorPerSchoolCount(0);
        config.setHasAdjustment(0);
        config.setFilingPrinciple("策略建议，不生成院校清单");
        config.setAdmissionOrder("官方结构化源补齐后再开放专业志愿表");
        config.setPolicyStatus("query_only_fallback");
        config.setOfficialSourceTitle(policy.getOfficialSourceName());
        config.setOfficialSourceUrl("");
        config.setOfficialSourceText(policy.getProvinceName() + "当前仅开放策略建议和数据缺口说明。");
        config.setEnabled(1);
        return config;
    }

    @Data
    public static class PolicyContext {
        private PolicyRuleConfig config;
        private boolean pendingConfirm;
        private String warning;
    }
}
