package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.AiService;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import com.gzly.service.SkillsRagService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/volunteer/plans/{planId}/skills")
@RequiredArgsConstructor
public class SkillsQaController {

    private final SkillsRagService skillsRagService;
    private final SafetyCodeRequestResolver safetyCodeRequestResolver;
    private final SafetyCodeService safetyCodeService;

    @PostMapping("/ask")
    public Result<SkillsRagService.SkillsAnswer> ask(@PathVariable Long planId,
                                                     @RequestBody(required = false) SkillsAskRequest req,
                                                     HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request, req);
        if (!safetyCodeService.verifyPlanAccess(planId, key)) {
            throw new BizException(403, "方案不存在或访问密钥无效");
        }
        return Result.ok(skillsRagService.ask(
                planId,
                key,
                req == null ? null : req.getQuestion(),
                req == null ? null : req.getAiReport(),
                req == null ? null : req.getMessages()));
    }

    @GetMapping("/suggested-questions")
    public Result<List<String>> suggestedQuestions(@PathVariable Long planId,
                                                   HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request);
        if (!safetyCodeService.verifyPlanAccess(planId, key)) {
            throw new BizException(403, "方案不存在或访问密钥无效");
        }
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
}
