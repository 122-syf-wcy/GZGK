package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("policy_rule_config")
public class PolicyRuleConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String province;
    private Integer year;
    private String candidateType;
    private String batchCode;
    private String batchName;
    private String volunteerMode;
    /** 志愿单位类型 MAJOR_96 / PROFESSIONAL_GROUP_45 / SCHOOL_SEQUENTIAL（20260812 迁移新增） */
    private String volunteerUnitType;
    private Integer maxVolunteerCount;
    private Integer majorPerSchoolCount;
    /** 组内专业志愿数：广西20/云南10/多数省6，专业+院校模式为0（20260812 迁移新增） */
    private Integer majorPerGroupCount;
    private Integer hasAdjustment;
    private String filingPrinciple;
    private String admissionOrder;
    /** 冲稳保垫目标数量与位次区间预设 JSON（20260812 迁移新增） */
    private String gradientPresetJson;
    /** 机会指数按省参数覆盖 JSON（20260812 迁移新增） */
    private String chanceParamsJson;
    /** 该批次可用历史数据最早年份 = 新高考首年（20260812 迁移新增） */
    private Integer dataYearFrom;
    private String policyStatus;
    private String officialSourceTitle;
    private String officialSourceUrl;
    private String officialSourceText;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
