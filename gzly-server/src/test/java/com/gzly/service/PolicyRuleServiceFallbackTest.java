package com.gzly.service;

import com.gzly.common.exception.BizException;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.mapper.PolicyRuleConfigMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 政策年动态解析：默认年取当前自然年；未显式传年且当年政策缺席时
 * 回退最近可用年份并携带口径警示；显式传年查不到仍视为配置缺失。
 */
class PolicyRuleServiceFallbackTest {

    private final PolicyRuleConfigMapper mapper = mock(PolicyRuleConfigMapper.class);
    private final PolicyRuleService service = new PolicyRuleService(mapper, new ProvincePolicyService());

    private PolicyRuleConfig config(int year) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince("GZ");
        config.setYear(year);
        config.setCandidateType("普通类");
        config.setBatchCode("NORMAL_UNDERGRADUATE");
        config.setMaxVolunteerCount(96);
        config.setPolicyStatus("confirmed");
        return config;
    }

    @Test
    void defaultYearIsCurrentCalendarYear() {
        assertThat(service.currentAdmissionYear()).isEqualTo(java.time.LocalDate.now().getYear());
    }

    @Test
    void fallsBackToLatestAvailableYearWithWarningWhenYearOmitted() {
        int lastYear = service.currentAdmissionYear() - 1;
        // 第一次精确年查询 miss，第二次最近年查询命中去年的政策
        when(mapper.selectOne(any())).thenReturn(null, config(lastYear));

        PolicyRuleService.PolicyContext context = service.requirePolicy("GZ", null, null, null);

        assertThat(context.getConfig().getYear()).isEqualTo(lastYear);
        assertThat(context.getWarning()).contains(String.valueOf(lastYear)).contains("政策口径");
    }

    @Test
    void explicitYearMissDoesNotFallBack() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requirePolicy("GZ", 2030, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("政策未配置");
    }

    @Test
    void exactHitCarriesNoFallbackWarning() {
        when(mapper.selectOne(any())).thenReturn(config(service.currentAdmissionYear()));

        PolicyRuleService.PolicyContext context = service.requirePolicy("GZ", null, null, null);

        assertThat(context.getConfig().getYear()).isEqualTo(service.currentAdmissionYear());
        assertThat(context.getWarning()).isNull();
    }
}
