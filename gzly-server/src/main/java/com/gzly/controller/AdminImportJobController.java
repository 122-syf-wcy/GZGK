package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AdminImportJobService;
import com.gzly.service.AdminImportJobService.CreateJobRequest;
import com.gzly.service.AdminImportJobService.JobDetail;
import com.gzly.service.AdminImportJobService.JobListResponse;
import com.gzly.service.AdminImportJobService.QualityCheckResult;
import com.gzly.service.AdminImportJobService.RollbackPlanResult;
import com.gzly.service.AdminImportJobService.SqlPackageResult;
import com.gzly.service.AdminImportJobService.StagingResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/import-jobs")
@RequiredArgsConstructor
public class AdminImportJobController {

    private final AdminImportJobService adminImportJobService;

    @PostMapping
    public Result<JobDetail> createJob(@RequestBody(required = false) CreateJobRequest request,
                                       HttpServletRequest httpRequest) {
        Object identifier = httpRequest == null ? null : httpRequest.getAttribute("authIdentifier");
        return Result.ok(adminImportJobService.createJob(request, identifier == null ? "admin" : String.valueOf(identifier)));
    }

    @GetMapping
    public Result<JobListResponse> listJobs(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String provinceCode,
                                            @RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) String status) {
        return Result.ok(adminImportJobService.listJobs(page, size, provinceCode, year, status));
    }

    @GetMapping("/{jobId}")
    public Result<JobDetail> getJob(@PathVariable Long jobId) {
        return Result.ok(adminImportJobService.detail(jobId));
    }

    @PostMapping("/{jobId}/staging")
    public Result<StagingResult> generateStaging(@PathVariable Long jobId) {
        return Result.ok(adminImportJobService.generateStagingDryRun(jobId));
    }

    @PostMapping("/{jobId}/quality-check")
    public Result<QualityCheckResult> runQualityCheck(@PathVariable Long jobId) {
        return Result.ok(adminImportJobService.runQualityCheck(jobId));
    }

    @PostMapping("/{jobId}/generate-formal-sql")
    public Result<SqlPackageResult> generateFormalSql(@PathVariable Long jobId) {
        return Result.ok(adminImportJobService.generateFormalSql(jobId));
    }

    @PostMapping("/{jobId}/rollback-plan")
    public Result<RollbackPlanResult> generateRollbackPlan(@PathVariable Long jobId) {
        return Result.ok(adminImportJobService.generateRollbackPlan(jobId));
    }
}
