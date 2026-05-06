package com.gzly.compliance;

import com.gzly.common.ComplianceConstants;
import com.gzly.entity.ComplianceSensitiveWord;
import com.gzly.mapper.ComplianceSensitiveWordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AiOutputSanitizer 三态行为：pass / replace / block。
 *
 * <ul>
 *   <li>无敏感词 → action=pass，sanitized 末尾追加免责声明。</li>
 *   <li>含可替换敏感词 → action=replace，词被替换为合规表述。</li>
 *   <li>替换后仍残留 high 严重度词 → action=block，整段回滚到合规兜底文案。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AiOutputSanitizerTest {

    @Mock ComplianceSensitiveWordMapper wordMapper;

    DisclaimerAppender appender = new DisclaimerAppender();

    @InjectMocks SensitiveWordMatcher matcher;

    @Test
    void sanitize_passesThroughCompliantText() {
        when(wordMapper.selectList(any())).thenReturn(List.of());
        AiOutputSanitizer sanitizer = new AiOutputSanitizer(matcher, appender);

        ComplianceTextGuard.ComplianceReview review = sanitizer.sanitize(
                "ai_output", "test-1",
                "本方案根据机会指数和数据参考度筛选，请结合招生章程谨慎参考。");

        assertThat(review.getAction()).isEqualTo("pass");
        assertThat(review.isCompliant()).isTrue();
        assertThat(review.getHitWords()).isEmpty();
        // 一定追加了免责声明
        assertThat(review.getSanitizedText()).contains(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
    }

    @Test
    void sanitize_replacesBannedTerms_andSetsActionReplace() {
        when(wordMapper.selectList(any())).thenReturn(List.of());
        AiOutputSanitizer sanitizer = new AiOutputSanitizer(matcher, appender);

        ComplianceTextGuard.ComplianceReview review = sanitizer.sanitize(
                "ai_output", "test-2",
                "这个方案保证录取且100%录取，录取概率非常高，请放心。");

        assertThat(review.getAction()).isEqualTo("replace");
        assertThat(review.isCompliant()).isTrue();
        // hits 应当含至少 2 个高危词
        assertThat(review.getHitWords()).extracting(SensitiveWordMatcher.SensitiveWordHit::getWord)
                .contains("保证录取", "100%录取", "录取概率");
        // sanitized 一定不再含原始敏感词
        assertThat(review.getSanitizedText())
                .doesNotContain("保证录取")
                .doesNotContain("100%录取")
                .doesNotContain("录取概率")
                .contains(ComplianceConstants.SAFE_ASSISTANT_NOTICE);
    }

    @Test
    void sanitize_blocksWhenHighRiskRemainsAfterReplacement() {
        // DB 词库定义一个"狡猾"的词：替换后仍含 high 严重度的词
        ComplianceSensitiveWord trap = new ComplianceSensitiveWord();
        trap.setWord("锚词A");
        trap.setSeverity("medium");
        trap.setReplacement("保证录取"); // 替换后仍是 high 词（fallback 中存在）
        when(wordMapper.selectList(any())).thenReturn(List.of(trap));
        AiOutputSanitizer sanitizer = new AiOutputSanitizer(matcher, appender);

        ComplianceTextGuard.ComplianceReview review = sanitizer.sanitize(
                "ai_output", "test-3",
                "这是含有锚词A的文本。");

        // 二次扫描发现 high → 整段触发 block
        assertThat(review.getAction()).isEqualTo("block");
        assertThat(review.isCompliant()).isFalse();
        assertThat(review.getSanitizedText())
                .contains("内容触发了合规复核")
                .contains(ComplianceConstants.SAFE_ASSISTANT_NOTICE)
                .doesNotContain("保证录取");
    }

    @Test
    void sanitize_carriesBusinessMetadata() {
        when(wordMapper.selectList(any())).thenReturn(List.of());
        AiOutputSanitizer sanitizer = new AiOutputSanitizer(matcher, appender);

        ComplianceTextGuard.ComplianceReview review = sanitizer.sanitize(
                "skills_qa", "plan-180", "正常文本。");

        assertThat(review.getBusinessType()).isEqualTo("skills_qa");
        assertThat(review.getBusinessId()).isEqualTo("plan-180");
        assertThat(review.getRawText()).isEqualTo("正常文本。");
    }
}
