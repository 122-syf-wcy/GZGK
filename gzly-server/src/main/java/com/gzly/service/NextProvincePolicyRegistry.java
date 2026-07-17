package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.recommend.QueryOnlyRecommendEngine;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NextProvincePolicyRegistry {

    public static final String GX = ProvincePolicyService.GX;
    public static final String HI = ProvincePolicyService.HI;
    public static final String YN = ProvincePolicyService.YN;
    public static final String HA = ProvincePolicyService.HA;
    public static final String CQ = ProvincePolicyService.CQ;
    public static final String GS = ProvincePolicyService.GS;
    public static final String XJ = ProvincePolicyService.XJ;
    public static final String DEFAULT_CANDIDATE_TYPE = "普通类";

    private static final Map<String, Profile> PROFILES = buildProfiles();

    public static Optional<Profile> find(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PROFILES.get(provinceCode.trim().toUpperCase(Locale.ROOT)));
    }

    public static Profile require(String provinceCode) {
        return find(provinceCode).orElseThrow(() -> new IllegalArgumentException("Unsupported next province: " + provinceCode));
    }

    public static List<Profile> allProfiles() {
        return List.copyOf(PROFILES.values());
    }

    public static boolean isLevelOneAiQaOnly(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return false;
        }
        String normalized = provinceCode.trim().toUpperCase(Locale.ROOT);
        return CQ.equals(normalized) || GS.equals(normalized) || XJ.equals(normalized);
    }

    public static Map<String, ProvincePolicyService.ProvincePolicy> provincePolicies() {
        Map<String, ProvincePolicyService.ProvincePolicy> policies = new LinkedHashMap<>();
        for (Profile profile : PROFILES.values()) {
            policies.put(profile.provinceCode(), profile.toProvincePolicy());
        }
        return Collections.unmodifiableMap(policies);
    }

    public static String normalizeProvinceCode(String provinceCode) {
        return require(provinceCode).provinceCode();
    }

    public static String normalizeBatchCode(String provinceCode, String batchCode) {
        Profile profile = require(provinceCode);
        if (batchCode == null || batchCode.isBlank()) {
            return profile.defaultBatchCode();
        }
        String raw = batchCode.trim();
        for (BatchProfile batch : profile.batches()) {
            if (batch.batchCode().equalsIgnoreCase(raw) || batch.batchName().equals(raw)) {
                return batch.batchCode();
            }
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    public static String normalizeCandidateType(String provinceCode, String candidateType) {
        return BatchRuleRegistry.normalizeCandidateType(candidateType);
    }

    private static Map<String, Profile> buildProfiles() {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        register(profiles, profile(
                GX,
                "广西",
                List.of(
                        ordinaryBatch(GX, "BENKE", "普通本科批", 45,
                                "广西普通本科批当前按 3+1+2 院校专业组口径展示历史估算能力；2026 官方计划、一分一档、投档线和选科要求未齐前不开放完整推荐。"),
                        ordinaryBatch(GX, "ZHUANKE", "高职高专普通批", 45,
                                "广西高职高专普通批当前仅展示历史估算能力；2026 专科计划和投档线未齐前不开放完整推荐。"),
                        queryOnlyBatch(GX, "EARLY", "普通类提前批", "EARLY", 20,
                                "广西提前批涉及顺序志愿、专项资格和单独计划，当前仅展示政策和数据缺口说明。"),
                        queryOnlyBatch(GX, "SPECIAL", "专项计划", "SPECIAL_PROGRAM", 20,
                                "广西专项计划需户籍、学籍、报名审核与单独计划数据，当前只查策略和资格提示。"),
                        queryOnlyBatch(GX, "ART", "艺术类批次", "ART", 45,
                                "广西艺术类需统考成绩、综合分规则和院校章程复核，不套普通位次模型。"),
                        queryOnlyBatch(GX, "SPORTS", "体育类批次", "SPORTS", 45,
                                "广西体育类需体育专业成绩、综合分或排序规则复核，不套普通位次模型。")
                ),
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY,
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY_LABEL,
                45,
                List.of("物理类", "历史类"),
                "广西招生考试院",
                "广西 2026 招生政策（待核实）",
                "广西 2026 官方普通类计划、分数线、一分一档、院校专业组投档线与选科要求仍待核验；当前只使用 2024/2025 历史窗口估算。",
                "广西 3+1+2 院校专业组适配已就位，普通主批仅历史估算，非普通批只查策略。"
        ));
        register(profiles, profile(
                HI,
                "海南",
                List.of(
                        ordinaryBatch(HI, "BENKE", "本科普通批", 30,
                                "海南本科普通批当前按 3+3 selectedSubjects 覆盖 requiredSubjects 口径展示历史估算能力；不按物理/历史分轨。"),
                        ordinaryBatch(HI, "ZHUANKE", "高职（专科）批", 30,
                                "海南高职（专科）批当前按 3+3 选科匹配口径展示历史估算能力；2026 计划和投档线未齐前不开放完整推荐。"),
                        queryOnlyBatch(HI, "EARLY", "本科提前批", "EARLY", 10,
                                "海南提前批涉及顺序志愿、资格和单独计划，当前仅展示政策和数据缺口说明。"),
                        queryOnlyBatch(HI, "SPECIAL", "专项/民族班/预科", "SPECIAL_PROGRAM", 10,
                                "海南专项、民族班和预科需资格审核与单独计划，当前只查策略和资格提示。"),
                        queryOnlyBatch(HI, "ART", "艺术类批次", "ART", 30,
                                "海南艺术类需统考成绩、综合分规则和院校章程复核，不套普通位次模型。"),
                        queryOnlyBatch(HI, "SPORTS", "体育类批次", "SPORTS", 30,
                                "海南体育类需体育专业成绩、综合分或排序规则复核，不套普通位次模型。")
                ),
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY,
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY_LABEL,
                30,
                List.of("综合选科"),
                "海南省考试局",
                "海南 2026 招生政策（待核实）",
                "海南 2026 正式实施办法、计划、成绩分布、投档线和选科要求仍待核验；当前只使用 2024/2025 历史窗口估算。",
                "海南 3+3 适配已就位，按 selectedSubjects 匹配 requiredSubjects，不使用物理/历史分轨。"
        ));
        register(profiles, profile(
                YN,
                "云南",
                List.of(
                        ordinaryBatch(YN, "BENKE", "普通本科批", 45,
                                "云南普通本科批当前按 2025 首年 3+1+2 口径展示历史估算能力；2024 旧文理只作弱参考，不进入主排序。"),
                        ordinaryBatch(YN, "ZHUANKE", "高职（专科）批", 45,
                                "云南高职（专科）批当前按 2025 首年新高考口径展示历史估算能力；历史窗口不足，风险更保守。"),
                        queryOnlyBatch(YN, "EARLY", "普通类提前批", "EARLY", 20,
                                "云南提前批涉及顺序志愿、专项资格和单独计划，当前仅展示政策和数据缺口说明。"),
                        queryOnlyBatch(YN, "SPECIAL", "专项计划", "SPECIAL_PROGRAM", 20,
                                "云南专项计划需户籍、学籍、报名审核与单独计划数据，当前只查策略和资格提示。"),
                        queryOnlyBatch(YN, "ART", "艺术类批次", "ART", 45,
                                "云南艺术类需统考成绩、综合分规则和院校章程复核，不套普通位次模型。"),
                        queryOnlyBatch(YN, "SPORTS", "体育类批次", "SPORTS", 45,
                                "云南体育类需体育专业成绩、综合分或排序规则复核，不套普通位次模型。")
                ),
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY,
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY_LABEL,
                45,
                List.of("物理类", "历史类"),
                "云南省招生考试院",
                "云南 2026 招生政策（待核实）",
                "云南 2026 录取方案、计划、分数线、组级投档线和选科要求仍待核验；2025 首年新高考样本有限，2024 旧文理只作弱参考。",
                "云南首年新高考适配已就位，普通主批仅历史估算，非普通批只查策略。"
        ));
        register(profiles, profile(
                HA,
                "河南",
                List.of(
                        ordinaryBatch(HA, "BENKE", "普通本科批", 45,
                                "河南普通本科批当前按 2025 首年 3+1+2 口径展示历史估算能力；大省位次波动和同分密度惩罚更保守。"),
                        ordinaryBatch(HA, "ZHUANKE", "高职（专科）批", 45,
                                "河南高职（专科）批当前按首年新高考历史窗口展示估算能力；专科计划和投档线未齐前不开放完整推荐。"),
                        queryOnlyBatch(HA, "EARLY", "普通类提前批", "EARLY", 20,
                                "河南提前批涉及顺序志愿、专项资格和单独计划，当前仅展示政策和数据缺口说明。"),
                        queryOnlyBatch(HA, "SPECIAL", "专项计划", "SPECIAL_PROGRAM", 20,
                                "河南专项计划需户籍、学籍、报名审核与单独计划数据，当前只查策略和资格提示。"),
                        queryOnlyBatch(HA, "ART", "艺术类批次", "ART", 45,
                                "河南艺术类需统考成绩、综合分规则和院校章程复核，不套普通位次模型。"),
                        queryOnlyBatch(HA, "SPORTS", "体育类批次", "SPORTS", 45,
                                "河南体育类需体育专业成绩、综合分或排序规则复核，不套普通位次模型。")
                ),
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY,
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY_LABEL,
                45,
                List.of("物理类", "历史类"),
                "河南省教育考试院",
                "河南 2026 招生政策（待核实）",
                "河南 2026 计划、线表、组级投档线、同分密度惩罚参数与大省波动口径仍待核验；当前只使用 2024/2025 历史窗口估算。",
                "河南首年新高考适配已就位，位次优先且风险更保守，非普通批只查策略。"
        ));
        register(profiles, levelOneProfile(
                CQ,
                "重庆",
                "重庆市教育考试院",
                "重庆当前地区正在接入历史数据，暂不开放完整志愿表生成，可先使用 AI 志愿问答和方向参考。"
        ));
        register(profiles, levelOneProfile(
                GS,
                "甘肃",
                "甘肃省教育考试院",
                "甘肃当前地区正在接入历史数据，暂不开放完整志愿表生成，可先使用 AI 志愿问答和方向参考。"
        ));
        register(profiles, levelOneProfile(
                XJ,
                "新疆",
                "新疆维吾尔自治区教育考试院",
                "新疆当前地区正在接入历史数据，暂不开放完整志愿表生成，可先使用 AI 志愿问答和方向参考。"
        ));
        return Collections.unmodifiableMap(profiles);
    }

    private static Profile levelOneProfile(String provinceCode,
                                           String provinceName,
                                           String officialSourceName,
                                           String dataStatusNote) {
        return profile(
                provinceCode,
                provinceName,
                List.of(
                        aiQaOnlyBatch(provinceCode, "BENKE", "普通本科批", 45,
                                dataStatusNote + " 当前不生成院校清单。"),
                        aiQaOnlyBatch(provinceCode, "ZHUANKE", "普通高职（专科）批", 45,
                                dataStatusNote + " 专科批历史数据补齐前不生成院校清单。"),
                        queryOnlyBatch(provinceCode, "EARLY", "普通类提前批", "EARLY", 20,
                                provinceName + "提前批涉及顺序志愿、资格审核和单独计划，当前仅展示政策与数据缺口说明。"),
                        queryOnlyBatch(provinceCode, "SPECIAL", "专项计划", "SPECIAL_PROGRAM", 20,
                                provinceName + "专项计划需户籍、学籍、报名审核与单独计划数据，当前只查策略和资格提示。"),
                        queryOnlyBatch(provinceCode, "ART", "艺术类批次", "ART", 45,
                                provinceName + "艺术类需统考成绩、综合分规则和院校章程复核，不套普通位次模型。"),
                        queryOnlyBatch(provinceCode, "SPORTS", "体育类批次", "SPORTS", 45,
                                provinceName + "体育类需体育专业成绩、综合分或排序规则复核，不套普通位次模型。")
                ),
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY,
                ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY_LABEL,
                45,
                List.of("物理类", "历史类"),
                officialSourceName,
                provinceName + " 2026 招生政策（待核实）",
                dataStatusNote,
                dataStatusNote
        );
    }

    private static BatchProfile ordinaryBatch(String provinceCode,
                                              String suffix,
                                              String batchName,
                                              int targetCount,
                                              String supportNote) {
        String batchCode = provinceCode + "_" + suffix;
        return new BatchProfile(
                batchCode,
                batchName,
                DEFAULT_CANDIDATE_TYPE,
                BatchRuleRegistry.CandidateCategory.ORDINARY.name(),
                BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name(),
                BatchRuleRegistry.RecommendMode.QUERY_ONLY.name(),
                com.gzly.service.recommend.QueryOnlyRecommendEngine.NAME,
                targetCount,
                6,
                true,
                "院校专业组（历史估算）",
                supportNote,
                List.of(
                        provinceCode + "_2026_plan",
                        provinceCode + "_2026_score_line",
                        provinceCode + "_2026_score_rank",
                        provinceCode + "_2026_major_requirement")
        );
    }

    private static BatchProfile aiQaOnlyBatch(String provinceCode,
                                              String suffix,
                                              String batchName,
                                              int targetCount,
                                              String supportNote) {
        String batchCode = provinceCode + "_" + suffix;
        return new BatchProfile(
                batchCode,
                batchName,
                DEFAULT_CANDIDATE_TYPE,
                BatchRuleRegistry.CandidateCategory.ORDINARY.name(),
                BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(),
                BatchRuleRegistry.RecommendMode.QUERY_ONLY.name(),
                QueryOnlyRecommendEngine.NAME,
                targetCount,
                6,
                true,
                "院校专业组（接入中）",
                supportNote,
                List.of(
                        "官方历史一分一段表",
                        "官方历史投档线",
                        "官方历史招生计划",
                        "官方选科要求")
        );
    }

    private static BatchProfile queryOnlyBatch(String provinceCode,
                                               String suffix,
                                               String batchName,
                                               String category,
                                               int targetCount,
                                               String supportNote) {
        String batchCode = provinceCode + "_" + suffix;
        String candidateType = switch (category) {
            case "ART" -> "艺术类";
            case "SPORTS" -> "体育类";
            default -> DEFAULT_CANDIDATE_TYPE;
        };
        return new BatchProfile(
                batchCode,
                batchName,
                candidateType,
                category,
                BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(),
                BatchRuleRegistry.RecommendMode.QUERY_ONLY.name(),
                "QueryOnlyRecommendEngine",
                targetCount,
                6,
                "ORDINARY".equals(category) || "EARLY".equals(category) || "SPECIAL_PROGRAM".equals(category),
                "院校专业组（只查策略）",
                supportNote,
                List.of(
                        provinceCode + "_2026_policy",
                        provinceCode + "_2026_plan",
                        provinceCode + "_2026_score_line",
                        category + "_qualification_or_composite_rule")
        );
    }

    private static void register(Map<String, Profile> profiles, Profile profile) {
        profiles.put(profile.provinceCode(), profile);
    }

    private static Profile profile(String provinceCode,
                                   String provinceName,
                                   List<BatchProfile> batches,
                                   String volunteerUnitType,
                                   String volunteerUnitLabel,
                                   int targetCount,
                                   List<String> subjectTypes,
                                   String officialSourceName,
                                   String officialSourceTitle,
                                   String missingDataNote,
                                   String supportNote) {
        return new Profile(
                provinceCode,
                provinceName,
                batches,
                volunteerUnitType,
                volunteerUnitLabel,
                targetCount,
                subjectTypes,
                officialSourceName,
                officialSourceTitle,
                "",
                supportNote + " 当前仅返回查询型院校专业组骨架，不构成录取承诺。",
                missingDataNote,
                supportNote
        );
    }

    public record Profile(
            String provinceCode,
            String provinceName,
            List<BatchProfile> batches,
            String volunteerUnitType,
            String volunteerUnitLabel,
            int targetCount,
            List<String> subjectTypes,
            String officialSourceName,
            String officialSourceTitle,
            String officialSourceUrl,
            String officialSourceText,
            String missingDataNote,
            String supportNote
    ) {
        public Profile {
            batches = batches == null ? List.of() : List.copyOf(batches);
            subjectTypes = subjectTypes == null ? List.of() : List.copyOf(subjectTypes);
        }

        public String defaultBatchCode() {
            return batches.isEmpty() ? provinceCode + "_BENKE" : batches.get(0).batchCode();
        }

        public String defaultBatchName() {
            return batches.isEmpty() ? "普通本科批" : batches.get(0).batchName();
        }

        public Optional<BatchProfile> findBatch(String batchCode) {
            if (batchCode == null || batchCode.isBlank()) {
                return batches.stream().findFirst();
            }
            String normalized = batchCode.trim().toUpperCase(Locale.ROOT);
            return batches.stream()
                    .filter(batch -> batch.batchCode().equalsIgnoreCase(normalized)
                            || batch.batchName().equals(batchCode.trim()))
                    .findFirst();
        }

        public ProvincePolicyService.ProvincePolicy toProvincePolicy() {
            ProvincePolicyService.ProvincePolicy policy = new ProvincePolicyService.ProvincePolicy();
            policy.setProvinceCode(provinceCode);
            policy.setProvinceName(provinceName);
            policy.setVolunteerUnitType(volunteerUnitType);
            policy.setVolunteerUnitLabel(volunteerUnitLabel);
            policy.setTargetBatch(defaultBatchName());
            policy.setTargetCount(targetCount);
            policy.setSubjectTypes(subjectTypes);
            policy.setOfficialSourceName(officialSourceName);
            return policy;
        }

        public PolicyRuleConfig syntheticConfig(int year, String candidateType) {
            return syntheticConfig(year, candidateType, defaultBatchCode());
        }

        public PolicyRuleConfig syntheticConfig(int year, String candidateType, String batchCode) {
            BatchProfile batch = findBatch(batchCode).orElseGet(() -> batches.isEmpty() ? null : batches.get(0));
            if (batch == null) {
                throw new IllegalArgumentException("Unsupported next province batch: " + batchCode);
            }
            PolicyRuleConfig config = new PolicyRuleConfig();
            config.setProvince(provinceCode);
            config.setYear(year);
            config.setCandidateType(candidateType == null || candidateType.isBlank()
                    ? batch.candidateType()
                    : candidateType.trim());
            config.setBatchCode(batch.batchCode());
            config.setBatchName(batch.batchName());
            config.setVolunteerMode(batch.volunteerMode());
            config.setMaxVolunteerCount(batch.targetCount());
            config.setMajorPerSchoolCount(batch.majorPerSchoolCount());
            config.setHasAdjustment(batch.hasAdjustment() ? 1 : 0);
            config.setFilingPrinciple(batch.recommendMode());
            config.setAdmissionOrder(batch.category());
            config.setPolicyStatus("registry_only");
            config.setOfficialSourceTitle(officialSourceTitle);
            config.setOfficialSourceUrl(officialSourceUrl);
            config.setOfficialSourceText(officialSourceText);
            config.setEnabled(1);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            return config;
        }

        public String pendingConfirmWarning() {
            return "当前年度政策待确认，请以" + officialSourceName + "最新文件为准";
        }

        public String dataStatusDetail() {
            return missingDataNote;
        }

        public List<String> missingData() {
            return batches.stream()
                    .flatMap(batch -> batch.missingData().stream())
                    .distinct()
                    .toList();
        }

        public String referenceNotice() {
            return provinceName + "当前处于 2026 官方数据待发布阶段，仅展示历史参考、策略说明和数据缺口，不构成录取承诺。";
        }

        public String supportNoteForBatch(String batchCode) {
            return findBatch(batchCode).map(BatchProfile::supportNote).orElse(supportNote);
        }

        public List<String> missingDataForBatch(String batchCode) {
            return findBatch(batchCode).map(BatchProfile::missingData).orElse(missingData());
        }
    }

    public record BatchProfile(
            String batchCode,
            String batchName,
            String candidateType,
            String category,
            String supportLevel,
            String recommendMode,
            String engineName,
            int targetCount,
            int majorPerSchoolCount,
            boolean hasAdjustment,
            String volunteerMode,
            String supportNote,
            List<String> missingData
    ) {
        public BatchProfile {
            missingData = missingData == null ? List.of() : List.copyOf(missingData);
        }

        public boolean ordinaryEstimate() {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)
                    && BatchRuleRegistry.CandidateCategory.ORDINARY.name().equals(category);
        }
    }
}
