package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("compliance_sensitive_word")
public class ComplianceSensitiveWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private String wordType;
    private String severity;
    private String replacement;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
