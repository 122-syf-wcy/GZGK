package com.gzly.service;

import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.ScoreRankGz;
import com.gzly.entity.ScoreLineGz;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import com.gzly.mapper.ScoreLineGzMapper;
import com.gzly.mapper.UniversityMapper;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreLineServiceHistoryTest {

    @Test
    void findRecentHistory_prefersMajorLevelRecords() {
        ScoreLineGzMapper scoreLineMapper = mock(ScoreLineGzMapper.class);
        MajorScoreGzMapper majorMapper = mock(MajorScoreGzMapper.class);
        ScoreRankGzMapper scoreRankMapper = mock(ScoreRankGzMapper.class);
        UniversityMapper universityMapper = mock(UniversityMapper.class);
        DataAdmissionGroupLineMapper groupLineMapper = mock(DataAdmissionGroupLineMapper.class);
        ScoreLineService service = new ScoreLineService(scoreLineMapper, majorMapper, scoreRankMapper, universityMapper, groupLineMapper, new ProvincePolicyService());
        when(majorMapper.selectList(any())).thenReturn(List.of(
                major("100", "计算机类（智能）", 2025, 620, 3600),
                major("100", "计算机类（智能）", 2024, 618, 3900),
                major("100", "计算机类（智能）", 2023, 615, 4100),
                major("100", "计算机类（智能）", 2022, 612, 4300)));

        List<ScoreLineService.ScoreLineView> records = service.findRecentHistory(
                "100", "计算机类（智能）", "物理类", 3);

        assertThat(records).hasSize(3);
        assertThat(records).extracting(ScoreLineService.ScoreLineView::getYear)
                .containsExactly(2025, 2024, 2023);
        assertThat(records).allMatch(record -> "专业级".equals(record.getDataSourceType()));
        verify(scoreLineMapper, never()).selectList(any());
    }

    @Test
    void findRecentHistory_fallsBackToSchoolLevelWhenMajorMissing() {
        ScoreLineGzMapper scoreLineMapper = mock(ScoreLineGzMapper.class);
        MajorScoreGzMapper majorMapper = mock(MajorScoreGzMapper.class);
        ScoreRankGzMapper scoreRankMapper = mock(ScoreRankGzMapper.class);
        UniversityMapper universityMapper = mock(UniversityMapper.class);
        DataAdmissionGroupLineMapper groupLineMapper = mock(DataAdmissionGroupLineMapper.class);
        ScoreLineService service = new ScoreLineService(scoreLineMapper, majorMapper, scoreRankMapper, universityMapper, groupLineMapper, new ProvincePolicyService());
        when(majorMapper.selectList(any())).thenReturn(List.of());
        when(scoreLineMapper.selectList(any())).thenReturn(List.of(
                score("200", 2025, 580, 12000),
                score("200", 2024, 575, 13000)));

        List<ScoreLineService.ScoreLineView> records = service.findRecentHistory(
                "200", "普通类", "物理类", 3);

        assertThat(records).hasSize(2);
        assertThat(records).allMatch(record -> "院校级".equals(record.getDataSourceType()));
        assertThat(records).extracting(ScoreLineService.ScoreLineView::getYear)
                .containsExactly(2025, 2024);
    }

    @Test
    void findRecentHistory_marksRankConvertedWhenOriginalRankMissing() {
        ScoreLineGzMapper scoreLineMapper = mock(ScoreLineGzMapper.class);
        MajorScoreGzMapper majorMapper = mock(MajorScoreGzMapper.class);
        ScoreRankGzMapper scoreRankMapper = mock(ScoreRankGzMapper.class);
        UniversityMapper universityMapper = mock(UniversityMapper.class);
        DataAdmissionGroupLineMapper groupLineMapper = mock(DataAdmissionGroupLineMapper.class);
        ScoreLineService service = new ScoreLineService(scoreLineMapper, majorMapper, scoreRankMapper, universityMapper, groupLineMapper, new ProvincePolicyService());
        when(majorMapper.selectList(any())).thenReturn(List.of());
        when(scoreLineMapper.selectList(any())).thenReturn(List.of(score("200", 2024, 575, 0)));
        when(scoreRankMapper.selectNearestAtOrBelow(2024, "物理类", 575)).thenReturn(scoreRank(2024, 575, 12888, 13120));

        List<ScoreLineService.ScoreLineView> records = service.findRecentHistory(
                "200", "普通类", "物理类", 3);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getMinRank()).isEqualTo(13120);
        assertThat(records.get(0).getRankSourceType()).isEqualTo("score_rank_converted");
        assertThat(records.get(0).getRankSourceNote()).contains("官方一分一段表");
    }

    private MajorScoreGz major(String schoolId, String majorName, int year, int score, int rank) {
        MajorScoreGz item = new MajorScoreGz();
        item.setSchoolId(schoolId);
        item.setUniversityName("测试大学");
        item.setMajorName(majorName);
        item.setYear(year);
        item.setSubjectType("物理类");
        item.setBatch("本科批");
        item.setMinScore(score);
        item.setMinRank(rank);
        item.setPlanCount(8);
        return item;
    }

    private ScoreLineGz score(String schoolId, int year, int score, int rank) {
        ScoreLineGz item = new ScoreLineGz();
        item.setSchoolId(schoolId);
        item.setUniversityName("测试大学");
        item.setMajorName("普通类");
        item.setYear(year);
        item.setSubjectType("物理类");
        item.setBatch("本科批");
        item.setMinScore(score);
        item.setMinRank(rank);
        return item;
    }

    private ScoreRankGz scoreRank(int year, int score, int rankLow, int rankHigh) {
        ScoreRankGz item = new ScoreRankGz();
        item.setYear(year);
        item.setSubjectType("物理类");
        item.setScore(score);
        item.setScoreLabel(String.valueOf(score));
        item.setRankLow(rankLow);
        item.setRankHigh(rankHigh);
        item.setSourceName("贵州省招生考试院");
        item.setSourceUrl("https://example.com/score-rank.pdf");
        item.setSourcePageUrl("https://example.com/source-page");
        item.setParseMethod("pdf_table");
        return item;
    }
}
