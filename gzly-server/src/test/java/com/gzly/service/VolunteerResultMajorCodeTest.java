package com.gzly.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VolunteerResultMajorCodeTest {

    @Test
    void gzItemKeepsOfficialMajorCodeNullWhenOnlySourceMajorIdExists() {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setProvinceCode("GZ");
        item.setUniversityName("贵州示例大学");
        item.setSchoolCode("10657");
        item.setMajorName("计算机类");
        item.setSourceMajorId("SRC-MAJOR-2025-001");
        item.setMajorCode(null);
        item.setMissingReason("缺官方专业代码；缺学制；缺学费");

        assertThat(item.getSourceMajorId()).isEqualTo("SRC-MAJOR-2025-001");
        assertThat(item.getMajorCode()).isNull();
        assertThat(item.getMajorName()).isEqualTo("计算机类");
        assertThat(item.getMajorName()).isNotEqualTo(item.getMajorCode());
        assertThat(item.getMissingReason()).contains("缺官方专业代码");
    }

    @Test
    void historyRecordSeparatesSourceMajorIdFromOfficialMajorCode() {
        VolunteerService.HistoryRecord history = new VolunteerService.HistoryRecord();
        history.setYear(2025);
        history.setMajorCode(null);
        history.setSourceMajorId("HIST-MAJOR-ID-88");
        history.setMinScore(530);
        history.setMinRank(21000);

        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setHistoryRecords(List.of(history));

        assertThat(item.getHistoryRecords()).hasSize(1);
        assertThat(item.getHistoryRecords().get(0).getMajorCode()).isNull();
        assertThat(item.getHistoryRecords().get(0).getSourceMajorId()).isEqualTo("HIST-MAJOR-ID-88");
    }
}
