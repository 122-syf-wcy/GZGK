package com.gzly.service;

import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 等效位次换算：E(r, y→t) = round(r ÷ N(y) × N(t))，任一年考生总数缺失时必须原样返回。
 */
class RankNormalizationServiceTest {

    private final ScoreRankGzMapper scoreRankGzMapper = mock(ScoreRankGzMapper.class);
    private final DataScoreRankMapper dataScoreRankMapper = mock(DataScoreRankMapper.class);
    private final RankNormalizationService service =
            new RankNormalizationService(scoreRankGzMapper, dataScoreRankMapper);

    @Test
    void normalizeScalesByPopulationRatio() {
        // 贵州 2024 年 20 万人、2025 年 22 万人：2024 年第 50000 名 ≈ 2025 年第 55000 名
        when(scoreRankGzMapper.selectObjs(any()))
                .thenReturn(List.of(200_000L))
                .thenReturn(List.of(220_000L));

        int normalized = service.normalize(50_000, 2024, 2025, "GZ", "物理类");

        assertThat(normalized).isEqualTo(55_000);
    }

    @Test
    void normalizeIsIdentityWhenPopulationMissing() {
        when(scoreRankGzMapper.selectObjs(any())).thenReturn(Collections.emptyList());

        assertThat(service.normalize(50_000, 2021, 2025, "GZ", "物理类")).isEqualTo(50_000);
        assertThat(service.canNormalize(2021, 2025, "GZ", "物理类")).isFalse();
    }

    @Test
    void normalizeIsIdentityForSameYear() {
        assertThat(service.normalize(12_345, 2025, 2025, "GZ", "历史类")).isEqualTo(12_345);
        assertThat(service.canNormalize(2025, 2025, "GZ", "历史类")).isTrue();
    }

    @Test
    void groupProvinceUsesMultiProvinceTable() {
        when(dataScoreRankMapper.selectObjs(any()))
                .thenReturn(List.of(400_000L))
                .thenReturn(List.of(380_000L));

        int normalized = service.normalize(40_000, 2025, 2026, "SC", "物理类");

        assertThat(normalized).isEqualTo(38_000);
    }
}
