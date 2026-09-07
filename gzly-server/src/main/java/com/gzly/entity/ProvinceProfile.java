package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 省份画像：省级、不随批次变化的属性。
 * 批次级属性（志愿数、志愿单位、组内专业数等）在 {@link PolicyRuleConfig}。
 */
@Data
@TableName("province_profile")
public class ProvinceProfile {
    @TableId
    private String provinceCode;
    private String provinceName;
    /** 3+1+2 / 3+3 */
    private String subjectMode;
    /** 新高考首年；之前年份的文理科数据不可与新高考位次直接比较 */
    private Integer newGaokaoFirstYear;
    /** RAW_750 / STANDARD_900（海南） */
    private String scoreSystem;
    private String rankTieBreakRule;
    private String officialSourceName;
    private String officialSourceUrl;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
