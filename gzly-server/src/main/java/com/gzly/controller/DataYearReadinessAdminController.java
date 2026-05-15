package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.DataYearReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/data-year-readiness")
@RequiredArgsConstructor
public class DataYearReadinessAdminController {

    private final DataYearReadinessService dataYearReadinessService;

    @GetMapping
    public Result<DataYearReadinessService.DataYearReadinessDto> getReadiness(@RequestParam(defaultValue = "GZ") String provinceCode,
                                                                               @RequestParam(defaultValue = "2026") Integer year) {
        int resolvedYear = year == null || year <= 0 ? 2026 : year;
        return Result.ok(dataYearReadinessService.buildDataReadinessDto(provinceCode, resolvedYear));
    }

    /**
     * 按实际表行数自动校准 readiness flags (仅 GZ).
     * 避免 admin 看到的 readiness 长期与真实数据脱节。
     * 不修改 recommendation_phase, 不强制 ml_training_ready。
     */
    @PostMapping("/refresh")
    public Result<DataYearReadinessService.RefreshResult> refresh(@RequestParam(defaultValue = "GZ") String provinceCode,
                                                                  @RequestParam(defaultValue = "2026") Integer year) {
        int resolvedYear = year == null || year <= 0 ? 2026 : year;
        return Result.ok(dataYearReadinessService.refreshReadinessFlags(provinceCode, resolvedYear));
    }
}
