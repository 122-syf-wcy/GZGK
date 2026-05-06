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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sichuan-data")
@RequiredArgsConstructor
public class SichuanDataAdminController {

    private final SichuanDataAdminService sichuanDataAdminService;

    @GetMapping("/status")
    public Result<StatusResponse> status(@RequestParam(required = false) Integer year) {
        return Result.ok(sichuanDataAdminService.status(year));
    }

    @PostMapping("/sources/refresh")
    public Result<SourceRefreshResult> refreshSources(@RequestBody(required = false) SourceRefreshRequest request) {
        return Result.ok(sichuanDataAdminService.refreshSources(request));
    }

    @PostMapping("/score-rank/import")
    public Result<ImportResult> importScoreRank(@RequestParam(defaultValue = "true") boolean dryRun,
                                                @RequestBody ScoreRankImportRequest request) {
        return Result.ok(sichuanDataAdminService.importScoreRanks(request, dryRun));
    }

    @PostMapping("/group-lines/import")
    public Result<ImportResult> importGroupLines(@RequestParam(defaultValue = "true") boolean dryRun,
                                                 @RequestBody GroupLineImportRequest request) {
        return Result.ok(sichuanDataAdminService.importGroupLines(request, dryRun));
    }

    @PostMapping("/group-plans/import")
    public Result<ImportResult> importGroupPlans(@RequestParam(defaultValue = "true") boolean dryRun,
                                                 @RequestBody GroupPlanImportRequest request) {
        return Result.ok(sichuanDataAdminService.importGroupPlans(request, dryRun));
    }
}
