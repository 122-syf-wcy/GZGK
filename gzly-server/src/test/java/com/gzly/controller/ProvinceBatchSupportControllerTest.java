package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.mapper.PolicyRuleConfigMapper;
import com.gzly.service.DataReadinessService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvinceAlgorithmPolicyService;
import com.gzly.service.ProvincePolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvinceBatchSupportControllerTest {

    @Mock private PolicyRuleService policyRuleService;
    @Mock private PolicyRuleConfigMapper policyRuleConfigMapper;
    @Mock private DataReadinessService dataReadinessService;
    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void preOfficialDataMainBatchesReturnEstimateRecommendAndKeepFullClosed() {
        ProvincePolicyService provincePolicyService = new ProvincePolicyService();
        ProvinceBatchSupportController controller = new ProvinceBatchSupportController(
                provincePolicyService,
                new ProvinceAlgorithmPolicyService(),
                policyRuleService,
                policyRuleConfigMapper,
                dataReadinessService,
                jdbcTemplate);

        when(dataReadinessService.get("HB", 2026)).thenReturn(preOfficialReadiness("HB"));
        when(dataReadinessService.isFullRecommendReady("HB", 2026)).thenReturn(false);
        when(dataReadinessService.toMap(any())).thenReturn(Map.of("recommendationPhase", DataReadinessService.PRE_OFFICIAL_DATA));
        when(policyRuleConfigMapper.selectList(any())).thenReturn(List.of(
                policy("HB", "普通类", "HB_BENKE", "本科普通批", "院校专业组（平行志愿）", 45),
                policy("HB", "艺术类", "HB_ART_XIAOKAO_BENKE", "艺术本科校考批", "院校专业组单一志愿", 1)
        ));
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(0L);

        Result<Map<String, Object>> result = controller.batchSupport("hb", 2026);

        assertThat(result.getCode()).isEqualTo(0);
        Map<String, Object> body = result.getData();
        assertThat(body.get("recommendationPhase")).isEqualTo(DataReadinessService.PRE_OFFICIAL_DATA);
        assertThat(body.get("dataSourceYears")).isEqualTo(List.of(2024, 2025));
        assertThat(body.get("phaseGates")).isInstanceOf(List.class);
        Map<String, Object> summary = (Map<String, Object>) body.get("summary");
        assertThat(summary.get("FULL_RECOMMEND")).isEqualTo(0L);
        assertThat(summary.get("ESTIMATE_RECOMMEND")).isEqualTo(1L);
        assertThat(summary.get("QUERY_ONLY")).isEqualTo(1L);
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("batchCode")).isEqualTo("HB_BENKE");
            assertThat(item.get("supportLevel")).isEqualTo("ESTIMATE_RECOMMEND");
            assertThat(item.get("generatorReady")).isEqualTo(true);
            assertThat((List<String>) item.get("missingData")).contains("official_2026_admission_plan", "official_2026_score_or_rank", "HB_A00306_manual_rank_review");
        });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("batchCode")).isEqualTo("HB_ART_XIAOKAO_BENKE");
            assertThat(item.get("supportLevel")).isEqualTo("QUERY_ONLY");
            assertThat(item.get("generatorReady")).isEqualTo(false);
        });
    }

    private DataReadinessService.Readiness preOfficialReadiness(String provinceCode) {
        DataReadinessService.Readiness readiness = new DataReadinessService.Readiness();
        readiness.provinceCode = provinceCode;
        readiness.year = 2026;
        readiness.recommendationPhase = DataReadinessService.PRE_OFFICIAL_DATA;
        readiness.historicalTrainingReady = true;
        return readiness;
    }

    private PolicyRuleConfig policy(String province, String candidateType, String batchCode,
                                    String batchName, String volunteerMode, int maxCount) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(province);
        config.setYear(2026);
        config.setCandidateType(candidateType);
        config.setBatchCode(batchCode);
        config.setBatchName(batchName);
        config.setVolunteerMode(volunteerMode);
        config.setMaxVolunteerCount(maxCount);
        config.setMajorPerSchoolCount(6);
        config.setHasAdjustment(1);
        config.setPolicyStatus("pending_confirm");
        config.setEnabled(1);
        return config;
    }
}
