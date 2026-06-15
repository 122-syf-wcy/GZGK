package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_call_log")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scene;
    private Integer success;
    private Integer httpStatus;
    private String errorCode;
    private String model;
    private Integer latencyMs;
    private String message;
    private LocalDateTime createdAt;
}
