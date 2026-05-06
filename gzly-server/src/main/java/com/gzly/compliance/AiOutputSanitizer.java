package com.gzly.compliance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiOutputSanitizer {

    private final SensitiveWordMatcher matcher;
    private final DisclaimerAppender disclaimerAppender;

    public ComplianceTextGuard.ComplianceReview sanitize(String businessType, String businessId, String rawText) {
        String sanitized = rawText == null ? "" : rawText.trim();
        List<SensitiveWordMatcher.SensitiveWordHit> hits = matcher.scan(sanitized);
        for (SensitiveWordMatcher.SensitiveWordHit hit : hits) {
            sanitized = sanitized.replace(hit.getWord(), hit.getReplacement());
        }
        sanitized = disclaimerAppender.append(sanitized);

        List<SensitiveWordMatcher.SensitiveWordHit> remaining = matcher.scan(sanitized);
        String action = hits.isEmpty() ? "pass" : "replace";
        if (remaining.stream().anyMatch(hit -> "high".equalsIgnoreCase(hit.getSeverity()))) {
            action = "block";
            sanitized = disclaimerAppender.append("内容触发了合规复核，系统已停止展示原始输出。请以贵州省招生考试院、高校招生章程、当年招生计划和正式投档录取结果为准。");
        }

        ComplianceTextGuard.ComplianceReview review = new ComplianceTextGuard.ComplianceReview();
        review.setBusinessType(businessType);
        review.setBusinessId(businessId);
        review.setRawText(rawText == null ? "" : rawText);
        review.setSanitizedText(sanitized);
        review.setHitWords(hits);
        review.setAction(action);
        review.setCompliant(!"block".equals(action));
        return review;
    }
}
