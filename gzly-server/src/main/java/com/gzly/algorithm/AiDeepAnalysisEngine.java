package com.gzly.algorithm;

import com.gzly.service.AiDeepAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiDeepAnalysisEngine {
    private final AiDeepAnalysisService service;

    public AiDeepAnalysisService.AiAnalysisVO generate(Long planId, String accessKey, boolean forceRefresh) {
        return service.generate(planId, accessKey, forceRefresh);
    }
}
