package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.DataYearReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
