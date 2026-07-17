package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 贵州批次控制线（省控线）：本科线 / 特殊类型招生控制线 / 高职专科线 */
@Data
@TableName("data_batch_line_gz")
public class BatchLineGz {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer year;
    private String candidateType;
    private String subjectType;
    private String batchCode;
    private String batchName;
    private Integer controlScore;
    private String sourceName;
    private String sourceUrl;
    private String sourcePageUrl;
    private String sourceFile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
