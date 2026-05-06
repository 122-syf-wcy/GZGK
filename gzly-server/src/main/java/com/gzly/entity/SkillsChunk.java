package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skills_chunk")
public class SkillsChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String chunkText;
    private String embeddingVector;
    private String tagsJson;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
