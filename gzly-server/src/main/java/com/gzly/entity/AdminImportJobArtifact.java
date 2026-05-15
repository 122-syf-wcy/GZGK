package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_import_job_artifact")
public class AdminImportJobArtifact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private String artifactType;
    private String artifactPath;
    private String sha256;
    private LocalDateTime createdAt;
}
