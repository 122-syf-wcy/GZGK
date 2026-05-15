package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.AiDeepAnalysisService;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/volunteer/plans/{planId}/ai-analysis")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiDeepAnalysisService aiDeepAnalysisService;
    private final SafetyCodeRequestResolver safetyCodeRequestResolver;
    private final SafetyCodeService safetyCodeService;

    @PostMapping
    public Result<AiDeepAnalysisService.AiAnalysisVO> generate(@PathVariable Long planId,
                                                              @RequestBody(required = false) AiAnalysisRequest req,
                                                              HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request, req);
        if (!safetyCodeService.verifyPlanAccess(planId, key)) {
            throw new BizException(403, "方案不存在或访问密钥无效");
        }
        return Result.ok(aiDeepAnalysisService.generate(
                planId,
                key,
                req != null && Boolean.TRUE.equals(req.getForceRefresh())));
    }

    @GetMapping
    public Result<AiDeepAnalysisService.AiAnalysisVO> get(@PathVariable Long planId,
                                                         HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request);
        if (!safetyCodeService.verifyPlanAccess(planId, key)) {
            throw new BizException(403, "方案不存在或访问密钥无效");
        }
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
}
