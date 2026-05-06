package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skills_source")
public class SkillsSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceName;
    private String sourceType;
    private String sourceUrl;
    private String localPath;
    private String branch;
    private Integer enabled;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
