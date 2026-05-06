package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("special_admission_policy")
public class SpecialAdmissionPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer year;
    private String category;
    private String categoryName;
    private String title;
    private String summary;
    private String contentMd;
    private String officialUrl;
    private String sourceName;
    private String sourceType;
    private LocalDate applyStart;
    private LocalDate applyEnd;
    private String examTime;
    private String targetStudents;
    private String requirements;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
