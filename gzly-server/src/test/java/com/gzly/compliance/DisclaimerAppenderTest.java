package com.gzly.compliance;

import com.gzly.common.ComplianceConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisclaimerAppenderTest {

    private final DisclaimerAppender appender = new DisclaimerAppender();

    @Test
    void append_returnsNoticeOnly_whenInputBlank() {
        assertThat(appender.append(null)).isEqualTo(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
        assertThat(appender.append("")).isEqualTo(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
        assertThat(appender.append("   ")).isEqualTo(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
    }

    @Test
    void append_addsNotice_whenAbsent() {
        String result = appender.append("这是一段普通的辅助参考文本。");

        assertThat(result).startsWith("这是一段普通的辅助参考文本。");
        assertThat(result).endsWith(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
        assertThat(result).contains("\n\n");
    }

    @Test
    void append_isIdempotent_whenNoticeAlreadyPresent() {
        String already = "之前的内容\n\n" + ComplianceConstants.SAFE_ASSISTANT_NOTICE;

        String result = appender.append(already);

        // 不再追加第二份免责声明
        long count = countOccurrences(result, ComplianceConstants.SAFE_ASSISTANT_NOTICE);
        assertThat(count).isEqualTo(1);
    }

    private long countOccurrences(String text, String token) {
        long count = 0;
        int i = 0;
        while ((i = text.indexOf(token, i)) >= 0) {
            count++;
            i += token.length();
        }
        return count;
    }
}
