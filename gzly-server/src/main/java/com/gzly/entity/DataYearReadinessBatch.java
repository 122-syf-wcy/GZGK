package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_year_readiness_batch")
public class DataYearReadinessBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provinceCode;
    private Integer year;
    private String dataType;
    private String importBatchId;
    private String status;
    private String sourceManifest;
    private String sourceFile;
    private String sourceUrl;
    private String fileHash;
    private String qualityReportPath;
    private String rollbackSqlPath;
    private Integer rowCount;
    private Integer failedGateCount;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
