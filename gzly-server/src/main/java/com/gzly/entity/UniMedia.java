package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("uni_media")
public class UniMedia {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private Long uploaderId;
    /** 1=photo 2=news 3=file 4=banner */
    private Integer mediaType;
    private String url;
    private String thumbUrl;
    private String caption;
    private Integer sortOrder;
    /** 0=待AI审核 1=已发布 2=已拒绝 3=待人工审核 */
    private Integer status;
    private String aiReviewResult;
    private String reviewNote;
    private String reviewActorRole;
    private Long reviewActorId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
