package com.gzly.service;

import com.gzly.common.ComplianceConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceGuardrailTest {

    @Test
    void sanitizeAiOutput_keepsCompliantContent() throws Exception {
        AiService service = new AiService(null, new VolunteerMetricsRecorder());

        String output = invokeSanitize(service, "> " + ComplianceConstants.AI_GENERATED_NOTICE + "\n\n本方案只能作为参考建议。");

        assertThat(output).contains(ComplianceConstants.AI_GENERATED_NOTICE);
        assertThat(output).contains("参考建议");
    }

    @Test
    void sanitizeAiOutput_addsNoticeAndBlocksBannedTerms() throws Exception {
        AiService service = new AiService(null, new VolunteerMetricsRecorder());

        String missingNotice = invokeSanitize(service, "这个方案整体不错。");
        String bannedTerm = invokeSanitize(service, "> " + ComplianceConstants.AI_GENERATED_NOTICE + "\n\n可以保录取。");

        assertThat(missingNotice)
                .contains(ComplianceConstants.AI_GENERATED_NOTICE)
                .contains(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
        assertThat(bannedTerm).contains("触发了安全复核");
    }

    @Test
    void sanitizeAiOutput_emptyTextShowsUnavailableInsteadOfSafetyReview() throws Exception {
        AiService service = new AiService(null, new VolunteerMetricsRecorder());

        String output = invokeSanitize(service, "   ");

        assertThat(output)
                .contains("AI 服务本次没有返回有效回复")
                .doesNotContain("触发了安全复核");
    }

    @Test
    void buildAnalysisUserPrompt_keepsLiteralPercentText() throws Exception {
        AiService service = new AiService(null, new VolunteerMetricsRecorder());

        Method method = AiService.class.getDeclaredMethod("buildAnalysisUserPrompt", String.class);
        method.setAccessible(true);
        String prompt = (String) method.invoke(service, "{\"items\":[]}");

        assertThat(prompt).contains("机会指数");
        assertThat(prompt).contains("{\"items\":[]}");
    }

    private String invokeSanitize(AiService service, String raw) throws Exception {
        Method method = AiService.class.getDeclaredMethod("sanitizeAiOutput", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, raw);
    }
}
