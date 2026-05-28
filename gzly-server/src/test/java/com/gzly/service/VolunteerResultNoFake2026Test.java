package com.gzly.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VolunteerResultNoFake2026Test {

    @Test
    void targetYearDoesNotTurnHistoricalSourceYearIntoFake2026() {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setProvinceCode("GZ");
        item.setReferenceYear(2025);
        item.setSourceYear("2025");
        item.setHistoryRecords(List.of(history(2025), history(2024), history(2023)));

        assertThat(item.getSourceYear()).isNotEqualTo("2026");
        assertThat(item.getReferenceYear()).isLessThan(2026);
        assertThat(item.getHistoryRecords())
                .extracting(VolunteerService.HistoryRecord::getYear)
                .allMatch(year -> year != null && year < 2026);
    }

    @Test
    void queryOnlyGapPlanMustNotInventVolunteerItems() {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode("HA");
        plan.setTargetCount(0);
        plan.setItems(List.of());
        plan.setDataQualityWarning("河南官方普通高考源待补齐，暂不生成院校志愿清单。");
        plan.setWarnings(List.of("PRE_OFFICIAL_DATA 阶段仅生成策略建议，不生成假院校清单。"));

        assertThat(plan.getProvinceCode()).isEqualTo("HA");
        assertThat(plan.getItems()).isEmpty();
        assertThat(plan.getDataQualityWarning()).contains("暂不生成院校志愿清单");
        assertThat(plan.getWarnings()).anyMatch(warning -> warning.contains("不生成假院校清单"));
    }

    private VolunteerService.HistoryRecord history(int year) {
        VolunteerService.HistoryRecord record = new VolunteerService.HistoryRecord();
        record.setYear(year);
        record.setMinScore(520);
        record.setMinRank(30000);
        return record;
    }
}
