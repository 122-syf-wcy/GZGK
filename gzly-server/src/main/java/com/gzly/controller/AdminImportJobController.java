package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AdminImportJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/import-jobs")
@RequiredArgsConstructor
public class AdminImportJobController {

    private final AdminImportJobService service;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String provinceCode,
                                                  @RequestParam(required = false) Integer year,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.list(provinceCode, year, status, limit));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody(required = false) AdminImportJobService.CreateRequest request) {
        return Result.ok(service.create(request));
    }

    @GetMapping("/{jobId}")
    public Result<Map<String, Object>> detail(@PathVariable long jobId) {
        return Result.ok(service.detail(jobId));
    }

    @PostMapping("/{jobId}/files")
    public Result<Map<String, Object>> registerFile(@PathVariable long jobId,
                                                    @RequestBody AdminImportJobService.FileRequest request) {
        return Result.ok(service.registerFile(jobId, request));
    }

    @PostMapping("/{jobId}/staging-dry-run")
    public Result<Map<String, Object>> stagingDryRun(@PathVariable long jobId) {
        return Result.ok(service.stagingDryRun(jobId));
    }

    @PostMapping("/{jobId}/quality-gate")
    public Result<Map<String, Object>> qualityGate(@PathVariable long jobId) {
        return Result.ok(service.qualityGate(jobId));
    }

    @PostMapping("/{jobId}/generate-formal-sql")
    public Result<Map<String, Object>> generateFormalSql(@PathVariable long jobId) {
        return Result.ok(service.generateFormalSql(jobId));
    }

    @PostMapping("/{jobId}/rollback-plan")
    public Result<Map<String, Object>> rollbackPlan(@PathVariable long jobId) {
        return Result.ok(service.rollbackPlan(jobId));
    }

    @PostMapping("/{jobId}/post-check")
    public Result<Map<String, Object>> postCheck(@PathVariable long jobId) {
        return Result.ok(service.postCheck(jobId));
    }
}
