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
    private Integer maxVolunteerCount;
    private Integer majorPerSchoolCount;
    private Integer hasAdjustment;
    private String filingPrinciple;
    private String admissionOrder;
    private String policyStatus;
    private String officialSourceTitle;
    private String officialSourceUrl;
    private String officialSourceText;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
