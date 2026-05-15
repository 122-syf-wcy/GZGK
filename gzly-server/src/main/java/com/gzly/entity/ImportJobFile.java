package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("official_import_job_file")
public class ImportJobFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobId;
    private String importBatchId;
    private String sourceFile;
    private String sourceUrl;
    private String sourceManifest;
    private String fileHash;
    private String rawText;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
