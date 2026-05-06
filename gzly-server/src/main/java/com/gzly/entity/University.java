package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "sys_university", autoResultMap = true)
public class University {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolId;
    private String name;
    private String province;
    private String city;
    private String level;
    private String typeName;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Integer f985;
    private Integer f211;
    private Integer dualClass;
    private String natureName;
    private String belong;
    private String logoUrl;
    private String schoolSite;
    private String phone;
    private String email;
    private String address;
    private String content;
    private Integer qaDisabled;
    private String qaDisabledReason;
    private LocalDateTime qaDisabledUntil;
    private LocalDateTime createdAt;
}
