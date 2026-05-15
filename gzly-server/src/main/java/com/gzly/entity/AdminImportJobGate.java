package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_import_job_gate")
public class AdminImportJobGate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private String gateName;
    private String gateStatus;
    private String expectedValue;
    private String actualValue;
    private String samplePath;
    private LocalDateTime createdAt;
}
