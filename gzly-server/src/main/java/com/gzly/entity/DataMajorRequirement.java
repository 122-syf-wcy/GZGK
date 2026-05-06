package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_major_requirement")
public class DataMajorRequirement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provinceCode;
    private String provinceName;
    private Integer year;
    private String schoolId;
    private String universityName;
    private String groupCode;
    private String majorCode;
    private String majorName;
    private String subjectType;
    private String firstSubjectRequirement;
    private String resubjectRequirement;
    private String requirementText;
    private String sourceName;
    private String sourceUrl;
    private String sourceFile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
