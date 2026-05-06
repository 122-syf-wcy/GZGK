package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_analysis_compliance_log")
public class AiAnalysisComplianceLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessType;
    private String businessId;
    private String rawText;
    private String sanitizedText;
    private String hitWordsJson;
    private String action;
    private LocalDateTime createdAt;
}
