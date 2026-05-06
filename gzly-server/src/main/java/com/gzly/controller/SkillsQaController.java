package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AiService;
import com.gzly.service.SkillsRagService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/volunteer/plans/{planId}/skills")
@RequiredArgsConstructor
public class SkillsQaController {

    private final SkillsRagService skillsRagService;

    @PostMapping("/ask")
    public Result<SkillsRagService.SkillsAnswer> ask(@PathVariable Long planId,
                                                     @RequestParam(required = false) String safetyCode,
                                                     @RequestParam(required = false) String accessKey,
                                                     @RequestHeader(value = "X-Plan-Safety-Code", required = false) String headerSafetyCode,
                                                     @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey,
                                                     @RequestBody SkillsAskRequest req) {
        return Result.ok(skillsRagService.ask(
                planId,
                firstNonBlank(req == null ? null : req.getSafetyCode(), safetyCode, headerSafetyCode,
                        req == null ? null : req.getAccessKey(), accessKey, headerAccessKey),
                req == null ? null : req.getQuestion(),
                req == null ? null : req.getAiReport(),
                req == null ? null : req.getMessages()));
    }

    @GetMapping("/suggested-questions")
    public Result<List<String>> suggestedQuestions() {
        return Result.ok(skillsRagService.suggestedQuestions());
    }

    @Data
    public static class SkillsAskRequest {
        private String safetyCode;
        private String accessKey;
        private String question;
        private String aiReport;
        private List<AiService.AdvisorSkillChatMessage> messages;
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
