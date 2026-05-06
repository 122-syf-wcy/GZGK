package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.BizUserFeedback;
import com.gzly.mapper.BizUserFeedbackMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final BizUserFeedbackMapper feedbackMapper;

    @PostMapping
    public Result<Void> submit(@RequestBody FeedbackRequest req, HttpServletRequest request) {
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.length() < 10 || content.length() > 500) {
            throw new BizException("反馈内容需控制在 10-500 字");
        }

        String sourcePage = req.getSourcePage() == null ? "" : req.getSourcePage().trim();
        if (sourcePage.length() > 120) {
            sourcePage = sourcePage.substring(0, 120);
        }

        BizUserFeedback feedback = new BizUserFeedback();
        feedback.setContent(content);
        feedback.setSourcePage(sourcePage);
        feedback.setStatus(0);
        feedback.setIpHash(hashIp(getClientIp(request)));
        feedbackMapper.insert(feedback);
        return Result.ok();
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
    public static class FeedbackRequest {
        private String content;
        private String sourcePage;
    }
}
