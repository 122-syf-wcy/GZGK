package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.entity.AiCallLog;
import com.gzly.mapper.AiCallLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * AI 调用日志服务：记录每次 AI 调用结果，供后台「AI 状态检测 + 最近调用日志」使用。
 *
 * <p>安全：message 仅存简要信息/中转错误体片段，<b>绝不存 API Key 或对话码</b>。</p>
 * <p>所有方法 best-effort，失败不影响主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogService {

    public static final String SCENE_AI_QA = "ai_qa";
    public static final String SCENE_VOLUNTEER_ANALYSIS = "volunteer_analysis";
    public static final String SCENE_ADVISOR_CHAT = "advisor_chat";
    public static final String SCENE_MAJOR_PLANNER = "major_planner";
    public static final String SCENE_TEST_CONNECTION = "test_connection";
    public static final String SCENE_TEST_VOLUNTEER = "test_volunteer";
    public static final String SCENE_TEST_AI_QA = "test_ai_qa";

    private static final int MAX_MESSAGE = 480;

    private final AiCallLogMapper aiCallLogMapper;

    public void record(String scene, boolean success, Integer httpStatus, String errorCode,
                       String model, Integer latencyMs, String message) {
        try {
            AiCallLog entity = new AiCallLog();
            entity.setScene(scene == null ? "" : scene);
            entity.setSuccess(success ? 1 : 0);
            entity.setHttpStatus(httpStatus);
            entity.setErrorCode(truncate(errorCode, 80));
            entity.setModel(truncate(model, 100));
            entity.setLatencyMs(latencyMs);
            entity.setMessage(truncate(message, MAX_MESSAGE));
            entity.setCreatedAt(LocalDateTime.now());
            aiCallLogMapper.insert(entity);
        } catch (Exception e) {
            log.debug("记录 AI 调用日志失败: {}", e.getMessage());
        }
    }

    public List<AiCallLog> recent(int limit) {
        int safe = Math.min(Math.max(limit, 1), 100);
        try {
            return aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                    .orderByDesc(AiCallLog::getCreatedAt)
                    .orderByDesc(AiCallLog::getId)
                    .last("LIMIT " + safe));
        } catch (Exception e) {
            log.debug("查询 AI 调用日志失败: {}", e.getMessage());
            return List.of();
        }
    }

    public long failureCountSince(LocalDateTime since) {
        try {
            Long count = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                    .eq(AiCallLog::getSuccess, 0)
                    .ge(AiCallLog::getCreatedAt, since));
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.debug("查询 AI 失败计数失败: {}", e.getMessage());
            return 0L;
        }
    }

    /** 基于最近一条日志推断 AI 通道整体状态。 */
    public StatusSummary statusSummary() {
        StatusSummary summary = new StatusSummary();
        List<AiCallLog> recent = recent(20);
        summary.setRecentCount(recent.size());
        if (recent.isEmpty()) {
            summary.setOverall("unknown");
            summary.setDetail("暂无 AI 调用记录");
            return summary;
        }
        AiCallLog latest = recent.get(0);
        summary.setLastScene(latest.getScene());
        summary.setLastSuccess(Integer.valueOf(1).equals(latest.getSuccess()));
        summary.setLastHttpStatus(latest.getHttpStatus());
        summary.setLastErrorCode(latest.getErrorCode());
        summary.setLastAt(latest.getCreatedAt());
        long failCount = recent.stream().filter(r -> !Integer.valueOf(1).equals(r.getSuccess())).count();
        summary.setRecentFailCount((int) failCount);

        if (summary.isLastSuccess()) {
            summary.setOverall("ok");
            summary.setDetail("最近一次 AI 调用成功");
            return summary;
        }

        String blob = ((latest.getErrorCode() == null ? "" : latest.getErrorCode()) + " "
                + (latest.getMessage() == null ? "" : latest.getMessage())).toLowerCase(Locale.ROOT);
        Integer status = latest.getHttpStatus();
        if (blob.contains("insufficient_balance") || blob.contains("balance") || blob.contains("余额")
                || blob.contains("quota") || blob.contains("欠费")) {
            summary.setOverall("insufficient_balance");
            summary.setDetail("AI 中转账户余额不足/配额用尽");
        } else if (blob.contains("group_not_allowed") || blob.contains("group not allowed")
                || blob.contains("分组") || blob.contains("无权使用")) {
            summary.setOverall("group_not_allowed");
            summary.setDetail("当前账号/分组无权使用该模型，请换模型或开通权限");
        } else if (blob.contains("handshake") || blob.contains("ssl") || blob.contains("remote host terminated")
                || blob.contains("tls") || blob.contains("certificate") || blob.contains("pkix")) {
            summary.setOverall("handshake_error");
            summary.setDetail("AI 中转 TLS/网络握手失败，请检查 Base URL 或更换中转线路");
        } else if (blob.contains("timeout") || blob.contains("timed out") || blob.contains("超时")) {
            summary.setOverall("timeout");
            summary.setDetail("AI 服务响应超时，可稍后重试；若连续出现请检查服务商线路或模型协议");
        } else if ((status != null && (status == 401 || status == 403))
                || blob.contains("invalid_api_key") || blob.contains("unauthorized")
                || blob.contains("api key") || blob.contains("permission")) {
            summary.setOverall("key_error");
            summary.setDetail("API Key 无效或无权限");
        } else if ((status != null && status == 404)
                || blob.contains("model") && (blob.contains("not found") || blob.contains("does not exist")
                || blob.contains("不存在") || blob.contains("unavailable"))) {
            summary.setOverall("model_unavailable");
            summary.setDetail("模型不可用或不存在");
        } else if (blob.contains("no_valid_response") || blob.contains("empty_content") || blob.contains("空内容")) {
            summary.setOverall("no_valid_response");
            summary.setDetail("AI 未返回有效内容，请检查模型名或协议兼容性");
        } else {
            summary.setOverall("error");
            summary.setDetail("AI 调用失败：" + (latest.getMessage() == null ? "未知错误" : latest.getMessage()));
        }
        return summary;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    @Data
    public static class StatusSummary {
        private String overall = "unknown";
        private String detail = "";
        private String lastScene;
        private boolean lastSuccess;
        private Integer lastHttpStatus;
        private String lastErrorCode;
        private LocalDateTime lastAt;
        private int recentCount;
        private int recentFailCount;
    }
}
