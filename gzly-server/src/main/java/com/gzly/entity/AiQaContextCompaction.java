package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_qa_context_compaction")
public class AiQaContextCompaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long fromMessageId;
    private Long toMessageId;
    private Integer compactedCount;
    private String summary;
    private String preservedFacts;
    private LocalDateTime createdAt;
}
