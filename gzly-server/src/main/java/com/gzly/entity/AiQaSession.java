package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_qa_session")
public class AiQaSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionUid;
    private String regionCode;
    private String regionName;
    private Integer examYear;
    private Integer score;
    private Integer provinceRank;
    private String subjects;
    private String batch;
    private String majorPreference;
    private String regionPreference;
    private String codeHash;
    private String codeFingerprint;
    private String contextSummary;
    private String memoryFacts;
    private Integer messageCount;
    private Integer compactionCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;
}
