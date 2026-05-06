package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ml_model_registry")
public class MlModelRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelName;
    private String modelType;
    private String modelVersion;
    private String trainYearRange;
    private Integer trainDataCount;
    private String featureSchemaJson;
    private String metricsJson;
    private String modelFilePath;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
}
