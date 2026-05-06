package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_card_key")
public class BizCardKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 卡密明文（32 位字母数字 + 分隔）。 */
    private String cardKey;
    private String batchNo;
    /** unused / active / revoked / expired */
    private String status;
    private Long userId;
    /** 安全码 BCrypt 哈希。 */
    private String secretCodeHash;
    private Integer maxPlans;
    private Integer usedPlans;
    private LocalDateTime expiresAt;
    private LocalDateTime activatedAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
