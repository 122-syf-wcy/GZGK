package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("uni_content_edit")
public class UniContentEdit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private Long editorId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    /** 0=待AI审核 1=已采纳 2=已拒绝 3=待人工审核 */
    private Integer status;
    private String aiReviewResult;
    private String reviewNote;
    private String reviewActorRole;
    private Long reviewActorId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
