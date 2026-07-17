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
            @RequestBody MajorPlannerService.RestoreRequest req,
            HttpServletRequest request) {
        return Result.ok(majorPlannerService.restore(req, clientIp(request)));
    }

    @GetMapping("/results/{id}")
    public Result<MajorPlannerService.MajorPlannerView> detail(@PathVariable Long id,
                                                               HttpServletRequest request) {
        return Result.ok(majorPlannerService.detail(id, resolvePlanCode(request), clientIp(request)));
    }

    @PostMapping("/results/{id}/ai-analysis")
    public Result<MajorPlannerService.AiAnalysisResult> aiAnalysis(
            @PathVariable Long id,
            @RequestBody(required = false) MajorPlannerService.AiAnalysisRequest req,
            HttpServletRequest request) {
        String code = req != null && req.getPlanCode() != null ? req.getPlanCode() : resolvePlanCode(request);
        boolean forceRefresh = req != null && Boolean.TRUE.equals(req.getForceRefresh());
        return Result.ok(majorPlannerService.aiAnalysis(id, code, forceRefresh, clientIp(request)));
    }

    private String resolvePlanCode(HttpServletRequest request) {
        String code = request.getHeader("X-Major-Plan-Code");
        if (code == null || code.isBlank()) {
            code = request.getParameter("planCode");
        }
        return code;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        String ip = null;
        if (isTrustedProxy(remoteAddr)) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = remoteAddr;
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("192.168.")
                || remoteAddr.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }
}
