package com.gzly.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProvincePolicyServiceTest {

    private final ProvincePolicyService service = new ProvincePolicyService();

    @Test
    void normalizeProvinceCode_shouldSupportNextProvinceNamesAndCodes() {
        assertThat(service.normalizeProvinceCode("广西")).isEqualTo(ProvincePolicyService.GX);
        assertThat(service.normalizeProvinceCode("广西壮族自治区")).isEqualTo(ProvincePolicyService.GX);
        assertThat(service.normalizeProvinceCode("海南省")).isEqualTo(ProvincePolicyService.HI);
        assertThat(service.normalizeProvinceCode("云南")).isEqualTo(ProvincePolicyService.YN);
        assertThat(service.normalizeProvinceCode("河南省")).isEqualTo(ProvincePolicyService.HA);
        assertThat(service.normalizeProvinceCode("重庆市")).isEqualTo(ProvincePolicyService.CQ);
        assertThat(service.normalizeProvinceCode("甘肃")).isEqualTo(ProvincePolicyService.GS);
        assertThat(service.normalizeProvinceCode("新疆维吾尔自治区")).isEqualTo(ProvincePolicyService.XJ);
        assertThat(service.normalizeProvinceCode(" gx ")).isEqualTo(ProvincePolicyService.GX);
        assertThat(service.normalizeProvinceCode("hi")).isEqualTo(ProvincePolicyService.HI);
        assertThat(service.normalizeProvinceCode("yn")).isEqualTo(ProvincePolicyService.YN);
        assertThat(service.normalizeProvinceCode("ha")).isEqualTo(ProvincePolicyService.HA);
        assertThat(service.normalizeProvinceCode("cq")).isEqualTo(ProvincePolicyService.CQ);
        assertThat(service.normalizeProvinceCode("gs")).isEqualTo(ProvincePolicyService.GS);
        assertThat(service.normalizeProvinceCode("xj")).isEqualTo(ProvincePolicyService.XJ);
    }

    @Test
    void listPolicies_shouldIncludeNextProvincesAsQueryOnlyWithoutChangingExistingCodes() {
        assertThat(service.listPolicies())
                .extracting(ProvincePolicyService.ProvincePolicy::getProvinceCode)
                .containsExactly("GZ", "SC", "HB", "AH", "GX", "HI", "YN", "HA", "CQ", "GS", "XJ");

        ProvincePolicyService.ProvincePolicy hubei = service.getPolicy("HB");
        assertThat(hubei.getVolunteerUnitType()).isEqualTo(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        assertThat(service.isNextProvinceQueryOnly("HB")).isFalse();
        assertThat(service.isProfessionalGroupProvince("HB")).isTrue();

        for (String province : new String[]{"GX", "HI", "YN", "HA", "CQ", "GS", "XJ"}) {
            ProvincePolicyService.ProvincePolicy policy = service.getPolicy(province);
            assertThat(policy.getVolunteerUnitType()).isEqualTo(ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY);
            assertThat(policy.getVolunteerUnitLabel()).isEqualTo(ProvincePolicyService.UNIT_NEXT_PROVINCE_QUERY_ONLY_LABEL);
            assertThat(service.isNextProvinceQueryOnly(province)).isTrue();
            assertThat(service.isProfessionalGroupProvince(province)).isFalse();
        }
    }
}
