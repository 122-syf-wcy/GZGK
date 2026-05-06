package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("major_info")
public class MajorInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String majorCode;
    private String majorName;
    private String majorCategory;
    private String discipline;
    private BigDecimal employmentScore;
    private BigDecimal salaryScore;
    private BigDecimal postgraduateScore;
    private BigDecimal civilServiceScore;
    private BigDecimal hotScore;
    private BigDecimal riskScore;
    private String suitableSubjects;
    private String limitationTags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
