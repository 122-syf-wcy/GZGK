package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("major_planner_result")
public class MajorPlannerResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planNo;
    private String planCodeHash;
    private String planCodeFingerprint;
    private String planCodeMasked;
    private String provinceCode;
    private String subjectCategory;
    private Integer score;
    private Integer provinceRank;
    private String answersJson;
    private String resultJson;
    private String aiSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
