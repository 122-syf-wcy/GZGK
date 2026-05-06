package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.EncouragementMessage;
import com.gzly.mapper.EncouragementMessageMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/encouragement-messages")
@RequiredArgsConstructor
public class EncouragementMessageController {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://|www\\.|\\.com|\\.cn|加微信|微信号|QQ|群号)");
    private static final List<String> BLOCKED_WORDS = List.of("包录取", "代填", "广告", "博彩", "贷款", "兼职刷单");

    private final EncouragementMessageMapper messageMapper;

    @GetMapping
    public Result<List<MessageView>> list(@RequestParam(defaultValue = "20") int size) {
        int limit = Math.min(Math.max(size, 1), 30);
        List<MessageView> rows = messageMapper.selectList(new LambdaQueryWrapper<EncouragementMessage>()
                        .eq(EncouragementMessage::getStatus, 1)
                        .orderByDesc(EncouragementMessage::getCreatedAt)
                        .last("LIMIT " + limit))
                .stream()
                .map(MessageView::from)
                .toList();
        return Result.ok(rows);
    }

    @PostMapping
    public Result<MessageView> submit(@RequestBody SubmitRequest req, HttpServletRequest request) {
        String content = normalize(req.getContent());
        if (content.length() < 4 || content.length() > 120) {
            throw new BizException("留言需控制在 4-120 字");
        }
        if (URL_PATTERN.matcher(content).find() || containsBlockedWord(content)) {
            throw new BizException("留言墙只接收给考生加油鼓励的内容，请勿发布广告或联系方式");
        }
        String nickname = normalize(req.getNickname());
        if (nickname.isBlank()) {
            nickname = "贵州考生";
        }
        if (nickname.length() > 12) {
            nickname = nickname.substring(0, 12);
        }

        EncouragementMessage message = new EncouragementMessage();
        message.setNickname(nickname);
        message.setContent(content);
        message.setStatus(1);
        message.setIpHash(hashIp(getClientIp(request)));
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return Result.ok(MessageView.from(message));
    }

    private boolean containsBlockedWord(String content) {
        return BLOCKED_WORDS.stream().anyMatch(content::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip;
    }

    private String hashIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(ip.hashCode());
        }
    }

    @Data
    public static class SubmitRequest {
        private String nickname;
        private String content;
    }

    @Data
    public static class MessageView {
        private Long id;
        private String nickname;
        private String content;
        private String createdAt;

        static MessageView from(EncouragementMessage message) {
            MessageView view = new MessageView();
            view.setId(message.getId());
            view.setNickname(message.getNickname());
            view.setContent(message.getContent());
            view.setCreatedAt(message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
            return view;
        }
    }
}
