package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_qa_evidence")
public class AiQaEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long messageId;
    private String title;
    private String url;
    private String sourceName;
    private String summary;
    private LocalDateTime createdAt;
}
