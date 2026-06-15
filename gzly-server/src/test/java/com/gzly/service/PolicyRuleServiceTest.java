package com.gzly.service;

import com.gzly.common.exception.BizException;
import com.gzly.mapper.PolicyRuleConfigMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyRuleServiceTest {

    @Test
    void requirePolicy_shouldRejectNonGuizhouProvinceUsingGuizhouBatch() {
        PolicyRuleService service = service();

        assertThatThrownBy(() -> service.requirePolicy("SC", 2025, "普通类", "NORMAL_UNDERGRADUATE"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前省份不支持该批次");
    }

    @Test
    void requirePolicy_shouldRejectCandidateTypeAndBatchMismatch() {
        PolicyRuleService service = service();

        assertThatThrownBy(() -> service.requirePolicy("GZ", 2025, "普通类", "ART_UNDERGRADUATE"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("考生类别与目标批次不匹配");
    }

    @Test
    void requirePolicy_shouldAllowGuizhouBatchWhenCandidateTypeMatches() {
        PolicyRuleService service = service();

        PolicyRuleService.PolicyContext context = service.requirePolicy("GZ", 2025, "艺术类", "ART_UNDERGRADUATE");

        assertThat(context.getConfig().getProvince()).isEqualTo("GZ");
        assertThat(context.getConfig().getCandidateType()).isEqualTo("艺术类");
        assertThat(context.getConfig().getBatchCode()).isEqualTo("ART_UNDERGRADUATE_B");
    }

    @Test
    void requirePolicy_nextProvinceShouldUseOwnQueryOnlyBatch() {
        PolicyRuleService service = service();

        PolicyRuleService.PolicyContext context = service.requirePolicy("GX", 2026, "普通类", null);

        assertThat(context.getConfig().getProvince()).isEqualTo("GX");
        assertThat(context.getConfig().getBatchCode()).isEqualTo("GX_BENKE");
        assertThat(context.getConfig().getFilingPrinciple()).isEqualTo("QUERY_ONLY");
        assertThat(service.toPublicPolicy(context.getConfig()))
                .containsEntry("supportLevel", "TRIAL_RECOMMEND")
                .containsEntry("recommendMode", "QUERY_ONLY")
                .containsEntry("engineName", "QueryOnlyRecommendEngine");
    }

    @Test
    void requirePolicy_nextProvinceShouldRejectSichuanAndGuizhouBatchFallbacks() {
        PolicyRuleService service = service();

        assertThatThrownBy(() -> service.requirePolicy("GX", 2026, "普通类", "SC_BENKE_B"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前省份不支持该批次");
        assertThatThrownBy(() -> service.requirePolicy("GX", 2026, "普通类", "NORMAL_UNDERGRADUATE"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前省份不支持该批次");
    }

    @Test
    void requirePolicy_hubeiShouldUseHbBatchCodesWithoutScFallback() {
        PolicyRuleService service = service();

        PolicyRuleService.PolicyContext context = service.requirePolicy("HB", 2026, "普通类", "本科普通批");

        assertThat(context.getConfig().getProvince()).isEqualTo("HB");
        assertThat(context.getConfig().getBatchCode()).isEqualTo("HB_BENKE");
        assertThat(context.getConfig().getBatchName()).isEqualTo("本科普通批");
        assertThat(context.getConfig().getFilingPrinciple()).isEqualTo("PARALLEL_GROUP");
        assertThat(service.toPublicPolicy(context.getConfig()))
                .containsEntry("supportLevel", "TRIAL_RECOMMEND")
                .containsEntry("recommendMode", "PARALLEL_GROUP")
                .containsEntry("engineName", "HubeiProfessionalGroup45Engine");

        assertThatThrownBy(() -> service.requirePolicy("HB", 2026, "普通类", "SC_BENKE_B"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前省份不支持该批次");
    }

    private PolicyRuleService service() {
        return new PolicyRuleService(Mockito.mock(PolicyRuleConfigMapper.class), new ProvincePolicyService(), new AdmissionYearService());
    }
}
