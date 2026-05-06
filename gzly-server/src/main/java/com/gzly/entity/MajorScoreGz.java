package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_major_score_gz")
public class MajorScoreGz {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private String universityName;
    private String majorName;
    private String majorId;
    private Integer year;
    private String subjectType;
    private String batch;
    private String resubjectRequirement;
    private Integer minScore;
    private Integer maxScore;
    private Integer avgScore;
    private Integer minRank;
    private Integer planCount;
    private LocalDateTime createdAt;
}
