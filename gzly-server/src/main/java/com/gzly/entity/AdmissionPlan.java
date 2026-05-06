package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admission_plan")
public class AdmissionPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer year;
    private String province;
    private String batchCode;
    private String candidateType;
    private String subjectType;
    private String selectedSubjectRequirement;
    private String schoolCode;
    private String schoolName;
    private String majorCode;
    private String majorName;
    private String majorCategory;
    private Integer planCount;
    private String tuition;
    private String duration;
    private String campus;
    private String remarks;
    private String specialLimit;
    private Integer isPublic;
    private Integer isPrivate;
    private Integer isChineseForeignCoop;
    private String schoolLevel;
    private String schoolProvince;
    private String schoolCity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
