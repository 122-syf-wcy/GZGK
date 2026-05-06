package com.gzly.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.entity.AiAnalysisComplianceLog;
import com.gzly.mapper.AiAnalysisComplianceLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceAuditLogger {

    private final AiAnalysisComplianceLogMapper logMapper;
    private final ObjectMapper objectMapper;

    public void log(ComplianceTextGuard.ComplianceReview review) {
        if (review == null) {
            return;
        }
        try {
            AiAnalysisComplianceLog row = new AiAnalysisComplianceLog();
            row.setBusinessType(review.getBusinessType());
            row.setBusinessId(review.getBusinessId());
            row.setRawText(review.getRawText());
            row.setSanitizedText(review.getSanitizedText());
            row.setHitWordsJson(objectMapper.writeValueAsString(review.getHitWords()));
            row.setAction(review.getAction());
            row.setCreatedAt(LocalDateTime.now());
            logMapper.insert(row);
        } catch (Exception e) {
            log.warn("合规审查日志写入失败: businessType={}, businessId={}",
                    review.getBusinessType(), review.getBusinessId(), e);
        }
    }
}
