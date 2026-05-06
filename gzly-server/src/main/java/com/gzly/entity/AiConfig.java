package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_config")
public class AiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String providerName;
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String reviewModel;
    private String visionModel;
    private Integer maxTokens;
    private Double temperature;
    private String systemPrompt;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
