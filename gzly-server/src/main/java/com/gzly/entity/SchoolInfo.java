package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("school_info")
public class SchoolInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolCode;
    private String schoolName;
    private String province;
    private String city;
    private String schoolType;
    private String schoolLevel;
    private Integer is985;
    private Integer is211;
    private Integer isDoubleFirstClass;
    private Integer isPublic;
    private BigDecimal rankingScore;
    private BigDecimal employmentScore;
    private BigDecimal postgraduateScore;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
