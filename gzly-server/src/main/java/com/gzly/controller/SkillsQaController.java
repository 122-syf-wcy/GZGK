package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AdvisorAgentService;
import com.gzly.service.AiService;
import com.gzly.service.SkillsRagService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/volunteer/plans/{planId}/skills")
@RequiredArgsConstructor
public class SkillsQaController {

    private final SkillsRagService skillsRagService;
    private final AdvisorAgentService advisorAgentService;
    /** 与 TaskExecutorConfig 的 bean 名一致，按名注入业务线程池。 */
    private final Executor taskExecutor;

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

    /**
     * Agent 流式问答：POST 直接返回 SSE，前端用 fetch ReadableStream 消费。
     * 过程事件（工具调用/思考/正文增量）见 AdvisorAgentService 的事件协议。
     */
    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@PathVariable Long planId, @RequestBody SkillsAskRequest req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String credential = firstNonBlank(
                req == null ? null : req.getSafetyCode(),
                req == null ? null : req.getAccessKey());
        String question = req == null ? null : req.getQuestion();
        List<AiService.AdvisorSkillChatMessage> messages = req == null ? null : req.getMessages();
        taskExecutor.execute(() ->
                advisorAgentService.streamAgentChat(emitter, planId, credential, question, messages));
        return emitter;
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
