package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Getter
@Setter
@TableName("biz_university_qa")
public class UniversityQa {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private Long parentId;
    private String content;
    private String authorName;
    private String authorType;
    private Integer status;
    private String aiReviewResult;
    private String reviewNote;
    private String reviewActorRole;
    private Long reviewActorId;
    private LocalDateTime reviewedAt;
    private Integer likeCount;
    private String ipHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
