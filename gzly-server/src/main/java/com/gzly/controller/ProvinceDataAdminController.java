package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.SichuanDataAdminService;
import com.gzly.service.SichuanDataAdminService.GroupLineImportRequest;
import com.gzly.service.SichuanDataAdminService.GroupPlanImportRequest;
import com.gzly.service.SichuanDataAdminService.ImportResult;
import com.gzly.service.SichuanDataAdminService.ScoreRankImportRequest;
import com.gzly.service.SichuanDataAdminService.SourceRefreshRequest;
import com.gzly.service.SichuanDataAdminService.SourceRefreshResult;
import com.gzly.service.SichuanDataAdminService.StatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/province-data/{provinceCode}")
@RequiredArgsConstructor
public class ProvinceDataAdminController {

    private final SichuanDataAdminService provinceDataAdminService;

    @GetMapping("/status")
    public Result<StatusResponse> status(@PathVariable String provinceCode,
                                         @RequestParam(required = false) Integer year) {
        return Result.ok(provinceDataAdminService.status(provinceCode, year));
    }

    @PostMapping("/sources/refresh")
    public Result<SourceRefreshResult> refreshSources(@PathVariable String provinceCode,
                                                      @RequestBody(required = false) SourceRefreshRequest request) {
        return Result.ok(provinceDataAdminService.refreshSources(provinceCode, request));
    }

    @PostMapping("/score-rank/import")
    public Result<ImportResult> importScoreRank(@PathVariable String provinceCode,
                                                @RequestParam(defaultValue = "true") boolean dryRun,
                                                @RequestBody ScoreRankImportRequest request) {
        return Result.ok(provinceDataAdminService.importScoreRanks(provinceCode, request, dryRun));
    }

    @PostMapping("/group-lines/import")
    public Result<ImportResult> importGroupLines(@PathVariable String provinceCode,
                                                 @RequestParam(defaultValue = "true") boolean dryRun,
                                                 @RequestBody GroupLineImportRequest request) {
        return Result.ok(provinceDataAdminService.importGroupLines(provinceCode, request, dryRun));
    }

    @PostMapping("/group-plans/import")
    public Result<ImportResult> importGroupPlans(@PathVariable String provinceCode,
                                                 @RequestParam(defaultValue = "true") boolean dryRun,
                                                 @RequestBody GroupPlanImportRequest request) {
        return Result.ok(provinceDataAdminService.importGroupPlans(provinceCode, request, dryRun));
    }
}
