package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.DataReviewWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only review workbench for CQ/GS/XJ review-only data.
 *
 * <p>Protected by global /admin/** AuthInterceptor. This controller does not
 * write business DB, does not import, and does not create processed files.</p>
 */
@RestController
@RequestMapping("/admin/data-review/cq-gs-xj")
@RequiredArgsConstructor
public class AdminDataReviewController {

    private final DataReviewWorkbenchService service;

    @GetMapping
    public Result<DataReviewWorkbenchService.ReviewListResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String school,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean sourceUrlEmpty,
            @RequestParam(required = false) Boolean planCountAnomaly) {
        DataReviewWorkbenchService.ReviewQuery query = new DataReviewWorkbenchService.ReviewQuery();
        query.setPage(page);
        query.setSize(size);
        query.setProvinceCode(provinceCode);
        query.setSchool(school);
        query.setStatus(status);
        query.setSourceUrlEmpty(sourceUrlEmpty);
        query.setPlanCountAnomaly(planCountAnomaly);
        return Result.ok(service.list(query));
    }

    @PostMapping("/decision")
    public Result<DataReviewWorkbenchService.ReviewRow> saveDecision(
            @RequestBody DataReviewWorkbenchService.ReviewDecisionRequest request) {
        return Result.ok(service.saveDecision(request));
    }

    @PostMapping("/export")
    public Result<DataReviewWorkbenchService.ExportResponse> exportReviewedFiles() {
        return Result.ok(service.exportReviewedFiles());
    }
}
