package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("uni_official_link")
public class UniOfficialLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private String schoolName;
    private String sourceDomain;
    private String schoolSite;
    private String admissionSite;
    private String admissionBrochureUrl;
    private String majorCatalogUrl;
    private String tuitionInfoUrl;
    private String tuitionRemark;
    private String tuitionSummary;
    private String majorCatalogSummary;
    private String adjustmentRule;
    private String foreignLanguageRule;
    private String physicalExamRule;
    private String singleSubjectRule;
    private String parserNotes;
    private String captureMethod;
    private Integer captureStatus;
    private Integer parseStatus;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime lastCapturedAt;
    private LocalDateTime lastParsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
