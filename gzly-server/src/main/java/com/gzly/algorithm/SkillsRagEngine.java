package com.gzly.algorithm;

import com.gzly.service.SkillsRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SkillsRagEngine {
    private final SkillsRagService service;

    public SkillsRagService.SkillsAnswer ask(Long planId, String accessKey, String question) {
        return service.ask(planId, accessKey, question, null, null);
    }
}
