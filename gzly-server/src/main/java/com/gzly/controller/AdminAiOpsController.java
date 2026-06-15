package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.entity.AiCallLog;
import com.gzly.service.AiCallLogService;
import com.gzly.service.AiConfigService;
import com.gzly.service.AiQaChatClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台：AI 通道状态检测 + 最近调用日志 + 测试按钮。
 *
 * <p>路径 {@code /admin/ai-ops/**}，由全局 AuthInterceptor(admin) 保护。
 * 测试按钮真实调用一次 AI（极小提示词），结果写入 ai_call_log；不打印 API Key。</p>
 */
@RestController
@RequestMapping("/admin/ai-ops")
@RequiredArgsConstructor
public class AdminAiOpsController {

    private final AiCallLogService aiCallLogService;
    private final AiConfigService aiConfigService;
    private final AiQaChatClient aiQaChatClient;

    @GetMapping("/ai-status")
    public Result<AiStatusView> aiStatus() {
        AiStatusView view = new AiStatusView();
        AiConfigService.AiConfigView config = aiConfigService.getSafeView();
        view.setConfigured(Boolean.TRUE.equals(config.getEnabled()) && Boolean.TRUE.equals(config.getHasApiKey()));
        view.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        view.setHasApiKey(Boolean.TRUE.equals(config.getHasApiKey()));
        view.setBaseUrlConfigured(config.getBaseUrl() != null && !config.getBaseUrl().isBlank());
        view.setChatModel(config.getChatModel());
        view.setConfigSource(config.getConfigSource());

        AiCallLogService.StatusSummary summary = aiCallLogService.statusSummary();
        view.setStatus(summary);

        List<AiLogView> logs = new ArrayList<>();
        for (AiCallLog log : aiCallLogService.recent(20)) {
            AiLogView lv = new AiLogView();
            lv.setScene(log.getScene());
            lv.setSuccess(Integer.valueOf(1).equals(log.getSuccess()));
            lv.setHttpStatus(log.getHttpStatus());
            lv.setErrorCode(log.getErrorCode());
            lv.setModel(log.getModel());
            lv.setLatencyMs(log.getLatencyMs());
            lv.setMessage(log.getMessage());
            lv.setCreatedAt(log.getCreatedAt() == null ? "" : log.getCreatedAt().toString());
            logs.add(lv);
        }
        view.setRecentLogs(logs);
        return Result.ok(view);
    }

    @PostMapping("/test-volunteer")
    public Result<TestResult> testVolunteer() {
        return Result.ok(runTest(AiCallLogService.SCENE_TEST_VOLUNTEER,
                "你是高考志愿 AI 解读测试探针，只需简短回应以确认通道可用。"));
    }

    @PostMapping("/test-ai-qa")
    public Result<TestResult> testAiQa() {
        return Result.ok(runTest(AiCallLogService.SCENE_TEST_AI_QA,
                "你是未上线地区 AI 志愿问答测试探针，只需简短回应以确认通道可用。"));
    }

    private TestResult runTest(String scene, String systemPrompt) {
        List<AiQaChatClient.ChatTurn> turns = List.of(new AiQaChatClient.ChatTurn("user", "ping，请回复 ok"));
        AiQaChatClient.ChatResult chat = aiQaChatClient.complete(scene, systemPrompt, turns, 64, 0.0D);
        TestResult result = new TestResult();
        result.setUsable(chat.isUsable());
        result.setSuccess(chat.isSuccess());
        if (!chat.isUsable()) {
            result.setMessage("AI 通道未配置或已停用");
        } else if (chat.isSuccess()) {
            result.setMessage("调用成功，AI 通道可用");
        } else {
            result.setMessage("AI 通道已配置但本次调用失败，详见最近调用日志/状态");
        }
        result.setStatus(aiCallLogService.statusSummary());
        return result;
    }

    @Data
    public static class AiStatusView {
        private boolean configured;
        private boolean enabled;
        private boolean hasApiKey;
        private boolean baseUrlConfigured;
        private String chatModel;
        private String configSource;
        private AiCallLogService.StatusSummary status;
        private List<AiLogView> recentLogs;
    }

    @Data
    public static class AiLogView {
        private String scene;
        private boolean success;
        private Integer httpStatus;
        private String errorCode;
        private String model;
        private Integer latencyMs;
        private String message;
        private String createdAt;
    }

    @Data
    public static class TestResult {
        private boolean usable;
        private boolean success;
        private String message;
        private AiCallLogService.StatusSummary status;
    }
}
