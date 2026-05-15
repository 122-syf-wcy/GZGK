package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_import_job")
public class AdminImportJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provinceCode;
    private Integer year;
    private String batchCode;
    private String subjectType;
    private String importType;
    private String sourceType;
    private String status;
    private String sourceDir;
    private String outputDir;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
