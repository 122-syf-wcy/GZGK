package com.gzly.common;

public final class ComplianceConstants {
    private ComplianceConstants() {
    }

    public static final String DISCLAIMER_VERSION = "2026-04-27-v1";

    public static final String DISCLAIMER_CONFIRM_ERROR = "请先阅读并确认生成前风险告知";

    public static final String AI_GENERATED_NOTICE = "本内容由 AI 生成，仅供参考。";

    public static final String SAFE_ASSISTANT_NOTICE =
            "本系统基于公开招生数据、历史录取数据、考生输入信息及模型计算结果生成志愿辅助参考，"
                    + "不构成任何录取承诺。实际录取结果以贵州省招生考试院、高校招生章程、当年招生计划和正式投档录取结果为准。";

    public static final String REFERENCE_PROBABILITY_NOTICE =
            "本系统输出为「机会指数」「风险等级」「数据参考度」与梯度建议，"
                    + "基于历史投档位次分布、官方一分一段表、招生计划变化和模型计算结果整理得出，"
                    + "仅供辅助参考，不构成任何录取承诺。最终结果以省级招生考试机构、各高校"
                    + "招生章程、官方招生专业目录、当年招生计划和正式投档录取结果为准。";
}
