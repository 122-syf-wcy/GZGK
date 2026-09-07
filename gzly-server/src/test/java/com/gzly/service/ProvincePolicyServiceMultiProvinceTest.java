package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阶段 0：省份口径扩展到 8 省后的政策事实校验。
 * 志愿数量为 2026 年官方核查值（docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 第二部分）。
 */
class ProvincePolicyServiceMultiProvinceTest {

    private final ProvincePolicyService service = new ProvincePolicyService();

    @Test
    void supportsAllEightProvinces() {
        assertThat(service.listPolicies()).hasSize(8);
        assertThat(service.normalizeProvinceCode("gx")).isEqualTo("GX");
        assertThat(service.normalizeProvinceCode("广西")).isEqualTo("GX");
        assertThat(service.normalizeProvinceCode("海南省")).isEqualTo("HI");
        assertThat(service.normalizeProvinceCode("云南")).isEqualTo("YN");
        assertThat(service.normalizeProvinceCode("河南")).isEqualTo("HA");
        assertThat(service.normalizeProvinceCode("")).isEqualTo("GZ");
    }

    @Test
    void unknownProvinceStillRejected() {
        assertThatThrownBy(() -> service.normalizeProvinceCode("XJ"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void volunteerCountsFollowOfficial2026Policy() {
        assertThat(service.getPolicy("GZ").getTargetCount()).isEqualTo(96);
        assertThat(service.getPolicy("SC").getTargetCount()).isEqualTo(45);
        assertThat(service.getPolicy("HB").getTargetCount()).isEqualTo(45);
        assertThat(service.getPolicy("AH").getTargetCount()).isEqualTo(45);
        assertThat(service.getPolicy("GX").getTargetCount()).isEqualTo(40);
        assertThat(service.getPolicy("HI").getTargetCount()).isEqualTo(30);
        assertThat(service.getPolicy("YN").getTargetCount()).isEqualTo(40);
        assertThat(service.getPolicy("HA").getTargetCount()).isEqualTo(48);
    }

    @Test
    void subjectModeAndScoreSystem() {
        assertThat(service.getPolicy("HI").getSubjectMode()).isEqualTo(ProvincePolicyService.SUBJECT_MODE_33);
        assertThat(service.getPolicy("HI").getScoreSystem()).isEqualTo(ProvincePolicyService.SCORE_SYSTEM_STANDARD_900);
        assertThat(service.isThreeThreeProvince("HI")).isTrue();
        assertThat(service.getPolicy("HA").getSubjectMode()).isEqualTo(ProvincePolicyService.SUBJECT_MODE_312);
        assertThat(service.isThreeThreeProvince("GZ")).isFalse();
    }

    @Test
    void newGaokaoFirstYearPerProvince() {
        assertThat(service.getPolicy("GZ").getNewGaokaoFirstYear()).isEqualTo(2024);
        assertThat(service.getPolicy("HB").getNewGaokaoFirstYear()).isEqualTo(2021);
        assertThat(service.getPolicy("HI").getNewGaokaoFirstYear()).isEqualTo(2020);
        assertThat(service.getPolicy("SC").getNewGaokaoFirstYear()).isEqualTo(2025);
        assertThat(service.getPolicy("YN").getNewGaokaoFirstYear()).isEqualTo(2025);
        assertThat(service.getPolicy("HA").getNewGaokaoFirstYear()).isEqualTo(2025);
    }

    @Test
    void majorPerGroupCountPerProvince() {
        assertThat(service.getPolicy("GX").getMajorPerGroupCount()).isEqualTo(20);
        assertThat(service.getPolicy("YN").getMajorPerGroupCount()).isEqualTo(10);
        assertThat(service.getPolicy("SC").getMajorPerGroupCount()).isEqualTo(6);
        assertThat(service.getPolicy("GZ").getMajorPerGroupCount()).isEqualTo(0);
    }

    @Test
    void professionalGroupRouting() {
        assertThat(service.isProfessionalGroupProvince("GZ")).isFalse();
        for (String code : new String[]{"SC", "HB", "AH", "GX", "HI", "YN", "HA"}) {
            assertThat(service.isProfessionalGroupProvince(code)).as(code).isTrue();
        }
    }
}
