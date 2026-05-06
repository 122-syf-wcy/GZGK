package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("score_rank_segment")
public class ScoreRankSegment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer year;
    private String province;
    private String candidateType;
    private String subjectType;
    private Integer score;
    private Integer sameScoreCount;
    private Integer cumulativeCount;
    private Integer rankMin;
    private Integer rankMax;
}
