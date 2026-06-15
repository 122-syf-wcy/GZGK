package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AiQaRegionRegistry;
import com.gzly.service.AiQaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 未上线地区 AI 志愿问答接口。
 *
 * <p>对外路径前缀为 {@code /api/ai-qa}（context-path=/api）。全部公开 + 限流；
 * 访问会话内容必须携带对话码（DB 只存哈希）。</p>
 */
@RestController
@RequestMapping("/ai-qa")
@RequiredArgsConstructor
public class AiQaController {

    private final AiQaService aiQaService;

    /** 未上线地区列表（前端地区选择器使用，已排除 8 省）。 */
    @GetMapping("/regions")
    public Result<List<RegionView>> regions() {
        List<RegionView> regions = new ArrayList<>();
        for (Map.Entry<String, String> entry : AiQaRegionRegistry.UNLAUNCHED_REGIONS.entrySet()) {
            regions.add(new RegionView(entry.getKey(), entry.getValue()));
        }
        return Result.ok(regions);
    }

    /** 创建会话，返回完整对话码（仅此一次）。 */
    @PostMapping("/sessions")
    public Result<AiQaService.CreateSessionResult> createSession(@RequestBody AiQaService.CreateSessionCommand cmd) {
        return Result.ok(aiQaService.createSession(cmd));
    }

    /** 凭对话码找回会话与历史消息。 */
    @PostMapping("/sessions/restore")
    public Result<AiQaService.RestoreResult> restore(@RequestBody RestoreRequest req, HttpServletRequest request) {
        return Result.ok(aiQaService.restore(req == null ? null : req.getConversationCode(), getClientIp(request)));
    }

    /** 发送一条问题，返回 AI 回复与来源卡片。 */
    @PostMapping("/sessions/{sessionUid}/messages")
    public Result<AiQaService.SendMessageResult> sendMessage(@PathVariable String sessionUid,
                                                             @RequestBody SendMessageRequest req) {
        return Result.ok(aiQaService.sendMessage(
                sessionUid,
                req == null ? null : req.getConversationCode(),
                req == null ? null : req.getContent()));
    }

    /** 拉取会话历史消息（需对话码校验）。 */
    @GetMapping("/sessions/{sessionUid}/messages")
    public Result<AiQaService.RestoreResult> listMessages(@PathVariable String sessionUid,
                                                          @RequestParam("code") String code) {
        return Result.ok(aiQaService.listMessages(sessionUid, code));
    }

    /**
     * 仅当请求来自可信反向代理（本机/内网）时才采信 X-Forwarded-For / X-Real-IP，
     * 否则一律使用 remoteAddr，避免攻击者伪造头部轮换 IP 绕过找回限流。
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String ip = null;
        if (isTrustedProxy(remoteAddr)) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = remoteAddr;
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
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

    @Data
    public static class RestoreRequest {
        private String conversationCode;
    }

    @Data
    public static class SendMessageRequest {
        private String conversationCode;
        private String content;
    }

    public record RegionView(String code, String name) {
    }
}
