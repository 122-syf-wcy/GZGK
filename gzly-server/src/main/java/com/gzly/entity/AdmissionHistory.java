package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admission_history")
public class AdmissionHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer year;
    private String province;
    private String batchCode;
    private String candidateType;
    private String subjectType;
    private String schoolCode;
    private String schoolName;
    private String majorCode;
    private String majorName;
    private Integer minScore;
    private Integer minRank;
    private Integer avgScore;
    private Integer avgRank;
    private Integer maxScore;
    private Integer maxRank;
    private Integer planCount;
    private Integer admittedCount;
    private Integer firstRoundFull;
    private Integer hasSupplement;
    private LocalDateTime createdAt;
}
