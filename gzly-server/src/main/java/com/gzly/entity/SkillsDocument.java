package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skills_document")
public class SkillsDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceId;
    private String docKey;
    private String title;
    private String filePath;
    private String contentHash;
    private String rawContent;
    private String sanitizedContent;
    private String tagsJson;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
