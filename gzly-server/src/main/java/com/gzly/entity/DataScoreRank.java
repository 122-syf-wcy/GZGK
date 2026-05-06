package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("data_score_rank")
public class DataScoreRank {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provinceCode;
    private String provinceName;
    private Integer year;
    private String subjectType;
    private Integer score;
    private String scoreLabel;
    private Integer segmentCount;
    private Integer cumulativeCount;
    private BigDecimal cumulativeRate;
    private Integer rankLow;
    private Integer rankHigh;
    private String sourceName;
    private String sourceUrl;
    private String sourcePageUrl;
    private String sourceFile;
    private String parseMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
