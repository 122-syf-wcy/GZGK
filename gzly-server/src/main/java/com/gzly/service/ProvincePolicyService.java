package com.gzly.service;

import com.gzly.common.exception.BizException;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final Map<String, ProvincePolicy> POLICIES = Map.of(
            GZ, policy(GZ, "贵州", UNIT_MAJOR_96, "专业（类）+ 院校", "普通本科批", 96,
                    List.of("物理类", "历史类"), "贵州省招生考试院"),
            SC, policy(SC, "四川", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批B段", 45,
                    List.of("物理类", "历史类"), "四川省教育考试院"),
            HB, policy(HB, "湖北", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "本科普通批", 45,
                    List.of("物理类", "历史类"), "湖北省教育考试院"),
            AH, policy(AH, "安徽", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批次", 45,
                    List.of("物理类", "历史类"), "安徽省教育招生考试院"),
            GX, policy(GX, "广西", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批", 45,
                    List.of("物理类", "历史类"), "广西壮族自治区招生考试院"),
            HI, policy(HI, "海南", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "本科普通批", 30,
                    List.of("综合改革"), "海南省考试局"),
            YN, policy(YN, "云南", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批", 45,
                    List.of("物理类", "历史类"), "云南省招生考试院"),
            HA, policy(HA, "河南", UNIT_PROFESSIONAL_GROUP_45, "院校专业组", "普通本科批", 45,
                    List.of("物理类", "历史类"), "河南省教育考试院")
    );

    public String normalizeProvinceCode(String provinceCode) {
        String raw = provinceCode == null ? "" : provinceCode.trim();
        if ("贵州".equals(raw) || "贵州省".equals(raw)) {
            return GZ;
        }
        if ("四川".equals(raw) || "四川省".equals(raw)) {
            return SC;
        }
        if ("湖北".equals(raw) || "湖北省".equals(raw)) {
            return HB;
        }
        if ("安徽".equals(raw) || "安徽省".equals(raw)) {
            return AH;
        }
        if ("广西".equals(raw) || "广西壮族自治区".equals(raw)) {
            return GX;
        }
        if ("海南".equals(raw) || "海南省".equals(raw)) {
            return HI;
        }
        if ("云南".equals(raw) || "云南省".equals(raw)) {
            return YN;
        }
        if ("河南".equals(raw) || "河南省".equals(raw)) {
            return HA;
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
        return POLICIES.get(normalizeProvinceCode(provinceCode));
    }

    public List<ProvincePolicy> listPolicies() {
        return List.of(POLICIES.get(GZ), POLICIES.get(SC), POLICIES.get(HB), POLICIES.get(AH),
                POLICIES.get(GX), POLICIES.get(HI), POLICIES.get(YN), POLICIES.get(HA));
    }

    public boolean isProfessionalGroupProvince(String provinceCode) {
        ProvincePolicy policy = getPolicy(provinceCode);
        return UNIT_PROFESSIONAL_GROUP_45.equals(policy.getVolunteerUnitType());
    }

    private static ProvincePolicy policy(String code, String name, String unitType, String unitLabel,
                                         String targetBatch, int targetCount, List<String> subjectTypes,
                                         String officialSourceName) {
        ProvincePolicy policy = new ProvincePolicy();
        policy.setProvinceCode(code);
        policy.setProvinceName(name);
        policy.setVolunteerUnitType(unitType);
        policy.setVolunteerUnitLabel(unitLabel);
        policy.setTargetBatch(targetBatch);
        policy.setTargetCount(targetCount);
        policy.setSubjectTypes(subjectTypes);
        policy.setOfficialSourceName(officialSourceName);
        return policy;
    }

    @Data
    public static class ProvincePolicy {
        private String provinceCode;
        private String provinceName;
        private String volunteerUnitType;
        private String volunteerUnitLabel;
        private String targetBatch;
        private int targetCount;
        private List<String> subjectTypes;
        private String officialSourceName;
    }
}
