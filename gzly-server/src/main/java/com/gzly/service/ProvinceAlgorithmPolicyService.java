package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProvinceAlgorithmPolicyService {

    public static final String POLICY_VERSION = "province-alg-policy-20260523-v1";

    private static final Set<String> GZ_GENERATION_BATCHES = Set.of("NORMAL_UNDERGRADUATE", "NORMAL_SPECIALTY");
    private static final Set<String> SC_GENERATION_BATCHES = Set.of("SC_BENKE_B");
    private static final Set<String> AH_GENERATION_BATCHES = Set.of("AH_BENKE");
    private static final Set<String> HB_GENERATION_BATCHES = Set.of("HB_BENKE");

    public AlgorithmPolicy resolve(String provinceCode, PolicyRuleConfig config) {
        String province = normalizeProvince(provinceCode, config);
        String candidateType = trim(config == null ? null : config.getCandidateType());
        if (candidateType.isBlank()) {
            candidateType = "普通类";
        }
        String batchCode = trim(config == null ? null : config.getBatchCode()).toUpperCase(Locale.ROOT);
        String batchName = trim(config == null ? null : config.getBatchName());
        String recommendMode = recommendMode(config);
        String category = category(config);
        String generationEngine = generationEngine(province, category, recommendMode);
        boolean ordinary = "普通类".equals(candidateType);
        boolean sequential = "SEQUENTIAL_QUERY".equals(recommendMode);
        boolean batchGenerationReady = ordinary && !sequential && generationBatchAllowed(province, batchCode);
        boolean mlEligible = ProvincePolicyService.GZ.equals(province) && batchGenerationReady;

        AlgorithmPolicy policy = new AlgorithmPolicy();
        policy.setPolicyVersion(POLICY_VERSION);
        policy.setProvinceCode(province);
        policy.setCandidateType(candidateType);
        policy.setBatchCode(batchCode);
        policy.setBatchName(batchName);
        policy.setCategory(category);
        policy.setRecommendMode(recommendMode);
        policy.setAlgorithmFamily(algorithmFamily(province, category, recommendMode));
        policy.setGenerationEngine(generationEngine);
        policy.setEngineName(generationEngine);
        policy.setModelRoute(modelRoute(province, category));
        policy.setModelRouteStatus(modelRouteStatus(province, category, mlEligible));
        policy.setMlEligible(mlEligible);
        policy.setMlModelPolicy(mlModelPolicy(province, mlEligible));
        policy.setActivationGate(activationGate(province, category, batchGenerationReady, mlEligible));
        policy.setGeneratorReady(batchGenerationReady);
        policy.setReason(reason(province, category, recommendMode, batchGenerationReady, mlEligible));
        return policy;
    }

    public boolean generatorReady(String provinceCode, PolicyRuleConfig config) {
        return resolve(provinceCode, config).isGeneratorReady();
    }

    public boolean mlEligible(String provinceCode, PolicyRuleConfig config) {
        return resolve(provinceCode, config).isMlEligible();
    }

    private String normalizeProvince(String provinceCode, PolicyRuleConfig config) {
        String value = trim(provinceCode);
        if (value.isBlank() && config != null) {
            value = trim(config.getProvince());
        }
        if (value.isBlank()) {
            return ProvincePolicyService.GZ;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private boolean generationBatchAllowed(String province, String batchCode) {
        return switch (province) {
            case ProvincePolicyService.GZ -> GZ_GENERATION_BATCHES.contains(batchCode);
            case ProvincePolicyService.SC -> SC_GENERATION_BATCHES.contains(batchCode);
            case ProvincePolicyService.AH -> AH_GENERATION_BATCHES.contains(batchCode);
            case ProvincePolicyService.HB -> HB_GENERATION_BATCHES.contains(batchCode);
            default -> false;
        };
    }

    private String generationEngine(String province, String category, String recommendMode) {
        if ("SEQUENTIAL_QUERY".equals(recommendMode)) return "sequential_query_v1";
        if ("ART".equals(category)) return province.toLowerCase(Locale.ROOT) + "_art_query_gate_v1";
        if ("SPORTS".equals(category)) return province.toLowerCase(Locale.ROOT) + "_sports_query_gate_v1";
        if ("SKILL".equals(category)) return province.toLowerCase(Locale.ROOT) + "_skill_query_gate_v1";
        if (ProvincePolicyService.GZ.equals(province)) return "gz_major_parallel_rank_v1";
        return province.toLowerCase(Locale.ROOT) + "_group_parallel_rank_v1";
    }

    private String algorithmFamily(String province, String category, String recommendMode) {
        if ("SEQUENTIAL_QUERY".equals(recommendMode)) return "sequential_policy_query";
        if ("ART".equals(category)) return "art_composite_query_gate";
        if ("SPORTS".equals(category)) return "sports_composite_query_gate";
        if ("SKILL".equals(category)) return "skill_exam_query_gate";
        if (ProvincePolicyService.GZ.equals(province)) return "major_parallel_rank_gradient";
        return "professional_group_parallel_rank_gradient";
    }

    private String modelRoute(String province, String category) {
        if (!"ORDINARY".equals(category)) {
            return province.toLowerCase(Locale.ROOT) + "_policy_query_only";
        }
        if (ProvincePolicyService.GZ.equals(province)) {
            return "gz_historical_rank_chance_v2";
        }
        return province.toLowerCase(Locale.ROOT) + "_baseline_rank_chance_candidate";
    }

    private String modelRouteStatus(String province, String category, boolean mlEligible) {
        if (!"ORDINARY".equals(category)) return "QUERY_GATE_ONLY";
        if (mlEligible) return "GZ_ACTIVE_ONLY_AFTER_READINESS";
        if (Set.of(ProvincePolicyService.SC, ProvincePolicyService.AH, ProvincePolicyService.HB).contains(province)) {
            return "BASELINE_ONLY_NOT_ACTIVATED";
        }
        return "RULE_FALLBACK_ONLY";
    }

    private String mlModelPolicy(String province, boolean mlEligible) {
        if (mlEligible) {
            return "允许在年度 readiness 全绿且生成门禁通过后调用已激活 GZ 历史 rank/chance 模型。";
        }
        if (Set.of(ProvincePolicyService.SC, ProvincePolicyService.AH, ProvincePolicyService.HB).contains(province)) {
            return "仅保留候选/离线基线，不调用线上 GZ 全局模型；待本省多年份数据和灰度确认后再激活。";
        }
        return "当前仅输出策略建议和数据缺口，不调用线上 ML，不生成院校清单。";
    }

    private String activationGate(String province, String category, boolean batchGenerationReady, boolean mlEligible) {
        if (!batchGenerationReady) {
            return "身份/批次仅展示策略，generatorReady=false，不消耗完整推荐额度。";
        }
        if (mlEligible) {
            return "必须同时满足本省官方招生数据、专业要求、专业元数据和模型训练门禁后，才可进入完整数据生成。";
        }
        return province + " 本省 ML 未激活；即便批次规则可生成，也只能在 readiness 全绿后走规则/基线兜底。";
    }

    private String reason(String province, String category, String recommendMode, boolean batchGenerationReady, boolean mlEligible) {
        if ("SEQUENTIAL_QUERY".equals(recommendMode)) {
            return "顺序志愿/单一志愿批次只做政策查询和人工复核，不进入平行志愿自动生成模型。";
        }
        if (!"ORDINARY".equals(category)) {
            return "艺术/体育/技能类按综合成绩或专项规则隔离，只开放查询门禁，不套用普通类位次模型。";
        }
        if (!batchGenerationReady) {
            return "该普通类批次不是当前自动生成主批次，仅展示官方策略和数据缺口。";
        }
        if (mlEligible) {
            return "贵州普通类主批次可在 readiness 全绿后使用本省历史位次与机会指数模型。";
        }
        return "该省普通类主批次策略已隔离；线上 ML 暂未激活，避免跨省错用模型。";
    }

    private String recommendMode(PolicyRuleConfig config) {
        String mode = trim(config == null ? null : config.getVolunteerMode());
        if (mode.contains("顺序") || mode.contains("单一")) return "SEQUENTIAL_QUERY";
        if (mode.contains("专业（类）") || mode.contains("专业类")) return "PARALLEL_MAJOR";
        return "PARALLEL_GROUP";
    }

    private String category(PolicyRuleConfig config) {
        String value = (trim(config == null ? null : config.getCandidateType()) + " "
                + trim(config == null ? null : config.getBatchName()) + " "
                + trim(config == null ? null : config.getBatchCode())).toUpperCase(Locale.ROOT);
        if (value.contains("ART") || value.contains("艺术")) return "ART";
        if (value.contains("SPORT") || value.contains("体育")) return "SPORTS";
        if (value.contains("技能")) return "SKILL";
        if (value.contains("提前")) return "EARLY";
        return "ORDINARY";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    public static class AlgorithmPolicy {
        private String policyVersion;
        private String provinceCode;
        private String candidateType;
        private String batchCode;
        private String batchName;
        private String category;
        private String recommendMode;
        private String algorithmFamily;
        private String generationEngine;
        private String engineName;
        private String modelRoute;
        private String modelRouteStatus;
        private boolean mlEligible;
        private String mlModelPolicy;
        private String activationGate;
        private boolean generatorReady;
        private String reason;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("policyVersion", policyVersion);
            map.put("provinceCode", provinceCode);
            map.put("candidateType", candidateType);
            map.put("batchCode", batchCode);
            map.put("batchName", batchName);
            map.put("category", category);
            map.put("recommendMode", recommendMode);
            map.put("algorithmFamily", algorithmFamily);
            map.put("generationEngine", generationEngine);
            map.put("engineName", engineName);
            map.put("modelRoute", modelRoute);
            map.put("modelRouteStatus", modelRouteStatus);
            map.put("mlEligible", mlEligible);
            map.put("mlModelPolicy", mlModelPolicy);
            map.put("activationGate", activationGate);
            map.put("generatorReady", generatorReady);
            map.put("reason", reason);
            return map;
        }
    }
}
