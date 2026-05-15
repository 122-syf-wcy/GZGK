package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("official_import_job")
public class ImportJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobId;
    private String provinceCode;
    private Integer year;
    private String dataType;
    private String importBatchId;
    private String sourceFile;
    private String sourceUrl;
    private String sourceManifest;
    private String rawText;
    private String status;
    private String currentStep;
    private Integer totalRows;
    private Integer cleanRows;
    private Integer reviewRows;
    private Integer errorRows;
    private String qualityReportPath;
    private String formalSqlPath;
    private String rollbackSqlPath;
    private String message;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
