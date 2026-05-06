package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("volunteer_ai_analysis")
public class VolunteerAiAnalysis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long userId;
    private String analysisStatus;
    private String conclusionText;
    private String gradientSummaryJson;
    private String keyKeepItemsJson;
    private String highRiskItemsJson;
    private String diagnosisText;
    private String actionStepsJson;
    private String sanitizedAnalysisText;
    private String sensitiveWordsJson;
    private String complianceStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
