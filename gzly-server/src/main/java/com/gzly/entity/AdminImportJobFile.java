package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_import_job_file")
public class AdminImportJobFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private String fileName;
    private String filePath;
    private String sha256;
    private Long fileSize;
    private String fileType;
    private String sourceUrl;
    private LocalDateTime createdAt;
}
