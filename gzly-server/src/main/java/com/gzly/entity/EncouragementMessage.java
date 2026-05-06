package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_encouragement_message")
public class EncouragementMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nickname;
    private String content;
    private Integer status;
    private String ipHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
