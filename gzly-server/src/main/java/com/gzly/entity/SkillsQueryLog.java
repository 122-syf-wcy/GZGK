package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skills_query_log")
public class SkillsQueryLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long userId;
    private String question;
    private String retrievedChunksJson;
    private String rawAnswer;
    private String sanitizedAnswer;
    private String complianceStatus;
    private LocalDateTime createdAt;
}
