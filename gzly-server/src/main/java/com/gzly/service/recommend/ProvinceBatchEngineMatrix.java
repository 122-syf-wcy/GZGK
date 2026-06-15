package com.gzly.service.recommend;

import com.gzly.service.AnhuiBatchRuleRegistry;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.HubeiBatchRuleRegistry;
import com.gzly.service.NextProvincePolicyRegistry;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.SichuanBatchRuleRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 省 × 批次 → 推荐引擎名称 的静态映射表。
 *
 * <p>设计目标：</p>
 * <ul>
 *   <li>每个省的每个批次都有显式 engineName，便于前端、运维、ML 训练侧按引擎名区分行为</li>
 *   <li>不复制业务生成逻辑（生成本身仍在 VolunteerService.generate / ProfessionalGroupVolunteerService.generate
 *       / SichuanBatchListingService / AnhuiBatchListingService 中），但路由与决策按本表统一</li>
 *   <li>新增省份只需新增 register{Province}() 方法</li>
 * </ul>
 *
 * <p>覆盖范围（截至 v7.50）：</p>
 * <ul>
 *   <li>GZ × 18 批次（贵州，普通本科批 / 提前 A/B/C / 专业本科 / 8 类专项 / 艺术 / 体育）</li>
 *   <li>SC × 18 批次（四川，本科批B 主 + 17 个非主流程）</li>
 *   <li>AH × 14 批次（安徽，普通本科批 / 高职专科 / 提前批 / 4 子艺术 / 2 子体育 / 3 子专项）</li>
 *   <li>HB × 6 批次（湖北，本科普通批历史估算 + 5 个策略建议批次）</li>
 *   <li>GX / HI / YN / HA × 6 查询型批次（PRE_OFFICIAL_DATA query-only 骨架）</li>
 * </ul>
 *
 * <p>未声明的 (province, batch) 组合返回 {@link QueryOnlyRecommendEngine#NAME} 作为兜底，
 * 永不静默回退到其它批次。</p>
 */
public final class ProvinceBatchEngineMatrix {

    private static final Map<String, String> MATRIX = buildMatrix();

    private ProvinceBatchEngineMatrix() {
    }

    /**
     * 查询 (province, batch) → engineName。
     *
     * @param provinceCode 省份代码（GZ / SC / AH / HB）
     * @param batchCode    批次代码（未归一化亦可）
     * @return engineName；未命中时返回 {@link QueryOnlyRecommendEngine#NAME}
     */
    public static String resolveEngineName(String provinceCode, String batchCode) {
        return resolveEngineNameOptional(provinceCode, batchCode).orElse(QueryOnlyRecommendEngine.NAME);
    }

    /**
     * 查询 (province, batch) → engineName 的 Optional 版本，便于上层做"未命中走兜底"的差异化处理。
     */
    public static Optional<String> resolveEngineNameOptional(String provinceCode, String batchCode) {
        String key = key(provinceCode, batchCode);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(MATRIX.get(key));
    }

    /**
     * 列出某省下所有已注册的 (batchCode → engineName) 映射，给运维和测试用。
     */
    public static Map<String, String> entriesForProvince(String provinceCode) {
        String prefix = normalizeProvinceCode(provinceCode) + "|";
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : MATRIX.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static int totalRegistrations() {
        return MATRIX.size();
    }

    private static String key(String provinceCode, String batchCode) {
        String p = normalizeProvinceCode(provinceCode);
        if (batchCode == null || batchCode.isBlank()) return null;
        return p + "|" + batchCode.trim();
    }

    private static String normalizeProvinceCode(String code) {
        if (code == null || code.isBlank()) return ProvincePolicyService.GZ;
        return code.trim().toUpperCase();
    }

    private static Map<String, String> buildMatrix() {
        Map<String, String> m = new LinkedHashMap<>();
        registerGuizhou(m);
        registerSichuan(m);
        registerHubei(m);
        registerAnhui(m);
        registerNextProvinces(m);
        return Collections.unmodifiableMap(m);
    }

    // ============================================================
    // 贵州 18 批次（与 BatchRuleRegistry 严格对齐）
    // ============================================================
    private static void registerGuizhou(Map<String, String> m) {
        // 普通本科 / 普通专科（主流程，96 平行专业）
        gz(m, "NORMAL_UNDERGRADUATE", OrdinaryParallelMajorEngine.NAME);
        gz(m, "NORMAL_SPECIALTY", OrdinaryParallelMajorEngine.NAME);

        // 提前批 A / B 段（顺序）
        gz(m, "EARLY_A_B", SequentialCollegeEngine.NAME);
        gz(m, "SPECIALTY_EARLY", SequentialCollegeEngine.NAME);

        // 提前批 C 段（平行专业）
        gz(m, "EARLY_C", EarlyCParallelMajorEngine.NAME);

        // 艺术（本科 A / B / 专科）
        gz(m, "ART_UNDERGRADUATE_A", ArtCompositeRecommendEngine.NAME);
        gz(m, "ART_UNDERGRADUATE_B", ArtCompositeRecommendEngine.NAME);
        gz(m, "ART_SPECIALTY", ArtCompositeRecommendEngine.NAME);

        // 体育（本科 / 专科）
        gz(m, "SPORTS_UNDERGRADUATE", SportsCompositeRecommendEngine.NAME);
        gz(m, "SPORTS_SPECIALTY", SportsCompositeRecommendEngine.NAME);

        // 8 类专项 / 特殊
        gz(m, "NATIONAL_SPECIAL", SpecialPlanEligibilityEngine.NAME);
        gz(m, "LOCAL_SPECIAL", SpecialPlanEligibilityEngine.NAME);
        gz(m, "UNIVERSITY_SPECIAL", SpecialPlanEligibilityEngine.NAME);
        gz(m, "ETHNIC_CLASS", SpecialPlanEligibilityEngine.NAME);
        gz(m, "PREPARATORY", SpecialPlanEligibilityEngine.NAME);
        gz(m, "ORIENTED", SpecialPlanEligibilityEngine.NAME);
        gz(m, "FREE_MEDICAL", SpecialPlanEligibilityEngine.NAME);
        gz(m, "TEACHER_EXCELLENCE", SpecialPlanEligibilityEngine.NAME);
    }

    // ============================================================
    // 四川 18 批次（与 SichuanBatchRuleRegistry 严格对齐）
    // ============================================================
    private static void registerSichuan(Map<String, String> m) {
        // 本科批 B / 高职专科 B（主流程）
        sc(m, "SC_BENKE_B", SichuanProfessionalGroup45Engine.NAME);
        sc(m, "SC_ZHUANKE_B", SichuanProfessionalGroup45Engine.NAME);

        // 提前批 A / 高校专项顺序 / 高职专科提前批 / 高水平运动队（顺序）
        sc(m, "SC_TIQIAN_A", SichuanSequentialCollegeEngine.NAME);
        sc(m, "SC_GAOXIAO_SPECIAL_PRE_B", SichuanSequentialCollegeEngine.NAME);
        sc(m, "SC_ZHUANKE_EARLY", SichuanSequentialCollegeEngine.NAME);
        sc(m, "SC_BENKE_SPORTS_TEAM", SichuanSequentialCollegeEngine.NAME);

        // 提前批 B（30 平行院校专业组：公费师范 / 优师 / 免费医学定向）
        sc(m, "SC_TIQIAN_B", SichuanProfessionalGroup45Engine.NAME);

        // 8 类专项（本科批 A 段国家/地方/高校专项、区域均衡、少数民族预科、提前批 A 段前国家专项）
        sc(m, "SC_TIQIAN_BEFORE_A_NATIONAL", SichuanSpecialPlanEligibilityEngine.NAME);
        sc(m, "SC_BENKE_A_NATIONAL", SichuanSpecialPlanEligibilityEngine.NAME);
        sc(m, "SC_BENKE_A_LOCAL", SichuanSpecialPlanEligibilityEngine.NAME);
        sc(m, "SC_BENKE_GAOXIAO_SPECIAL", SichuanSpecialPlanEligibilityEngine.NAME);
        sc(m, "SC_BENKE_REGION_BALANCE", SichuanSpecialPlanEligibilityEngine.NAME);
        sc(m, "SC_BENKE_MINORITY_PRE", SichuanSpecialPlanEligibilityEngine.NAME);

        // 艺术（提前 / 本科 / 专科）
        sc(m, "SC_ART_TIQIAN", SichuanArtCompositeEngine.NAME);
        sc(m, "SC_ART_BENKE", SichuanArtCompositeEngine.NAME);
        sc(m, "SC_ART_ZHUANKE", SichuanArtCompositeEngine.NAME);

        // 体育（本科 / 专科）
        sc(m, "SC_SPORTS_BENKE", SichuanSportsCompositeEngine.NAME);
        sc(m, "SC_SPORTS_ZHUANKE", SichuanSportsCompositeEngine.NAME);
    }

    private static void registerHubei(Map<String, String> m) {
        hb(m, "HB_BENKE", "HubeiProfessionalGroup45Engine");
        hb(m, "HB_ZHUANKE", QueryOnlyRecommendEngine.NAME);
        hb(m, "HB_EARLY", QueryOnlyRecommendEngine.NAME);
        hb(m, "HB_SPECIAL", QueryOnlyRecommendEngine.NAME);
        hb(m, "HB_ART", QueryOnlyRecommendEngine.NAME);
        hb(m, "HB_SPORTS", QueryOnlyRecommendEngine.NAME);
    }

    // ============================================================
    // 安徽 14 批次（与 AnhuiBatchRuleRegistry 严格对齐）
    // ============================================================
    private static void registerAnhui(Map<String, String> m) {
        // 本科批 / 高职专科批（主流程）
        ah(m, "AH_BENKE", AnhuiProfessionalGroup45Engine.NAME);
        ah(m, "AH_ZHUANKE", AnhuiProfessionalGroup45Engine.NAME);

        // 本科 / 高职提前批平行（6 子类合并 20 平行 / 定向培养军士等）
        ah(m, "AH_TIQIAN_BENKE_PARALLEL", AnhuiSpecialPlanEligibilityEngine.NAME);
        ah(m, "AH_TIQIAN_ZHUANKE_PARALLEL", AnhuiSpecialPlanEligibilityEngine.NAME);

        // 本科 / 高职提前批顺序（司法/应急/其他类）
        ah(m, "AH_TIQIAN_BENKE_SEQUENTIAL", AnhuiSequentialCollegeEngine.NAME);
        ah(m, "AH_TIQIAN_ZHUANKE_SEQUENTIAL", AnhuiSequentialCollegeEngine.NAME);

        // 3 类专项（国家专项 20 平行、地方专项 20 平行、高校专项 1 顺序）
        ah(m, "AH_NATIONAL_SPECIAL", AnhuiSpecialPlanEligibilityEngine.NAME);
        ah(m, "AH_LOCAL_SPECIAL", AnhuiSpecialPlanEligibilityEngine.NAME);
        ah(m, "AH_UNIVERSITY_SPECIAL", AnhuiSequentialCollegeEngine.NAME);

        // 艺术（校考本科 1 顺序、统考本科 20 平行、统考高职 20 平行）
        ah(m, "AH_ART_XIAOKAO_BENKE", AnhuiSequentialCollegeEngine.NAME);
        ah(m, "AH_ART_TONGKAO_BENKE", AnhuiArtCompositeEngine.NAME);
        ah(m, "AH_ART_TONGKAO_ZHUANKE", AnhuiArtCompositeEngine.NAME);

        // 体育（本科 20 平行、高职 20 平行）
        ah(m, "AH_SPORTS_BENKE", AnhuiSportsCompositeEngine.NAME);
        ah(m, "AH_SPORTS_ZHUANKE", AnhuiSportsCompositeEngine.NAME);
    }

    private static void registerNextProvinces(Map<String, String> m) {
        for (NextProvincePolicyRegistry.Profile profile : NextProvincePolicyRegistry.allProfiles()) {
            for (NextProvincePolicyRegistry.BatchProfile batch : profile.batches()) {
                m.put(profile.provinceCode() + "|" + batch.batchCode(), batch.engineName());
            }
        }
    }

    private static void gz(Map<String, String> m, String batch, String engine) {
        m.put(ProvincePolicyService.GZ + "|" + batch, engine);
    }

    private static void sc(Map<String, String> m, String batch, String engine) {
        m.put(ProvincePolicyService.SC + "|" + batch, engine);
    }

    private static void hb(Map<String, String> m, String batch, String engine) {
        m.put(ProvincePolicyService.HB + "|" + batch, engine);
    }

    private static void ah(Map<String, String> m, String batch, String engine) {
        m.put(ProvincePolicyService.AH + "|" + batch, engine);
    }

    /**
     * 启动期自检：验证矩阵里每条 SC/AH 注册都能在对应 Registry 中找到批次规则，
     * 避免代码漂移（Registry 加批次但忘了注册 engine，或反之）。
     */
    public static SelfCheckResult selfCheck() {
        SelfCheckResult result = new SelfCheckResult();
        for (Map.Entry<String, String> entry : MATRIX.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String province = parts[0];
            String batch = parts[1];
            boolean known;
            if (ProvincePolicyService.GZ.equals(province)) {
                known = BatchRuleRegistry.find(batch).isPresent();
            } else if (ProvincePolicyService.SC.equals(province)) {
                known = SichuanBatchRuleRegistry.find(batch).isPresent();
            } else if (ProvincePolicyService.HB.equals(province)) {
                known = HubeiBatchRuleRegistry.find(batch).isPresent();
            } else if (ProvincePolicyService.AH.equals(province)) {
                known = AnhuiBatchRuleRegistry.find(batch).isPresent();
            } else if (NextProvincePolicyRegistry.find(province).isPresent()) {
                known = NextProvincePolicyRegistry.require(province).findBatch(batch).isPresent();
            } else {
                known = false;
            }
            if (!known) {
                result.unknownBatches.add(entry.getKey());
            }
        }
        // 反向：Registry 里有的批次，矩阵也应该注册
        for (BatchRuleRegistry.BatchRule rule : BatchRuleRegistry.allRules()) {
            if (!MATRIX.containsKey(ProvincePolicyService.GZ + "|" + rule.batchCode())) {
                result.unregisteredBatches.add(ProvincePolicyService.GZ + "|" + rule.batchCode());
            }
        }
        for (SichuanBatchRuleRegistry.BatchRule rule : SichuanBatchRuleRegistry.allRules()) {
            if (!MATRIX.containsKey(ProvincePolicyService.SC + "|" + rule.batchCode())) {
                result.unregisteredBatches.add(ProvincePolicyService.SC + "|" + rule.batchCode());
            }
        }
        for (HubeiBatchRuleRegistry.BatchRule rule : HubeiBatchRuleRegistry.allRules()) {
            if (!MATRIX.containsKey(ProvincePolicyService.HB + "|" + rule.batchCode())) {
                result.unregisteredBatches.add(ProvincePolicyService.HB + "|" + rule.batchCode());
            }
        }
        for (AnhuiBatchRuleRegistry.BatchRule rule : AnhuiBatchRuleRegistry.allRules()) {
            if (!MATRIX.containsKey(ProvincePolicyService.AH + "|" + rule.batchCode())) {
                result.unregisteredBatches.add(ProvincePolicyService.AH + "|" + rule.batchCode());
            }
        }
        for (NextProvincePolicyRegistry.Profile profile : NextProvincePolicyRegistry.allProfiles()) {
            for (NextProvincePolicyRegistry.BatchProfile batch : profile.batches()) {
                if (!MATRIX.containsKey(profile.provinceCode() + "|" + batch.batchCode())) {
                    result.unregisteredBatches.add(profile.provinceCode() + "|" + batch.batchCode());
                }
            }
        }
        return result;
    }

    public static final class SelfCheckResult {
        public final java.util.List<String> unknownBatches = new java.util.ArrayList<>();
        public final java.util.List<String> unregisteredBatches = new java.util.ArrayList<>();

        public boolean ok() {
            return unknownBatches.isEmpty() && unregisteredBatches.isEmpty();
        }

        @Override
        public String toString() {
            return "SelfCheckResult{unknownBatches=" + unknownBatches
                    + ", unregisteredBatches=" + unregisteredBatches + "}";
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SelfCheckResult)) return false;
            SelfCheckResult that = (SelfCheckResult) o;
            return Objects.equals(unknownBatches, that.unknownBatches)
                    && Objects.equals(unregisteredBatches, that.unregisteredBatches);
        }

        public int hashCode() {
            return Objects.hash(unknownBatches, unregisteredBatches);
        }
    }
}
