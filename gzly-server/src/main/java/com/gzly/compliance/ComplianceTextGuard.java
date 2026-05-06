package com.gzly.compliance;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ComplianceTextGuard {

    private final AiOutputSanitizer sanitizer;
    private final ComplianceAuditLogger auditLogger;

    public ComplianceReview review(String businessType, String businessId, String rawText) {
        ComplianceReview review = sanitizer.sanitize(businessType, businessId, rawText);
        auditLogger.log(review);
        return review;
    }

    public String sanitizeText(String businessType, String businessId, String rawText) {
        return review(businessType, businessId, rawText).getSanitizedText();
    }

    @Data
    public static class ComplianceReview {
        private String businessType;
        private String businessId;
        private String rawText;
        private String sanitizedText;
        private List<SensitiveWordMatcher.SensitiveWordHit> hitWords;
        private String action;
        private boolean compliant;
    }
}
