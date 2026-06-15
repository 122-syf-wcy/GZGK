package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.MajorPlannerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/major-planner")
@RequiredArgsConstructor
public class MajorPlannerController {

    private final MajorPlannerService majorPlannerService;

    @PostMapping("/evaluate")
    public Result<MajorPlannerService.MajorPlannerView> evaluate(
            @RequestBody MajorPlannerService.EvaluateRequest req) {
        return Result.ok(majorPlannerService.evaluate(req));
    }

    @PostMapping("/restore")
    public Result<MajorPlannerService.MajorPlannerView> restore(
            @RequestBody MajorPlannerService.RestoreRequest req) {
        return Result.ok(majorPlannerService.restore(req));
    }

    @GetMapping("/results/{id}")
    public Result<MajorPlannerService.MajorPlannerView> detail(@PathVariable Long id,
                                                               HttpServletRequest request) {
        return Result.ok(majorPlannerService.detail(id, resolvePlanCode(request)));
    }

    @PostMapping("/results/{id}/ai-analysis")
    public Result<MajorPlannerService.AiAnalysisResult> aiAnalysis(
            @PathVariable Long id,
            @RequestBody(required = false) MajorPlannerService.AiAnalysisRequest req,
            HttpServletRequest request) {
        String code = req != null && req.getPlanCode() != null ? req.getPlanCode() : resolvePlanCode(request);
        boolean forceRefresh = req != null && Boolean.TRUE.equals(req.getForceRefresh());
        return Result.ok(majorPlannerService.aiAnalysis(id, code, forceRefresh));
    }

    private String resolvePlanCode(HttpServletRequest request) {
        String code = request.getHeader("X-Major-Plan-Code");
        if (code == null || code.isBlank()) {
            code = request.getParameter("planCode");
        }
        return code;
    }
}
