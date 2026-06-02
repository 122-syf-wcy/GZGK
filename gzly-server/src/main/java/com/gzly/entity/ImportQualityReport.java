package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("official_import_quality_report")
public class ImportQualityReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobId;
    private String importBatchId;
    private String dataType;
    private String reportPath;
    private String gateStatus;
    private String summary;
    private Integer totalRows;
    private Integer cleanRows;
    private Integer reviewRows;
    private Integer errorRows;
    private LocalDateTime createdAt;
}
