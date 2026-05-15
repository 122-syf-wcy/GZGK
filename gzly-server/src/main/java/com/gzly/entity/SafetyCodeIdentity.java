package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匿名安全码身份。安全码本身从不入库，仅存放确定性指纹，用于按当前安全码定位历史方案。
 */
@Data
@TableName("safety_code_identity")
public class SafetyCodeIdentity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String safetyCodeHash;
    private String safetyCodeVersion;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;
    private Integer planCount;
    private Integer enabled;
}
