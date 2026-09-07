package com.gzly.algorithm.core;

/**
 * 志愿单位类型。注意这是「省 × 批次」的属性，不是省的属性：
 * 河南本科提前批为专业+院校、本科批为院校专业组；贵州提前批 A/B 段为院校顺序志愿。
 *
 * <p>legacyCode 对应 policy_rule_config.volunteer_unit_type 与 biz_plan_history.volunteer_unit_type
 * 既有存量值（"PROFESSIONAL_GROUP_45" 中的 45 是历史命名，实际志愿数以批次政策 maxVolunteerCount 为准，
 * 各省为 30-48 不等）。</p>
 */
public enum VolunteerUnitType {

    /** 专业（类）+ 院校：贵州本科批 96、河南提前批 64。无组内调剂概念。 */
    MAJOR_PLUS_SCHOOL("MAJOR_96"),

    /** 院校专业组：川/鄂/皖/桂/琼/滇/豫本科批。有组内专业分配与服从调剂。 */
    PROFESSIONAL_GROUP("PROFESSIONAL_GROUP_45"),

    /** 院校顺序志愿：贵州提前批 A/B 段等。志愿优先而非分数优先，算法后置实现。 */
    SCHOOL_SEQUENTIAL("SCHOOL_SEQUENTIAL");

    private final String legacyCode;

    VolunteerUnitType(String legacyCode) {
        this.legacyCode = legacyCode;
    }

    public String legacyCode() {
        return legacyCode;
    }

    public static VolunteerUnitType fromLegacyCode(String code) {
        if (code != null) {
            for (VolunteerUnitType type : values()) {
                if (type.legacyCode.equals(code.trim())) {
                    return type;
                }
            }
        }
        return MAJOR_PLUS_SCHOOL;
    }
}
