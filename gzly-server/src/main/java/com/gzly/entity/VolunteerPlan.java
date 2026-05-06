package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("volunteer_plan")
public class VolunteerPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer year;
    private String province;
    private String candidateType;
    private String subjectType;
    private String selectedSubjectsJson;
    private Integer score;
    @TableField("`rank`")
    private Integer rank;
    private String batchCode;
    private String riskPreference;
    private Long policyRuleId;
    private String mlModelVersion;
    private Integer totalCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
