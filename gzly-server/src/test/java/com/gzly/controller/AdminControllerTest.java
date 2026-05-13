package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.BatchSupportService;
import com.gzly.service.DataYearReadinessService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AdminControllerTest {

    @Test
    void adminReadiness_shouldReturnFullDto() {
        AtomicReference<String> capturedProvince = new AtomicReference<>();
        AtomicReference<Integer> capturedYear = new AtomicReference<>();
        DataYearReadinessService.DataYearReadinessDto dto = buildExpectedDto();
        DataYearReadinessService service = new DataYearReadinessService(null, null) {
            @Override
            public DataYearReadinessService.DataYearReadinessDto buildDataReadinessDto(String provinceCode, int year) {
                capturedProvince.set(provinceCode);
                capturedYear.set(year);
                return dto;
            }
        };

        DataYearReadinessAdminController controller = new DataYearReadinessAdminController(service);

        Result<DataYearReadinessService.DataYearReadinessDto> result = controller.getReadiness("GZ", 2026);

        assertThat(capturedProvince.get()).isEqualTo("GZ");
        assertThat(capturedYear.get()).isEqualTo(2026);
        assertThat(result.getCode()).isEqualTo(0);
        DataYearReadinessService.DataYearReadinessDto data = result.getData();
        assertThat(data).isSameAs(dto);
        assertThat(data.getProvinceCode()).isEqualTo("GZ");
        assertThat(data.getYear()).isEqualTo(2026);
        assertThat(data.getActiveAdmissionYear()).isEqualTo(2026);
        assertThat(data.getLatestOfficialDataYear()).isEqualTo(2025);
        assertThat(data.getTrainingYears()).containsExactly(2024, 2025);
        assertThat(data.getDataSourceYears()).containsExactly(2024, 2025);
        assertThat(data.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(data.isOfficialDataReady()).isFalse();
        assertThat(data.isEstimateMode()).isTrue();
        assertThat(data.getDataReadiness().isHistoricalTrainingReady()).isTrue();
        assertThat(data.getPhaseDescription()).isNotBlank();
        assertThat(data.getNextActions()).contains("导入 2026 一分一段表", "完成模型重训后再开放正式推荐");
    }

    @Test
    void adminReadiness_shouldDefaultYearWhenMissingOrInvalid() {
        AtomicReference<Integer> capturedYear = new AtomicReference<>();
        DataYearReadinessService service = new DataYearReadinessService(null, null) {
            @Override
            public DataYearReadinessService.DataYearReadinessDto buildDataReadinessDto(String provinceCode, int year) {
                capturedYear.set(year);
                return buildExpectedDto();
            }
        };
        DataYearReadinessAdminController controller = new DataYearReadinessAdminController(service);

        controller.getReadiness("GZ", null);
        assertThat(capturedYear.get()).isEqualTo(2026);

        controller.getReadiness("GZ", 0);
        assertThat(capturedYear.get()).isEqualTo(2026);

        controller.getReadiness("GZ", -1);
        assertThat(capturedYear.get()).isEqualTo(2026);
    }

    private static DataYearReadinessService.DataYearReadinessDto buildExpectedDto() {
        DataYearReadinessService.DataYearReadinessDto dto = new DataYearReadinessService.DataYearReadinessDto();
        BatchSupportService.DataReadiness readiness = new BatchSupportService.DataReadiness();
        readiness.setProvinceCode("GZ");
        readiness.setYear(2026);
        readiness.setHistoricalTrainingReady(true);
        readiness.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        dto.setProvinceCode("GZ");
        dto.setYear(2026);
        dto.setActiveAdmissionYear(2026);
        dto.setLatestOfficialDataYear(2025);
        dto.setTrainingYears(List.of(2024, 2025));
        dto.setDataSourceYears(List.of(2024, 2025));
        dto.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        dto.setOfficialDataReady(false);
        dto.setEstimateMode(true);
        dto.setDataReadiness(readiness);
        dto.setPhaseDescription("2026 官方数据未发布或未完成导入，当前仅提供历史趋势和预估参考。");
        dto.setNextActions(List.of("导入 2026 一分一段表", "导入 2026 招生计划", "导入 2026 选科要求", "完成模型重训后再开放正式推荐"));
        return dto;
    }
}
