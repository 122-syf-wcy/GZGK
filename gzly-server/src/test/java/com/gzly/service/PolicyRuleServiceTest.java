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

    private PolicyRuleService service() {
        return new PolicyRuleService(Mockito.mock(PolicyRuleConfigMapper.class), new ProvincePolicyService(), new AdmissionYearService());
    }
}
