package com.gzly.service;

import com.gzly.mapper.DataScoreRankMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProvinceRankServiceMultiProvinceTest {

    @Test
    void estimateRank_usesGenericSichuanTableInsteadOfGuizhouFallback() {
        ProvincePolicyService provincePolicyService = new ProvincePolicyService();
        AlgorithmService algorithmService = mock(AlgorithmService.class);
        DataScoreRankMapper dataScoreRankMapper = mock(DataScoreRankMapper.class);
        ProvinceRankService service = new ProvinceRankService(provincePolicyService, algorithmService, dataScoreRankMapper);
        when(dataScoreRankMapper.selectLatestYear("SC", "物理类")).thenReturn(null);

        AlgorithmService.RankEstimate result = service.estimateRank("SC", 500, "物理类", null);

        assertThat(result.getConfidence()).isEqualTo("数据不足");
        assertThat(result.getNote()).contains("四川");
        verify(dataScoreRankMapper).selectLatestYear("SC", "物理类");
        verifyNoInteractions(algorithmService);
    }
}
