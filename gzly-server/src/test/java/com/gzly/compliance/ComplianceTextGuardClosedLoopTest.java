package com.gzly.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.mapper.AiAnalysisComplianceLogMapper;
import com.gzly.mapper.ComplianceSensitiveWordMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplianceTextGuardClosedLoopTest {

    @Test
    void shouldReplaceBannedAdmissionPromiseTermsAndAppendDisclaimer() {
        ComplianceSensitiveWordMapper wordMapper = mock(ComplianceSensitiveWordMapper.class);
        AiAnalysisComplianceLogMapper logMapper = mock(AiAnalysisComplianceLogMapper.class);
        when(wordMapper.selectList(any())).thenReturn(java.util.List.of());
        when(logMapper.insert(any())).thenReturn(1);
        ComplianceTextGuard guard = new ComplianceTextGuard(
                new AiOutputSanitizer(
                        new SensitiveWordMatcher(wordMapper),
                        new DisclaimerAppender()
                ),
                new ComplianceAuditLogger(logMapper, new ObjectMapper())
        );

        String text = guard.sanitizeText("test", "1", "这个方案稳上，录取概率很高，可以保证录取。");

        assertThat(text)
                .contains("风险相对较低")
                .contains("机会指数")
                .contains("不构成任何录取承诺");
        assertThat(text).doesNotContain("稳上", "录取概率", "保证录取");
    }
}
