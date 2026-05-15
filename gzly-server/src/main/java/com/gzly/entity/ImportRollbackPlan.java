package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("official_import_rollback_plan")
public class ImportRollbackPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobId;
    private String importBatchId;
    private String rollbackSqlPath;
    private String summary;
    private Integer executable;
    private LocalDateTime createdAt;
}
