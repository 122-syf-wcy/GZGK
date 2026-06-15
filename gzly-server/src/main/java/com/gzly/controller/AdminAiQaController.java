package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.Result;
import com.gzly.entity.AiQaEvidence;
import com.gzly.entity.AiQaMessage;
import com.gzly.entity.AiQaSession;
import com.gzly.mapper.AiQaEvidenceMapper;
import com.gzly.mapper.AiQaMessageMapper;
import com.gzly.mapper.AiQaSessionMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 后台：未上线地区 AI 问答会话只读列表。
 *
 * <p>路径 {@code /admin/ai-qa/**}，由全局 AuthInterceptor(admin) 保护。
 * <b>绝不返回对话码、哈希、指纹</b>，只展示运营所需聚合信息。</p>
 */
@RestController
@RequestMapping("/admin/ai-qa")
@RequiredArgsConstructor
public class AdminAiQaController {

    private final AiQaSessionMapper sessionMapper;
    private final AiQaMessageMapper messageMapper;
    private final AiQaEvidenceMapper evidenceMapper;

    @GetMapping("/sessions")
    public Result<Map<String, Object>> sessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String regionCode) {
        size = Math.min(Math.max(size, 1), 50);
        page = Math.max(page, 1);

        LambdaQueryWrapper<AiQaSession> qw = new LambdaQueryWrapper<AiQaSession>()
                .orderByDesc(AiQaSession::getLastActiveAt)
                .orderByDesc(AiQaSession::getId);
        if (regionCode != null && !regionCode.isBlank()) {
            qw.eq(AiQaSession::getRegionCode, regionCode.trim().toUpperCase(Locale.ROOT));
        }

        Page<AiQaSession> p = sessionMapper.selectPage(new Page<>(page, size), qw);
        List<AiQaSessionView> items = p.getRecords().stream().map(this::toView).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        result.put("totalSessions", sessionMapper.selectCount(null));
        return Result.ok(result);
    }

    private AiQaSessionView toView(AiQaSession s) {
        AiQaSessionView view = new AiQaSessionView();
        view.setSessionUid(s.getSessionUid());
        view.setRegionCode(s.getRegionCode());
        view.setRegionName(s.getRegionName());
        view.setCreatedAt(s.getCreatedAt());
        view.setLastActiveAt(s.getLastActiveAt());
        view.setMessageCount(s.getMessageCount() == null ? 0 : s.getMessageCount());
        view.setCompacted(s.getCompactionCount() != null && s.getCompactionCount() > 0);

        Long sid = s.getId();
        Long evidenceCount = evidenceMapper.selectCount(new LambdaQueryWrapper<AiQaEvidence>()
                .eq(AiQaEvidence::getSessionId, sid));
        view.setEvidenceCount(evidenceCount == null ? 0 : evidenceCount.intValue());

        AiQaMessage lastAssistant = messageMapper.selectOne(new LambdaQueryWrapper<AiQaMessage>()
                .eq(AiQaMessage::getSessionId, sid)
                .eq(AiQaMessage::getRole, "assistant")
                .orderByDesc(AiQaMessage::getId)
                .last("LIMIT 1"));
        view.setAiCallFailed(isAiFailure(lastAssistant));

        AiQaMessage lastUser = messageMapper.selectOne(new LambdaQueryWrapper<AiQaMessage>()
                .eq(AiQaMessage::getSessionId, sid)
                .eq(AiQaMessage::getRole, "user")
                .orderByDesc(AiQaMessage::getId)
                .last("LIMIT 1"));
        view.setLastQuestion(lastUser == null ? "" : brief(lastUser.getContent()));
        return view;
    }

    private boolean isAiFailure(AiQaMessage lastAssistant) {
        if (lastAssistant == null || lastAssistant.getContent() == null) {
            return false;
        }
        String c = lastAssistant.getContent();
        return c.contains("AI 暂时没有返回有效回复") || c.contains("未启用或未配置完整");
    }

    private String brief(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 60 ? trimmed.substring(0, 60) + "…" : trimmed;
    }

    @Data
    public static class AiQaSessionView {
        private String sessionUid;
        private String regionCode;
        private String regionName;
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;
        private int messageCount;
        private boolean compacted;
        private int evidenceCount;
        private boolean aiCallFailed;
        private String lastQuestion;
    }
}
