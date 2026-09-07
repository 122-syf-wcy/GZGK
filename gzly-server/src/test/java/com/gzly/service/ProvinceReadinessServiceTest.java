package com.gzly.service;

import com.gzly.common.exception.BizException;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 阶段 0：数据就绪度门禁按真实行数判定四态。
 * 对应场景：湖北/安徽零数据 → LOCKED；四川 27 组 < 45 → QUERY_ONLY；数据齐 → ESTIMATE。
 */
class ProvinceReadinessServiceTest {

    private final ScoreRankGzMapper scoreRankGzMapper = mock(ScoreRankGzMapper.class);
    private final MajorScoreGzMapper majorScoreGzMapper = mock(MajorScoreGzMapper.class);
    private final DataScoreRankMapper dataScoreRankMapper = mock(DataScoreRankMapper.class);
    private final DataAdmissionGroupLineMapper groupLineMapper = mock(DataAdmissionGroupLineMapper.class);

    private ProvinceReadinessService service() {
        return new ProvinceReadinessService(new ProvincePolicyService(),
                scoreRankGzMapper, majorScoreGzMapper, dataScoreRankMapper, groupLineMapper);
    }

    @Test
    void groupProvinceWithoutScoreRankIsLocked() {
        when(dataScoreRankMapper.selectCount(any())).thenReturn(0L);

        ProvinceReadinessService.ProvinceReadiness readiness = service().getReadiness("HB");

        assertThat(readiness.getLevel()).isEqualTo(ProvinceReadinessService.LEVEL_LOCKED);
        assertThat(readiness.getSubjects()).allMatch(s ->
                ProvinceReadinessService.LEVEL_LOCKED.equals(s.getLevel()));
        assertThat(readiness.getSubjects().get(0).getMissingData()).contains("official_score_rank");
    }

    @Test
    void groupProvinceWithInsufficientGroupsIsQueryOnly() {
        when(dataScoreRankMapper.selectCount(any())).thenReturn(541L);
        when(groupLineMapper.selectLatestYear(eq("SC"), anyString())).thenReturn(2025);
        when(groupLineMapper.countDistinctGroups(eq("SC"), anyInt(), anyString(), anyString())).thenReturn(18L);

        ProvinceReadinessService.ProvinceReadiness readiness = service().getReadiness("SC");

        assertThat(readiness.getLevel()).isEqualTo(ProvinceReadinessService.LEVEL_QUERY_ONLY);
        assertThat(readiness.getSubjects().get(0).getReason()).contains("18").contains("45");
    }

    @Test
    void groupProvinceWithEnoughGroupsIsEstimate() {
        when(dataScoreRankMapper.selectCount(any())).thenReturn(1000L);
        when(groupLineMapper.selectLatestYear(eq("HB"), anyString())).thenReturn(2025);
        when(groupLineMapper.countDistinctGroups(eq("HB"), anyInt(), anyString(), anyString())).thenReturn(120L);

        ProvinceReadinessService.ProvinceReadiness readiness = service().getReadiness("HB");

        assertThat(readiness.getLevel()).isEqualTo(ProvinceReadinessService.LEVEL_ESTIMATE);
    }

    @Test
    void guizhouWithHistoricalDataIsEstimate() {
        when(scoreRankGzMapper.selectCount(any())).thenReturn(2435L);
        when(majorScoreGzMapper.selectCount(any())).thenReturn(80000L);

        ProvinceReadinessService.ProvinceReadiness readiness = service().getReadiness("GZ");

        assertThat(readiness.getLevel()).isEqualTo(ProvinceReadinessService.LEVEL_ESTIMATE);
    }

    @Test
    void requireGenerationReadyThrowsWithGapReason() {
        when(dataScoreRankMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().requireGenerationReady("AH", "物理类"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("安徽")
                .hasMessageContaining("一分一段");
    }

    @Test
    void requireGenerationReadyPassesForReadySubject() {
        when(scoreRankGzMapper.selectCount(any())).thenReturn(2435L);
        when(majorScoreGzMapper.selectCount(any())).thenReturn(80000L);

        service().requireGenerationReady("GZ", "物理类");
    }
}
