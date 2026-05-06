package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_user_feedback")
public class BizUserFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String content;
    private String sourcePage;
    private Integer status;
    private String ipHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime readAt;
}
