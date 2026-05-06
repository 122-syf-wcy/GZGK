package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_plan_history")
public class PlanHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String clientIp;
    /** 省份代码：GZ / SC */
    private String provinceCode;
    /** 志愿单位类型：MAJOR_96 / PROFESSIONAL_GROUP_45 */
    private String volunteerUnitType;
    /** 目标批次 */
    private String targetBatch;
    private Integer agreedDisclaimer;
    private String disclaimerVersion;
    private LocalDateTime disclaimerConfirmedAt;
    private Integer totalScore;
    private Integer provinceRank;
    private String firstSubject;
    private String resubjects;
    /** 意向专业(JSON 数组) */
    private String preferredMajors;
    /** 意向地区(JSON 数组) */
    private String preferredRegions;
    /** 方案取向：保守型 / 均衡型 / 冲刺型 */
    private String strategyMode;
    /** 决策优先级：学校优先 / 专业优先 */
    private String decisionPriority;
    /** 长期目标 */
    private String careerGoal;
    /** 预算偏好 */
    private String tuitionBudget;
    /** 是否接受民办 0/1，可空 */
    private Integer acceptPrivate;
    /** 是否接受中外/港澳台合作 0/1，可空 */
    private Integer acceptSinoForeign;
    private String planJson;
    private Integer itemCount;
    private String aiAnalysis;
    /** 数据质量警告（用户可见） */
    private String dataQualityWarning;
    /** 强制人工复核清单(JSON 数组) */
    private String manualReviewJson;
    /** 生成耗时/复核率等监控指标(JSON) */
    private String metricsJson;
    /** 完整 GenerateRequest 快照(JSON) */
    private String requestSnapshotJson;
    private String safetyCodeHash;
    private LocalDateTime createdAt;
}
