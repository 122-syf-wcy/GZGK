package com.gzly.service;

import com.gzly.common.exception.BizException;
import com.gzly.entity.ProvinceProfile;
import com.gzly.mapper.ProvinceProfileMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 省份政策口径。
 *
 * <p>阶段 0/1 重构后的口径（docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md）：</p>
 * <ul>
 *   <li>支持全部 8 个省份（此前只有 GZ/SC/HB/AH，GX/HI/YN/HA 会直接抛异常）；</li>
 *   <li>内置 Java 常量为兜底事实源；`province_profile` 表存在且有数据时按表内容覆盖
 *       （subjectMode / newGaokaoFirstYear / scoreSystem / 官方来源），带 60 秒本地缓存；</li>
 *   <li>"能否生成"不在本类判断——由 {@link ProvinceReadinessService} 按真实数据行数门禁。</li>
 * </ul>
 *
 * <p>注意：volunteerUnitType 严格说是「省 × 批次」属性（河南提前批为专业+院校、本科批为专业组），
 * 本类保留的是该省普通类本科批的默认值；批次级差异以 policy_rule_config.volunteer_unit_type 为准。</p>
 */
@Slf4j
@Service
public class ProvincePolicyService {

    public static final String GZ = "GZ";
    public static final String SC = "SC";
    public static final String HB = "HB";
    public static final String AH = "AH";
    public static final String GX = "GX";
    public static final String HI = "HI";
    public static final String YN = "YN";
    public static final String HA = "HA";

    public static final String UNIT_MAJOR_96 = "MAJOR_96";
    public static final String UNIT_PROFESSIONAL_GROUP_45 = "PROFESSIONAL_GROUP_45";

    public static final String SUBJECT_MODE_312 = "3+1+2";
    public static final String SUBJECT_MODE_33 = "3+3";
    public static final String SCORE_SYSTEM_RAW_750 = "RAW_750";
    public static final String SCORE_SYSTEM_STANDARD_900 = "STANDARD_900";

    private static final long PROFILE_CACHE_TTL_MS = 60_000L;

    /**
     * 内置兜底口径。志愿数量与批次名为 2026 年官方核查值：
     * GZ 96（专业+院校）/ SC 45 / HB 45 / AH 45 / GX 40 / HI 30 / YN 40 / HA 48。
     */
    private static final Map<String, ProvincePolicy> POLICIES = buildDefaults();

    private static final Map<String, String> NAME_TO_CODE = Map.ofEntries(
            Map.entry("贵州", GZ), Map.entry("贵州省", GZ),
            Map.entry("四川", SC), Map.entry("四川省", SC),
            Map.entry("湖北", HB), Map.entry("湖北省", HB),
            Map.entry("安徽", AH), Map.entry("安徽省", AH),
            Map.entry("广西", GX), Map.entry("广西壮族自治区", GX),
            Map.entry("海南", HI), Map.entry("海南省", HI),
            Map.entry("云南", YN), Map.entry("云南省", YN),
            Map.entry("河南", HA), Map.entry("河南省", HA)
    );

    /** 可选依赖：表未建 / 单测直接 new 时保持全部兜底常量可用。 */
    @Autowired(required = false)
    private ProvinceProfileMapper provinceProfileMapper;

    private final Map<String, CachedProfile> profileCache = new ConcurrentHashMap<>();

    public ProvincePolicyService() {
    }

    public ProvincePolicyService(ProvinceProfileMapper provinceProfileMapper) {
        this.provinceProfileMapper = provinceProfileMapper;
    }

    public String normalizeProvinceCode(String provinceCode) {
        String raw = provinceCode == null ? "" : provinceCode.trim();
        String byName = NAME_TO_CODE.get(raw);
        if (byName != null) {
            return byName;
        }
        String value = raw.toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            return GZ;
        }
        if (!POLICIES.containsKey(value)) {
            throw new BizException("暂不支持该省份志愿生成");
        }
        return value;
    }

    public ProvincePolicy getPolicy(String provinceCode) {
        String code = normalizeProvinceCode(provinceCode);
        ProvincePolicy base = POLICIES.get(code);
        ProvinceProfile profile = loadProfile(code);
        if (profile == null) {
            return base;
        }
        ProvincePolicy merged = copy(base);
        if (notBlank(profile.getProvinceName())) merged.setProvinceName(profile.getProvinceName());
        if (notBlank(profile.getSubjectMode())) merged.setSubjectMode(profile.getSubjectMode());
        if (profile.getNewGaokaoFirstYear() != null && profile.getNewGaokaoFirstYear() > 0) {
            merged.setNewGaokaoFirstYear(profile.getNewGaokaoFirstYear());
        }
        if (notBlank(profile.getScoreSystem())) merged.setScoreSystem(profile.getScoreSystem());
        if (notBlank(profile.getOfficialSourceName())) merged.setOfficialSourceName(profile.getOfficialSourceName());
        return merged;
    }

    public List<ProvincePolicy> listPolicies() {
        return List.of(getPolicy(GZ), getPolicy(SC), getPolicy(HB), getPolicy(AH),
                getPolicy(GX), getPolicy(HI), getPolicy(YN), getPolicy(HA));
    }

    public boolean isProfessionalGroupProvince(String provinceCode) {
        ProvincePolicy policy = getPolicy(provinceCode);
        return UNIT_PROFESSIONAL_GROUP_45.equals(policy.getVolunteerUnitType());
    }

    public boolean isThreeThreeProvince(String provinceCode) {
        return SUBJECT_MODE_33.equals(getPolicy(provinceCode).getSubjectMode());
    }

    // ══════════════════ 内部 ══════════════════

    private ProvinceProfile loadProfile(String code) {
        if (provinceProfileMapper == null) {
            return null;
        }
        CachedProfile cached = profileCache.get(code);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < PROFILE_CACHE_TTL_MS) {
            return cached.profile;
        }
        ProvinceProfile profile = null;
        try {
            profile = provinceProfileMapper.selectById(code);
        } catch (Exception e) {
            // 表未创建或查询失败时静默回退内置常量，不影响主链路。
            log.debug("province_profile 读取失败，使用内置口径: code={}, err={}", code, e.getMessage());
        }
        profileCache.put(code, new CachedProfile(profile, now));
        return profile;
    }

    private static Map<String, ProvincePolicy> buildDefaults() {
        Map<String, ProvincePolicy> map = new LinkedHashMap<>();
        map.put(GZ, policy(GZ, "贵州", UNIT_MAJOR_96, "专业（类）+ 院校", "普通本科批", 96,
                List.of("物理类", "历史类"), "贵州省招生考试院", SUBJECT_MODE_312, 2024, SCORE_SYSTEM_RAW_750, 0));
        map.put(SC, policy(SC, "四川", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批B段", 45,
                List.of("物理类", "历史类"), "四川省教育考试院", SUBJECT_MODE_312, 2025, SCORE_SYSTEM_RAW_750, 6));
        map.put(HB, policy(HB, "湖北", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "本科普通批", 45,
                List.of("物理类", "历史类"), "湖北省教育考试院", SUBJECT_MODE_312, 2021, SCORE_SYSTEM_RAW_750, 6));
        map.put(AH, policy(AH, "安徽", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批次", 45,
                List.of("物理类", "历史类"), "安徽省教育招生考试院", SUBJECT_MODE_312, 2024, SCORE_SYSTEM_RAW_750, 6));
        map.put(GX, policy(GX, "广西", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "本科普通批", 40,
                List.of("物理类", "历史类"), "广西壮族自治区招生考试院", SUBJECT_MODE_312, 2024, SCORE_SYSTEM_RAW_750, 20));
        map.put(HI, policy(HI, "海南", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "本科普通批", 30,
                List.of("综合"), "海南省考试局", SUBJECT_MODE_33, 2020, SCORE_SYSTEM_STANDARD_900, 6));
        map.put(YN, policy(YN, "云南", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "本科批", 40,
                List.of("物理类", "历史类"), "云南省招生考试院", SUBJECT_MODE_312, 2025, SCORE_SYSTEM_RAW_750, 10));
        map.put(HA, policy(HA, "河南", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批", 48,
                List.of("物理类", "历史类"), "河南省教育考试院", SUBJECT_MODE_312, 2025, SCORE_SYSTEM_RAW_750, 6));
        return map;
    }

    private static ProvincePolicy policy(String code, String name, String unitType, String unitLabel,
                                         String targetBatch, int targetCount, List<String> subjectTypes,
                                         String officialSourceName, String subjectMode, int newGaokaoFirstYear,
                                         String scoreSystem, int majorPerGroupCount) {
        ProvincePolicy policy = new ProvincePolicy();
        policy.setProvinceCode(code);
        policy.setProvinceName(name);
        policy.setVolunteerUnitType(unitType);
        policy.setVolunteerUnitLabel(unitLabel);
        policy.setTargetBatch(targetBatch);
        policy.setTargetCount(targetCount);
        policy.setSubjectTypes(subjectTypes);
        policy.setOfficialSourceName(officialSourceName);
        policy.setSubjectMode(subjectMode);
        policy.setNewGaokaoFirstYear(newGaokaoFirstYear);
        policy.setScoreSystem(scoreSystem);
        policy.setMajorPerGroupCount(majorPerGroupCount);
        return policy;
    }

    private static ProvincePolicy copy(ProvincePolicy base) {
        ProvincePolicy copy = new ProvincePolicy();
        copy.setProvinceCode(base.getProvinceCode());
        copy.setProvinceName(base.getProvinceName());
        copy.setVolunteerUnitType(base.getVolunteerUnitType());
        copy.setVolunteerUnitLabel(base.getVolunteerUnitLabel());
        copy.setTargetBatch(base.getTargetBatch());
        copy.setTargetCount(base.getTargetCount());
        copy.setSubjectTypes(base.getSubjectTypes());
        copy.setOfficialSourceName(base.getOfficialSourceName());
        copy.setSubjectMode(base.getSubjectMode());
        copy.setNewGaokaoFirstYear(base.getNewGaokaoFirstYear());
        copy.setScoreSystem(base.getScoreSystem());
        copy.setMajorPerGroupCount(base.getMajorPerGroupCount());
        return copy;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record CachedProfile(ProvinceProfile profile, long loadedAt) {
    }

    @Data
    public static class ProvincePolicy {
        private String provinceCode;
        private String provinceName;
        /** 该省普通类本科批默认志愿单位；批次级差异以 policy_rule_config 为准 */
        private String volunteerUnitType;
        private String volunteerUnitLabel;
        private String targetBatch;
        private int targetCount;
        private List<String> subjectTypes;
        private String officialSourceName;
        /** 3+1+2 / 3+3 */
        private String subjectMode = SUBJECT_MODE_312;
        /** 新高考首年 */
        private int newGaokaoFirstYear;
        /** RAW_750 / STANDARD_900 */
        private String scoreSystem = SCORE_SYSTEM_RAW_750;
        /** 组内专业志愿数（专业+院校模式为 0） */
        private int majorPerGroupCount;
    }
}
