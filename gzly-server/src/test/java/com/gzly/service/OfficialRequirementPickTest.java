package com.gzly.service;

import com.gzly.entity.MajorRequirementGz;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存版选科要求匹配必须与 selectLatestByMajor 的 SQL 语义一致：
 * 谓词（精确/核心/括号前缀双向）+ 排序（年份降序 → 精确名 → 核心名 → 名称长度降序）。
 */
class OfficialRequirementPickTest {

    @Test
    void exactNameWinsOverCoreVariantInSameYear() {
        MajorRequirementGz exact = row("计算机科学与技术", 2025, "化学");
        MajorRequirementGz variant = row("计算机科学与技术（大数据方向）", 2025, "化学和生物");

        MajorRequirementGz picked = VolunteerService.pickOfficialRequirement(
                List.of(variant, exact), "计算机科学与技术", "计算机科学与技术");

        assertThat(picked).isSameAs(exact);
    }

    @Test
    void newerYearWinsRegardlessOfNamePriority() {
        MajorRequirementGz old = row("护理学", 2024, "化学或生物");
        MajorRequirementGz brandNew = row("护理学（涉外方向）", 2025, "化学");

        MajorRequirementGz picked = VolunteerService.pickOfficialRequirement(
                List.of(old, brandNew), "护理学", "护理学");

        assertThat(picked).isSameAs(brandNew);
    }

    @Test
    void bracketPrefixMatchesBothDirections() {
        // 行内是核心名 + 括号后缀，请求是核心名
        assertThat(VolunteerService.pickOfficialRequirement(
                List.of(row("软件工程（校企合作）", 2025, "不限")), "软件工程", "软件工程")).isNotNull();
        // 请求带括号后缀，行内是核心名
        assertThat(VolunteerService.pickOfficialRequirement(
                List.of(row("软件工程", 2025, "不限")), "软件工程（校企合作）", "软件工程")).isNotNull();
        // 无关专业不得命中
        assertThat(VolunteerService.pickOfficialRequirement(
                List.of(row("土木工程", 2025, "不限")), "软件工程", "软件工程")).isNull();
    }

    @Test
    void shortCoreDoesNotFuzzyMatch() {
        // 核心名不足 2 字时只允许精确匹配（对应 SQL 的 CHAR_LENGTH 守卫）
        assertThat(VolunteerService.pickOfficialRequirement(
                List.of(row("数（实验班）", 2025, "不限")), "数", "数")).isNull();
    }

    private MajorRequirementGz row(String majorName, int year, String requirement) {
        MajorRequirementGz row = new MajorRequirementGz();
        row.setMajorName(majorName);
        row.setYear(year);
        row.setResubjectRequirement(requirement);
        return row;
    }
}
