package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_user")
public class BizUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String identifier;
    /** 关联 biz_card_key.id（激活后绑定）。 */
    private Long cardKeyId;
    /** 昵称，激活时由用户填写，可空字符串。 */
    private String nickname;
    private Integer remainCount;
    private Integer totalUsed;
    private LocalDateTime lastActiveTime;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
