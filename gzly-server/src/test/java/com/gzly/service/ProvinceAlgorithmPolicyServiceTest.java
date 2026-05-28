package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProvinceAlgorithmPolicyServiceTest {

    private final ProvinceAlgorithmPolicyService service = new ProvinceAlgorithmPolicyService();

    @Test
    void gzOrdinaryMainBatchIsMlEligible() {
        ProvinceAlgorithmPolicyService.AlgorithmPolicy policy = service.resolve("GZ", config(
                "GZ", "普通类", "NORMAL_UNDERGRADUATE", "普通类本科批", "专业类平行志愿"));

        assertThat(policy.isGeneratorReady()).isTrue();
        assertThat(policy.isMlEligible()).isTrue();
        assertThat(policy.getGenerationEngine()).isEqualTo("gz_major_parallel_rank_v1");
        assertThat(policy.getModelRoute()).isEqualTo("gz_historical_rank_chance_v2");
    }

    @Test
    void hubeiOrdinaryMainBatchDoesNotUseGlobalMl() {
        ProvinceAlgorithmPolicyService.AlgorithmPolicy policy = service.resolve("HB", config(
                "HB", "普通类", "HB_BENKE", "本科普通批", "院校专业组（平行志愿）"));

        assertThat(policy.isGeneratorReady()).isTrue();
        assertThat(policy.isMlEligible()).isFalse();
        assertThat(policy.getGenerationEngine()).isEqualTo("hb_group_parallel_rank_v1");
        assertThat(policy.getModelRouteStatus()).isEqualTo("BASELINE_ONLY_NOT_ACTIVATED");
    }

    @Test
    void artSportsSkillAndSequentialBatchesStayQueryOnly() {
        assertThat(service.resolve("SC", config("SC", "艺术类", "SC_ART_BENKE", "艺术类本科批", "院校专业组（综合分平行志愿）"))
                .isGeneratorReady()).isFalse();
        assertThat(service.resolve("AH", config("AH", "体育类", "AH_SPORTS_BENKE", "体育类本科批", "院校专业组（综合分平行志愿）"))
                .getModelRouteStatus()).isEqualTo("QUERY_GATE_ONLY");
        assertThat(service.resolve("HB", config("HB", "技能高考", "HB_SKILL_BENKE", "技能高考本科", "院校专业组平行志愿"))
                .getGenerationEngine()).isEqualTo("hb_skill_query_gate_v1");
        assertThat(service.resolve("GZ", config("GZ", "普通类", "EARLY_A_B", "普通类本科提前批A/B段", "院校顺序志愿"))
                .getRecommendMode()).isEqualTo("SEQUENTIAL_QUERY");
    }

    private PolicyRuleConfig config(String province, String candidateType, String batchCode, String batchName, String volunteerMode) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(province);
        config.setCandidateType(candidateType);
        config.setBatchCode(batchCode);
        config.setBatchName(batchName);
        config.setVolunteerMode(volunteerMode);
        return config;
    }
}
