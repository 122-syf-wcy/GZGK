package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AiDeepAnalysisService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/volunteer/plans/{planId}/ai-analysis")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiDeepAnalysisService aiDeepAnalysisService;

    @PostMapping
    public Result<AiDeepAnalysisService.AiAnalysisVO> generate(@PathVariable Long planId,
                                                              @RequestParam(required = false) String safetyCode,
                                                              @RequestParam(required = false) String accessKey,
                                                              @RequestHeader(value = "X-Plan-Safety-Code", required = false) String headerSafetyCode,
                                                              @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey,
                                                              @RequestBody(required = false) AiAnalysisRequest req) {
        return Result.ok(aiDeepAnalysisService.generate(
                planId,
                firstNonBlank(req == null ? null : req.getSafetyCode(), safetyCode, headerSafetyCode,
                        req == null ? null : req.getAccessKey(), accessKey, headerAccessKey),
                req != null && Boolean.TRUE.equals(req.getForceRefresh())));
    }

    @GetMapping
    public Result<AiDeepAnalysisService.AiAnalysisVO> get(@PathVariable Long planId,
                                                         @RequestParam(required = false) String safetyCode,
                                                         @RequestParam(required = false) String accessKey,
                                                         @RequestHeader(value = "X-Plan-Safety-Code", required = false) String headerSafetyCode,
                                                         @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey) {
        String key = firstNonBlank(safetyCode, headerSafetyCode, accessKey, headerAccessKey);
        AiDeepAnalysisService.AiAnalysisVO vo = aiDeepAnalysisService.get(planId, key);
        if (vo == null) {
            vo = aiDeepAnalysisService.generate(planId, key, false);
        }
        return Result.ok(vo);
    }

    @Data
    public static class AiAnalysisRequest {
        private Boolean forceRefresh;
        private String safetyCode;
        private String accessKey;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
