package com.gzly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VolunteerResultHistoricalScoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void itemCarriesThreeYearHistoricalScoreAndRankRecords() {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setProvinceCode("GZ");
        item.setHistoryRecords(List.of(
                history(2025, 545, 18888, 12),
                history(2024, 538, 20123, 10),
                history(2023, 526, 22456, 9)));

        assertThat(item.getHistoryRecords()).extracting(VolunteerService.HistoryRecord::getYear)
                .containsExactly(2025, 2024, 2023);
        assertThat(item.getHistoryRecords()).extracting(VolunteerService.HistoryRecord::getMinScore)
                .containsExactly(545, 538, 526);
        assertThat(item.getHistoryRecords()).extracting(VolunteerService.HistoryRecord::getMinRank)
                .containsExactly(18888, 20123, 22456);
    }

    @Test
    void historicalRecordsSerializeForProfessionalTableYearColumns() {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setHistoryRecords(List.of(history(2025, 545, 18888, 12)));

        JsonNode node = objectMapper.valueToTree(item).get("historyRecords").get(0);

        assertThat(node.get("year").asInt()).isEqualTo(2025);
        assertThat(node.get("minScore").asInt()).isEqualTo(545);
        assertThat(node.get("minRank").asInt()).isEqualTo(18888);
        assertThat(node.get("planCount").asInt()).isEqualTo(12);
    }

    private VolunteerService.HistoryRecord history(int year, int score, int rank, int planCount) {
        VolunteerService.HistoryRecord record = new VolunteerService.HistoryRecord();
        record.setProvinceCode("GZ");
        record.setYear(year);
        record.setMinScore(score);
        record.setMinRank(rank);
        record.setPlanCount(planCount);
        record.setDataSourceType("专业级");
        return record;
    }
}
