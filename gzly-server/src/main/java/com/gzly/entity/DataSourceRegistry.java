package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_source_registry")
public class DataSourceRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provinceCode;
    private String provinceName;
    private String dataType;
    private Integer year;
    private String subjectType;
    private String batch;
    private String sourceName;
    private String sourceUrl;
    private String sourcePageUrl;
    private String sourceLevel;
    private String sourceHash;
    private String parseMethod;
    private String status;
    private Integer rowCount;
    private String notes;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
