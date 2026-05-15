package com.gzly.controller;

import com.gzly.common.PageResult;
import com.gzly.common.Result;
import com.gzly.service.OfficialImportJobService;
import com.gzly.service.OfficialImportJobService.CreateImportJobRequest;
import com.gzly.service.OfficialImportJobService.DataYearReadinessView;
import com.gzly.service.OfficialImportJobService.ImportJobDetail;
import com.gzly.service.OfficialImportJobService.ImportJobView;
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
@RequestMapping("/admin")
@RequiredArgsConstructor
public class OfficialImportJobAdminController {

    private final OfficialImportJobService officialImportJobService;

    @PostMapping("/import-jobs")
    public Result<ImportJobView> create(@RequestBody CreateImportJobRequest request,
                                        HttpServletRequest httpRequest) {
        Object actor = httpRequest.getAttribute("authIdentifier");
        return Result.ok(officialImportJobService.createJob(request, actor == null ? "admin" : actor.toString()));
    }

    @GetMapping("/import-jobs")
    public Result<PageResult<ImportJobView>> list(@RequestParam(required = false) String provinceCode,
                                                  @RequestParam(required = false) Integer year,
                                                  @RequestParam(required = false) String dataType,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(officialImportJobService.listJobs(provinceCode, year, dataType, status, page, pageSize));
    }

    @GetMapping("/import-jobs/{jobId}")
    public Result<ImportJobDetail> detail(@PathVariable String jobId) {
        return Result.ok(officialImportJobService.detail(jobId));
    }

    @PostMapping("/import-jobs/{jobId}/staging")
    public Result<ImportJobView> staging(@PathVariable String jobId) {
        return Result.ok(officialImportJobService.runStaging(jobId));
    }

    @PostMapping("/import-jobs/{jobId}/quality-check")
    public Result<ImportJobView> qualityCheck(@PathVariable String jobId) {
        return Result.ok(officialImportJobService.runQualityCheck(jobId));
    }

    @PostMapping("/import-jobs/{jobId}/generate-formal-sql")
    public Result<ImportJobView> generateFormalSql(@PathVariable String jobId) {
        return Result.ok(officialImportJobService.generateFormalSql(jobId));
    }

    @PostMapping("/import-jobs/{jobId}/rollback-plan")
    public Result<ImportJobView> rollbackPlan(@PathVariable String jobId) {
        return Result.ok(officialImportJobService.generateRollbackPlan(jobId));
    }

    @GetMapping("/data-year-readiness")
    public Result<DataYearReadinessView> readiness(@RequestParam(defaultValue = "GZ") String provinceCode,
                                                   @RequestParam(defaultValue = "2026") Integer year) {
        return Result.ok(officialImportJobService.readiness(provinceCode, year));
    }
}
