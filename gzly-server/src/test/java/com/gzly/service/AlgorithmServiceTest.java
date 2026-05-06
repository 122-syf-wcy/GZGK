package com.gzly.service;

import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.ScoreRankGz;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import com.gzly.mapper.ScoreLineGzMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmServiceTest {

    @Mock
    private ScoreLineGzMapper scoreLineGzMapper;

    @Mock
    private MajorScoreGzMapper majorScoreGzMapper;

    @Mock
    private ScoreRankGzMapper scoreRankGzMapper;

    @InjectMocks
    private AlgorithmService algorithmService;

    @Test
    void calcProbability_singleYearSample_returnsReferenceLevelAndCappedProbability() {
        when(majorScoreGzMapper.selectList(any())).thenReturn(List.of(
                majorLine(2025, 500, 50_000)
        ));

        AlgorithmService.AdmissionProbability result = algorithmService.calcProbability(
                45_000, "1001", "计算机科学与技术", "物理类");

        assertThat(result.getLevel()).isEqualTo("单年参考");
        assertThat(result.getProbability()).isBetween(20.0, 80.0);
        assertThat(result.getHistoryRanks()).containsExactly(50_000);
    }

    @Test
    void calcProbability_noValidHistory_returnsDataInsufficient() {
        when(majorScoreGzMapper.selectList(any())).thenReturn(List.of());
        when(scoreLineGzMapper.selectList(any())).thenReturn(List.of());

        AlgorithmService.AdmissionProbability result = algorithmService.calcProbability(
                45_000, "1001", "计算机科学与技术", "物理类");

        assertThat(result.getLevel()).isEqualTo("数据不足");
        assertThat(result.getProbability()).isZero();
        assertThat(result.getHistoryRanks()).isEmpty();
    }

    @Test
    void calcProbability_multiYearSample_keepsHistoricalProbabilityLevel() {
        when(majorScoreGzMapper.selectList(any())).thenReturn(List.of(
                majorLine(2025, 500, 50_000),
                majorLine(2024, 498, 52_000),
                majorLine(2023, 495, 54_000)
        ));

        AlgorithmService.AdmissionProbability result = algorithmService.calcProbability(
                51_000, "1001", "计算机科学与技术", "物理类");

        assertThat(result.getLevel()).isNotEqualTo("单年参考");
        assertThat(result.getProbability()).isBetween(1.0, 99.0);
        assertThat(result.getHistoryRanks()).hasSize(3);
    }

    @Test
    void estimateRank_officialScoreRank_returnsOfficialInterval() {
        when(scoreRankGzMapper.selectLatestYear("物理类")).thenReturn(2025);
        when(scoreRankGzMapper.selectNearestAtOrBelow(2025, "物理类", 500)).thenReturn(
                scoreRank(2025, "物理类", 500, "500", 738, 52_633));

        AlgorithmService.RankEstimate result = algorithmService.estimateRank(500, "物理类");

        assertThat(result.getConfidence()).isEqualTo("官方");
        assertThat(result.getReferenceYear()).isEqualTo(2025);
        assertThat(result.getRankLow()).isEqualTo(51_896);
        assertThat(result.getRankHigh()).isEqualTo(52_633);
        assertThat(result.getEstimatedRank()).isEqualTo(52_633);
        assertThat(result.getDataPoints()).isEqualTo(1);
    }

    @Test
    void estimateRank_missingOfficialTable_returnsDataInsufficient() {
        when(scoreRankGzMapper.selectLatestYear("历史类")).thenReturn(null);

        AlgorithmService.RankEstimate result = algorithmService.estimateRank(500, "历史类");

        assertThat(result.getConfidence()).isEqualTo("数据不足");
        assertThat(result.getEstimatedRank()).isZero();
        assertThat(result.getDataPoints()).isZero();
        assertThat(result.getNote()).contains("未导入官方一分一段表");
    }

    private MajorScoreGz majorLine(int year, int minScore, int minRank) {
        MajorScoreGz line = new MajorScoreGz();
        line.setSchoolId("1001");
        line.setUniversityName("测试大学");
        line.setMajorName("计算机科学与技术");
        line.setSubjectType("物理类");
        line.setYear(year);
        line.setMinScore(minScore);
        line.setMinRank(minRank);
        line.setBatch("本科批");
        return line;
    }

    private ScoreRankGz scoreRank(int year, String subjectType, int score, String scoreLabel,
                                  int segmentCount, int cumulativeCount) {
        ScoreRankGz line = new ScoreRankGz();
        line.setYear(year);
        line.setProvince("贵州");
        line.setSubjectType(subjectType);
        line.setScore(score);
        line.setScoreLabel(scoreLabel);
        line.setSegmentCount(segmentCount);
        line.setCumulativeCount(cumulativeCount);
        line.setRankLow(cumulativeCount - segmentCount + 1);
        line.setRankHigh(cumulativeCount);
        line.setSourceName("贵州省招生考试院");
        line.setSourceUrl("https://zsksy.guizhou.gov.cn/");
        return line;
    }
}
