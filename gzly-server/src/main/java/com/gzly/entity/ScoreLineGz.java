package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_score_line_gz")
public class ScoreLineGz {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private String universityName;
    private String majorName;
    private String majorId;
    private Integer year;
    private String subjectType;
    private Integer minScore;
    private Integer maxScore;
    private Integer avgScore;
    private Integer minRank;
    private Integer planCount;
    private String batch;
    private String resubjectRequirement;
    private LocalDateTime createdAt;
}
