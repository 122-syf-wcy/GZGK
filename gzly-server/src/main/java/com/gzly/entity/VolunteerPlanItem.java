package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("volunteer_plan_item")
public class VolunteerPlanItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Integer volunteerIndex;
    private String gradient;
    private String schoolCode;
    private String schoolName;
    private String majorCode;
    private String majorName;
    private String schoolCity;
    private String tuition;
    private Integer candidateRank;
    private Integer predictedMinRank;
    private Integer rankDiff;
    private BigDecimal internalScore;
    private Integer chanceScore;
    private String chanceLevel;
    private String riskLevel;
    private String confidenceLevel;
    private BigDecimal dataConfidence;
    private BigDecimal finalScore;
    private String recommendReasonJson;
    private String riskWarningJson;
    private String modelFeatureContributionJson;
    private LocalDateTime createdAt;
}
