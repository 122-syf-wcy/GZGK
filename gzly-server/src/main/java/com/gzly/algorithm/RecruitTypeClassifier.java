package com.gzly.algorithm;

import java.util.List;
import java.util.Locale;

/**
 * 招生类型（recruitType）归一工具。
 *
 * <p>对应《贵州省高考志愿辅助系统推荐算法与梯度规则优化研究报告》中提出的
 * 「专业（类）+ 院校 + recruit_type」复合主键约束：把当前主要靠关键词匹配的
 * 「专项 / 提前批 / 艺术体育 / 军警 / 性别限制 / 定向 / 师范履约」等情形
 * 归一到一组稳定的枚举字符串，供 VolunteerItem.recruitType 输出和 PlanMetrics 计数使用。</p>
 *
 * <p>归一后的取值同时回答两个问题：
 * <ul>
 *   <li>{@link #classify(String, String, String, List)} 返回该条招生记录属于哪一类；</li>
 *   <li>{@link #isExclusiveFromMainList(String)} 表示该类型是否应当从普通志愿主列表中剔除。</li>
 * </ul>
 * 不依赖数据库结构变更，保证可灰度上线。</p>
 */
public final class RecruitTypeClassifier {

    /** 普通类（无特殊招生限制），可进入普通本科批 96 / 专科批 96 / 提前批 60 主列表。 */
    public static final String NORMAL = "NORMAL";
    /** 专项计划：国家 / 地方 / 高校专项；不进入普通志愿主列表。 */
    public static final String SPECIAL_PROGRAM = "SPECIAL_PROGRAM";
    /** 本科 / 专科提前批；不进入普通本科批主列表。 */
    public static final String PRE_BATCH = "PRE_BATCH";
    /** 军校、武警、公安、消防救援等需要政审 / 体测 / 单独投档。 */
    public static final String MILITARY_POLICE = "MILITARY_POLICE";
    /** 艺术、体育类联考 / 校考。 */
    public static final String ART_SPORTS = "ART_SPORTS";
    /** 仅招男生 / 仅招女生等性别限制条目。 */
    public static final String GENDER_RESTRICTED = "GENDER_RESTRICTED";
    /** 公费 / 免费 / 优师专项等定向 / 师范履约类型。 */
    public static final String FREE_NORMAL = "FREE_NORMAL";
    /** 其它定向：医学、矿业、农村三支等明确履约义务的类型。 */
    public static final String DIRECTED = "DIRECTED";
    /** 征集志愿：投档时间、规则、剩余计划与常规批次显著不同。 */
    public static final String SUPPLEMENT = "SUPPLEMENT";

    private RecruitTypeClassifier() {
    }

    /**
     * 把单条候选归类成稳定的 recruit_type 字符串。
     *
     * @param schoolName     学校名称（可空）
     * @param majorName      专业名称（可空）
     * @param batch          批次文案（可空）
     * @param universityTags 学校标签列表（可空）
     * @return {@link #NORMAL}…{@link #SUPPLEMENT} 之一，无关键字命中默认归 {@link #NORMAL}
     */
    public static String classify(String schoolName, String majorName, String batch, List<String> universityTags) {
        String text = (safe(schoolName) + " " + safe(majorName) + " " + safe(batch)).toLowerCase(Locale.ROOT);
        // 注意顺序：提前批/专项/军警/艺体/性别 优先于一般"定向/师范"判定，避免被覆盖。
        if (containsAny(text, "国家专项", "地方专项", "高校专项", "专项计划", "专项")) {
            return SPECIAL_PROGRAM;
        }
        if (containsAny(text, "提前批", "本科提前", "提前本科", "专科提前")) {
            return PRE_BATCH;
        }
        if (containsAny(text, "征集志愿", "征集")) {
            return SUPPLEMENT;
        }
        boolean hasMilitaryTag = universityTags != null && universityTags.contains("军事");
        if (hasMilitaryTag
                || containsAny(text, "军校", "军医", "公安", "警察", "警校", "国防", "武警", "刑侦", "消防救援")) {
            return MILITARY_POLICE;
        }
        if (containsAny(text, "艺术", "美术", "音乐", "舞蹈", "播音", "戏剧", "影视", "体育", "运动训练")) {
            return ART_SPORTS;
        }
        if (containsAny(text, "只招男生", "只招女生", "仅招男", "仅招女", "男生", "女生")) {
            return GENDER_RESTRICTED;
        }
        if (containsAny(text, "公费师范", "免费师范", "优师专项", "公费医学", "免费医学")) {
            return FREE_NORMAL;
        }
        if (containsAny(text, "定向")) {
            return DIRECTED;
        }
        return NORMAL;
    }

    /** 该 recruitType 是否应当被普通志愿主列表排除。 */
    public static boolean isExclusiveFromMainList(String recruitType) {
        if (recruitType == null) return false;
        return SPECIAL_PROGRAM.equals(recruitType)
                || PRE_BATCH.equals(recruitType)
                || MILITARY_POLICE.equals(recruitType)
                || ART_SPORTS.equals(recruitType)
                || GENDER_RESTRICTED.equals(recruitType)
                || FREE_NORMAL.equals(recruitType)
                || DIRECTED.equals(recruitType)
                || SUPPLEMENT.equals(recruitType);
    }

    /**
     * 给前端 / 文案系统使用的中文短描述，仅在被排除时返回非空字符串。
     */
    public static String exclusionReason(String recruitType) {
        if (recruitType == null) return "";
        switch (recruitType) {
            case SPECIAL_PROGRAM:
                return "专项计划不进入普通志愿主列表";
            case PRE_BATCH:
                return "提前批不进入普通志愿主列表";
            case MILITARY_POLICE:
                return "军警公安等特殊类型需单独核验";
            case ART_SPORTS:
                return "艺术体育类不进入普通志愿主列表";
            case GENDER_RESTRICTED:
                return "存在性别限制，需人工核验";
            case FREE_NORMAL:
                return "公费/免费/优师等履约类型需单独核验";
            case DIRECTED:
                return "定向类型需单独核验";
            case SUPPLEMENT:
                return "征集志愿需按官方批次单独建模，不进入主列表";
            default:
                return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isEmpty() || keywords == null) return false;
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) continue;
            if (text.contains(kw)) return true;
            String lower = kw.toLowerCase(Locale.ROOT);
            if (text.contains(lower)) return true;
        }
        return false;
    }
}
