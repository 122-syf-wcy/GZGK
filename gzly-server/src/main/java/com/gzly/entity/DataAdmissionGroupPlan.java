package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_admission_group_plan")
public class DataAdmissionGroupPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provinceCode;
    private String provinceName;
    private Integer year;
    private String schoolId;
    private String universityName;
    private String groupCode;
    private String groupName;
    private String majorCode;
    private String majorName;
    private String subjectType;
    private String firstSubjectRequirement;
    private String resubjectRequirement;
    private Integer planCount;
    private String tuition;
    private String studyYears;
    private String batch;
    private String sourceName;
    private String sourceUrl;
    private String sourcePageUrl;
    private String sourceLevel;
    private String parseMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
