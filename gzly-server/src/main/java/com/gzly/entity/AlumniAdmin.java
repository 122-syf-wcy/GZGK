package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_alumni_admin")
public class AlumniAdmin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private String nickname;
    private String phone;
    private String email;
    private String avatarUrl;
    private String credentialUrl;
    private Short graduationYear;
    private String major;
    private String bio;
    /** 0=pending 1=school_admin 9=super_admin */
    private Integer role;
    /** 0=pending 1=approved 2=rejected 3=disabled */
    private Integer status;
    private String rejectReason;
    @JsonIgnore
    private String passwordHash;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
