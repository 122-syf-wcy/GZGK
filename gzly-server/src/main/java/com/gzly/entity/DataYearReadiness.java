package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_year_readiness")
public class DataYearReadiness {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provinceCode;
    private Integer year;
    private Integer policyReady;
    private Integer scoreSegmentReady;
    private Integer admissionPlanReady;
    private Integer majorRequirementReady;
    private Integer majorMetaReady;
    private Integer mlTrainingReady;
    private Integer historicalTrainingReady;
    private String recommendationPhase;
    private String latestImportBatchId;
    private String remarks;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
